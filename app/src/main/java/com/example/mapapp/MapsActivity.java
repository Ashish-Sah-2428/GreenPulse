
package com.example.mapapp;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import kotlinx.coroutines.*; // For Kotlin coroutine, ignore if Java
import okhttp3.OkHttpClient;

import org.osmdroid.views.overlay.Polyline;
import android.graphics.Color;

import android.location.Address;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

import java.util.List;
import java.util.Locale;

public class MapsActivity extends AppCompatActivity {

    private AutoCompleteTextView etPlace;
    private ArrayAdapter<String> placeAdapter;

    private MapView map;
    private IMapController controller;
    private FusedLocationProviderClient fusedLocationClient;

    private GeoPoint currentLocation;
    private Marker myMarker;
    private Polygon radiusCircle;
    private FolderOverlay trafficOverlay;
    private Polyline currentRoute;
    private Marker destinationMarker;
    private boolean firstFix = true;

    private DatabaseReference trafficRef;
    private ValueEventListener trafficListener;
    private List<Polygon> redAlertCircles = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // OSMDroid config
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

        etPlace = findViewById(R.id.etPlace);
        placeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line);
        etPlace.setAdapter(placeAdapter);

// Auto-suggestions
        etPlace.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() < 3) return; // 3 chars se suggestions

                new Thread(() -> {
                    try {
                        Geocoder geocoder = new Geocoder(MapsActivity.this, Locale.getDefault());
                        List<Address> addresses = geocoder.getFromLocationName(s.toString(), 5);
                        List<String> suggestions = new ArrayList<>();
                        for (Address addr : addresses) {
                            suggestions.add(addr.getFeatureName() + ", " + addr.getLocality());
                        }

                        runOnUiThread(() -> {
                            placeAdapter.clear();
                            placeAdapter.addAll(suggestions);
                            placeAdapter.notifyDataSetChanged();
                            etPlace.showDropDown();
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

// Select place → draw route
        etPlace.setOnItemClickListener((parent, view, position, id) -> {
            String selection = placeAdapter.getItem(position);
            if (selection == null) return;

            new Thread(() -> {
                try {
                    Geocoder geocoder = new Geocoder(MapsActivity.this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocationName(selection, 1);
                    if (addresses == null || addresses.isEmpty()) return;

                    GeoPoint destination = new GeoPoint(addresses.get(0).getLatitude(), addresses.get(0).getLongitude());

                    runOnUiThread(() -> {
                        drawRoute(currentLocation, destination);
                        controller.setCenter(destination);
                    });

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        });

    }

    // Live location updates
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            return;
        }

        LocationRequest request = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(2000)
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

                renderTrafficWithinRadius();

                if (firstFix) {
                    controller.setCenter(currentLocation);
                    firstFix = false;
                   // GeoPoint destination = new GeoPoint(28.85, 78.8); // Example destination
                    //drawRoute(currentLocation, destination);
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
//    private void updateMyLocationMarker(Location loc) {
//        if (myMarker == null) {
//            myMarker = new Marker(map);
//            myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
//            myMarker.setTitle("You are here");
//            myMarker.setIcon(getResources().getDrawable(R.drawable.baseline_man_24));
//            map.getOverlays().add(myMarker);
//        }
//        myMarker.setPosition(currentLocation);
//
//        // optional: rotate by bearing if available
//        if (loc.hasBearing()) {
//            myMarker.setRotation(loc.getBearing());
//        }
//    }



    private void updateMyLocationMarker(Location loc) {
        if (myMarker == null) {
            myMarker = new Marker(map);
            myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            myMarker.setIcon(getResources().getDrawable(R.drawable.baseline_man_24));
            map.getOverlays().add(myMarker);
        }

        myMarker.setPosition(currentLocation);

        // Rotate by bearing if available
        if (loc.hasBearing()) {
            myMarker.setRotation(loc.getBearing());
        }

        // Geocoder to get street + city
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(
                    loc.getLatitude(),
                    loc.getLongitude(),
                    1
            );
            if (addresses != null && !addresses.isEmpty()) {
                String street = addresses.get(0).getThoroughfare(); // Street name
                String city = addresses.get(0).getLocality();       // City name
                String title = "";

                if (street != null) title += street;
                if (city != null) title += (title.isEmpty() ? "" : ", ") + city;
                if (title.isEmpty()) title = "You are here";

                myMarker.setTitle(title);
            }
        } catch (IOException e) {
            e.printStackTrace();
            myMarker.setTitle("You are here");
        }
    }



    private void updateRadiusCircle(GeoPoint center, double radiusMeters) {
        if (radiusCircle == null) {
            radiusCircle = new Polygon(map);
            radiusCircle.setFillColor(0x2000FF00);
            radiusCircle.setStrokeColor(0xFF00AA00);
            radiusCircle.setStrokeWidth(2f);
            radiusCircle.setTitle(currentLocation.getLongitude()+","+currentLocation.getLatitude());
            map.getOverlays().add(radiusCircle);
        }
        radiusCircle.setPoints(Polygon.pointsAsCircle(center, radiusMeters));
    }

    private void drawRoute(GeoPoint start, GeoPoint end) {
        runOnUiThread(() -> {
            // Remove previous route if exists
            if (currentRoute != null) {
                map.getOverlays().remove(currentRoute);
                currentRoute = null;
            }

            // Remove previous destination marker
            if (destinationMarker != null) {
                map.getOverlays().remove(destinationMarker);
                destinationMarker = null;
            }

            // Add new destination marker
            destinationMarker = new Marker(map);
            destinationMarker.setPosition(end);
            destinationMarker.setTitle("Destination");
            destinationMarker.setIcon(getResources().getDrawable(R.drawable.ic_destination)); // apna icon
            map.getOverlays().add(destinationMarker);

            map.invalidate(); // Refresh map
        });

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                String url = "https://router.project-osrm.org/route/v1/driving/"
                        + start.getLongitude() + "," + start.getLatitude() + ";"
                        + end.getLongitude() + "," + end.getLatitude()
                        + "?overview=full&geometries=geojson";

                okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
                okhttp3.Response response = client.newCall(request).execute();
                String jsonString = response.body().string();

                JSONObject json = new JSONObject(jsonString);
                JSONArray coords = json.getJSONArray("routes")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                List<GeoPoint> roadPoints = new ArrayList<>();
                for (int i = 0; i < coords.length(); i++) {
                    JSONArray point = coords.getJSONArray(i);
                    double lon = point.getDouble(0);
                    double lat = point.getDouble(1);
                    roadPoints.add(new GeoPoint(lat, lon));
                }

                runOnUiThread(() -> {
                    // Draw new route
                    Polyline polyline = new Polyline();
                    polyline.setPoints(roadPoints);
                    polyline.setColor(Color.BLUE);
                    polyline.setWidth(8f);
                    map.getOverlays().add(polyline);

                    currentRoute = polyline; // save reference

                    map.invalidate();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    //  Firebase traffic lights
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
    //private List<Polygon> redAlert = new ArrayList<>();

    private void renderTrafficWithinRadius(DataSnapshot snapshot) {
        if (currentLocation == null) return;

        // Clear only traffic markers, keep myMarker & main radiusCircle
        trafficOverlay.getItems().clear();

        // Remove old red alert circles
        for (Polygon c : redAlertCircles) {
            map.getOverlays().remove(c);
        }
        redAlertCircles.clear();

        for (DataSnapshot lightSnap : snapshot.getChildren()) {
            Double lat = lightSnap.child("lat").getValue(Double.class);
            Double lng = lightSnap.child("lng").getValue(Double.class);
            String status = lightSnap.child("status").getValue(String.class);
            String redAlert = lightSnap.child("red_alert").getValue(String.class);

            if (lat == null || lng == null) continue;

            GeoPoint p = new GeoPoint(lat, lng);
            double dist = distanceMeters(currentLocation, p);

            // Only traffic markers within 3km
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

            // Add red alert circle if redalert == "true" and within any distance
            if ("true".equalsIgnoreCase(redAlert)) {
                Polygon alertCircle = new Polygon(map);
                alertCircle.setPoints(Polygon.pointsAsCircle(p, 300)); // 300 meters
                alertCircle.setFillColor(0x40FF0000); // semi-transparent red
                alertCircle.setStrokeColor(0xFFFF0000);
                alertCircle.setStrokeWidth(2f);
                alertCircle.setTitle("High Traffic");

                map.getOverlays().add(alertCircle);
                redAlertCircles.add(alertCircle);


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