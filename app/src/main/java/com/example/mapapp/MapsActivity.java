//package com.example.mapapp;
//
//import android.Manifest;
//import android.content.pm.PackageManager;
//import android.graphics.Color;
//import android.os.Bundle;
//import android.util.Log;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.preference.PreferenceManager;
//
//import com.google.android.gms.location.FusedLocationProviderClient;
//import com.google.android.gms.location.LocationServices;
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
//
//import org.osmdroid.api.IMapController;
//import org.osmdroid.config.Configuration;
//import org.osmdroid.util.GeoPoint;
//import org.osmdroid.views.MapView;
//import org.osmdroid.views.overlay.Marker;
//import org.osmdroid.views.overlay.Polygon;
//
//public class MapsActivity extends AppCompatActivity {
//
//    private MapView map;
//    private FusedLocationProviderClient fusedLocationClient;
//    private GeoPoint currentLocation;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        // OSM config
//        Configuration.getInstance().load(
//                getApplicationContext(),
//                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
//        );
//
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_maps);
//
//        map = findViewById(R.id.map);
//        map.setMultiTouchControls(true);
//        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK);
//
//        IMapController mapController = map.getController();
//        mapController.setZoom(15.0);
//
//        // Location client
//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
//
//        // ✅ Step 1: Get current location
//        getMyLocation();
//
//        // ✅ Step 2: Load traffic lights from Firebase
//        loadTrafficLights();
//    }
//
//    private void getMyLocation() {
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
//            return;
//        }
//
//        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
//            if (location != null) {
//                currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
//
//                // Map ko center karo
//                map.getController().setCenter(currentLocation);
//
//                // Marker lagao
//                Marker myMarker = new Marker(map);
//                myMarker.setPosition(currentLocation);
//                myMarker.setTitle("You are here");
//                myMarker.setIcon(getResources().getDrawable(R.drawable.baseline_location_pin_24));
//                map.getOverlays().add(myMarker);
//
//                // Radius circle
//                drawRadiusCircle(currentLocation, 3000);
//                Log.d("DEBUGloc", "Current Location: " + currentLocation);
//
//                map.invalidate();
//            }
//        });
//    }
//    private void loadTrafficLights() {
//        DatabaseReference trafficRef = FirebaseDatabase.getInstance().getReference("trafficLights");
//
//        trafficRef.addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (currentLocation == null) {
//                    Toast.makeText(MapsActivity.this, "⚠ Current Location null hai", Toast.LENGTH_SHORT).show();
//                    return;
//                }
//               // if (currentLocation == null) return; // wait until location is ready
//
//                // 🔥 Sirf traffic light markers remove karo (current location ko mat hatana)
//                map.getOverlays().removeIf(overlay ->
//                        overlay instanceof Marker && !"You are here".equals(((Marker) overlay).getTitle())
//                );
//
//                boolean anyLightShown = false;
//                // ✅ Traffic lights load karo
//                for (DataSnapshot lightSnap : snapshot.getChildren()) {
//                    Double lat = lightSnap.child("lat").getValue(Double.class);
//                    Double lng = lightSnap.child("lng").getValue(Double.class);
//                    String status = lightSnap.child("status").getValue(String.class);
//
//                    if (lat != null && lng != null) {
//
//
//                        GeoPoint point = new GeoPoint(lat, lng);
//
//                        double distance = distanceBetween(currentLocation, point);
//
//                        Toast.makeText(MapsActivity.this,
//                                "Light @ " + lat + "," + lng + " Dist=" + (int) distance + "m",
//                                Toast.LENGTH_SHORT).show();
//
//
//                        if (distance <= 3000) { // sirf 3km radius ke andar
//                            Marker marker = new Marker(map);
//                            marker.setPosition(point);
//                            marker.setTitle("Traffic Light: " + status + " (" + (int) distance + "m)");
//
//                            if ("RED".equalsIgnoreCase(status)) {
//                                marker.setIcon(getResources().getDrawable(R.drawable.red));
//                            } else if ("GREEN".equalsIgnoreCase(status)) {
//
//                                marker.setIcon(getResources().getDrawable(R.drawable.green));
//                            } else {
//                                marker.setIcon(getResources().getDrawable(R.drawable.yellow));
//                            }
//
//                            map.getOverlays().add(marker);
//                            anyLightShown = true;
//                        }
//                    }
//                }
//
//                if (!anyLightShown) {
//                    Toast.makeText(MapsActivity.this, "⚠ 3km radius me koi traffic light nahi mili", Toast.LENGTH_LONG).show();
//                }
//
//
//                map.invalidate(); // refresh map
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {    Toast.makeText(MapsActivity.this, "Firebase Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();}
//        });
//    }
//
//
//
//    private double distanceBetween(GeoPoint start, GeoPoint end) {
//        double earthRadius = 6371000; // meters
//        double dLat = Math.toRadians(end.getLatitude() - start.getLatitude());
//        double dLon = Math.toRadians(end.getLongitude() - start.getLongitude());
//
//        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
//                Math.cos(Math.toRadians(start.getLatitude())) *
//                        Math.cos(Math.toRadians(end.getLatitude())) *
//                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
//
//        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
//        return earthRadius * c;
//    }
//
//    private void drawRadiusCircle(GeoPoint center, double radiusInMeters) {
//        Polygon circle = new Polygon(map);
//        circle.setPoints(Polygon.pointsAsCircle(center, radiusInMeters));
//        circle.setFillColor(0x3000FF00); // light green transparent
//        circle.setStrokeColor(Color.GREEN);
//        circle.setStrokeWidth(2f);
//        map.getOverlays().add(circle);
//    }
//}
//

package com.example.mapapp;

import android.Manifest;
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

    // ---- Live location updates ----
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            return;
        }

        LocationRequest request = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(3000)        // 3 sec
                .setFastestInterval(1500);

        fusedLocationClient.requestLocationUpdates(request, new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult lr) {
                Location loc = lr.getLastLocation();
                if (loc == null) return;

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

    // Create/update moving marker
    private void updateMyLocationMarker(Location loc) {
        if (myMarker == null) {
            myMarker = new Marker(map);
            myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            myMarker.setTitle("You are here");
            myMarker.setIcon(getResources().getDrawable(R.drawable.baseline_location_pin_24));
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
