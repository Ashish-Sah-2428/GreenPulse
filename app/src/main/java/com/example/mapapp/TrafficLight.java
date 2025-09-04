package com.example.mapapp;



public class TrafficLight {
    private double latitude;
    private double longitude;
    private String color;

    public TrafficLight() {}

    public TrafficLight(double latitude, double longitude, String color) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.color = color;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getColor() { return color; }
}


