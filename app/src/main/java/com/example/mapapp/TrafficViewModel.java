package com.example.mapapp;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.mapapp.TrafficLight;
import com.example.mapapp.TrafficRepository;

import java.util.List;

public class TrafficViewModel extends ViewModel {
    private final TrafficRepository repository;
    private final LiveData<List<TrafficLight>> trafficLights;

    public TrafficViewModel() {
        repository = new TrafficRepository();
        trafficLights = repository.getTrafficLights();
    }

    public LiveData<List<TrafficLight>> getTrafficLights() {
        return trafficLights;
    }
}
