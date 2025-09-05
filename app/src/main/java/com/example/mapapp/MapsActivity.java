
package com.example.mapapp;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

public class MapsActivity extends AppCompatActivity {

    private MapView map;
    private IMapController controller;
    private FusedLocationProviderClient fusedLocationClient;

    private GeoPoint currentLocation;
    private Marker myMarker;
    private Polygon radiusCircle;
    private FolderOverlay trafficOverlay;

    private boolean firstFix = true;

    private DatabaseReference trafficRef;
    private ValueEventListener trafficListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // OSMDroid config (important for tiles)
        Configuration.getInstance().load(
                getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_maps);

        map = findViewById(R.id.map);
        map.setMultiTouchControls(true);
        controller = map.getController();
        controller.setZoom(15.0);

        // Overlay group for traffic lights only
        trafficOverlay = new FolderOverlay();
        map.getOverlays().add(trafficOverlay);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        trafficRef = FirebaseDatabase.getInstance().getReference("trafficLights");

        startLocationUpdates();
        subscribeTraffic();
    }

    // Live location updates
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            return;
        }

        LocationRequest request = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(2000)        // 3 sec
                .setFastestInterval(1000);

        fusedLocationClient.requestLocationUpdates(request, new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult lr) {
                Location loc = lr.getLastLocation();
                if (loc == null) return;

                GeoPoint newLocation = new GeoPoint(loc.getLatitude(), loc.getLongitude());

                animateMarker(myMarker, newLocation);
                currentLocation = new GeoPoint(loc.getLatitude(), loc.getLongitude());

                updateMyLocationMarker(loc);
                updateRadiusCircle(currentLocation, 3000);   // meters
                // Re-filter traffic markers for new location
                renderTrafficWithinRadius();

                if (firstFix) {
                    controller.setCenter(currentLocation);
                    firstFix = false;
                }
                map.invalidate();
            }
        }, getMainLooper());
    }
    private void animateMarker(Marker marker, GeoPoint to) {
        if (marker == null) return;
        GeoPoint start = marker.getPosition();

        ValueAnimator animator = ValueAnimator.ofFloat(0,1);
        animator.setDuration(1000); // 1 sec
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            double lat = start.getLatitude() + fraction*(to.getLatitude()-start.getLatitude());
            double lon = start.getLongitude() + fraction*(to.getLongitude()-start.getLongitude());
            marker.setPosition(new GeoPoint(lat, lon));
            map.invalidate();
        });
        animator.start();
    }

    // Create/update moving marker
    private void updateMyLocationMarker(Location loc) {
        if (myMarker == null) {
            myMarker = new Marker(map);
            myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            myMarker.setTitle("You are here");
            myMarker.setIcon(getResources().getDrawable(R.drawable.baseline_man_24));
            map.getOverlays().add(myMarker);
        }
        myMarker.setPosition(currentLocation);

        // optional: rotate by bearing if available
        if (loc.hasBearing()) {
            myMarker.setRotation(loc.getBearing());
        }
    }

    // Create/update 3km circle (NOTE: meters direct — no division!)
    private void updateRadiusCircle(GeoPoint center, double radiusMeters) {
        if (radiusCircle == null) {
            radiusCircle = new Polygon(map);
            radiusCircle.setFillColor(0x2000FF00);   // transparent green
            radiusCircle.setStrokeColor(0xFF00AA00);
            radiusCircle.setStrokeWidth(2f);
            map.getOverlays().add(radiusCircle);
        }
        radiusCircle.setPoints(Polygon.pointsAsCircle(center, radiusMeters)); // <-- meters, not degrees
    }

    // ---- Firebase traffic lights ----
    private void subscribeTraffic() {
        trafficListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                // just re-render based on latest list + currentLocation
                renderTrafficWithinRadius(snapshot);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MapsActivity.this, "Firebase: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        trafficRef.addValueEventListener(trafficListener);
    }

    // If we have latest snapshot, render from it; else clear/keep waiting
    private void renderTrafficWithinRadius() {
        trafficRef.get().addOnSuccessListener(this::renderTrafficWithinRadius);
    }

    private void renderTrafficWithinRadius(DataSnapshot snapshot) {
        if (currentLocation == null) return;

        // Clear only traffic markers, keep myMarker & circle
        trafficOverlay.getItems().clear();

        for (DataSnapshot lightSnap : snapshot.getChildren()) {
            Double lat = lightSnap.child("lat").getValue(Double.class);
            Double lng = lightSnap.child("lng").getValue(Double.class);
            String status = lightSnap.child("status").getValue(String.class);

            if (lat == null || lng == null) continue;

            GeoPoint p = new GeoPoint(lat, lng);
            double dist = distanceMeters(currentLocation, p);
            if (dist <= 3000) {
                Marker m = new Marker(map);
                m.setPosition(p);
                m.setTitle("Traffic: " + status + " (" + (int) dist + "m)");

                if ("RED".equalsIgnoreCase(status)) {
                    m.setIcon(getResources().getDrawable(R.drawable.red));
                } else if ("GREEN".equalsIgnoreCase(status)) {
                    m.setIcon(getResources().getDrawable(R.drawable.green));
                } else {
                    m.setIcon(getResources().getDrawable(R.drawable.yellow));
                }
                trafficOverlay.add(m);
            }
        }
        map.invalidate();
    }

    private double distanceMeters(GeoPoint a, GeoPoint b) {
        float[] res = new float[1];
        Location.distanceBetween(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude(), res);
        return res[0];
    }

    // Permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, perms, grantResults);
        if (requestCode == 1001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trafficListener != null) trafficRef.removeEventListener(trafficListener);
    }
}