package com.example.mapapp;



import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mapapp.TrafficLight;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class TrafficRepository {

    private final DatabaseReference trafficRef;
    private final MutableLiveData<List<TrafficLight>> trafficLightsLiveData = new MutableLiveData<>();

    public TrafficRepository() {
        trafficRef = FirebaseDatabase.getInstance().getReference("trafficLights");

        fetchTrafficLights();
    }

    private void fetchTrafficLights() {
        trafficRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<TrafficLight> list = new ArrayList<>();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    TrafficLight light = snap.getValue(TrafficLight.class);
                    if (light != null) {
                        list.add(light);
                    }
                }
                trafficLightsLiveData.setValue(list);
              //  Toast.makeText(MyApplication.getAppContext(), "Repo: " + list.size() + " lights fetched", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { Log.e("FirebaseError!", error.getMessage());}
        });
    }

    public LiveData<List<TrafficLight>> getTrafficLights() {
        return trafficLightsLiveData;
    }
}

