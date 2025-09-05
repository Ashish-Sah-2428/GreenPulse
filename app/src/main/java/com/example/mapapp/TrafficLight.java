package com.example.mapapp;



public class TrafficLight {
    private double latitude;
    private double longitude;
    private String color;
    private String alert;

    public TrafficLight() {}

    public TrafficLight(double latitude, double longitude, String color, String alert) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.color = color;
        this.alert = alert;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getColor() { return color; }
    public String getAlert() { return alert; }
}


