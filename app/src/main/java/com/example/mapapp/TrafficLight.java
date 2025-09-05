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

//
//public class TrafficLight {
//    private Double latitude = 0.0;
//    private Double longitude = 0.0;
//    private String redAlert = "false"; // default value
//
//    public TrafficLight() { }
//
//    // getters and setters
//    public Double getLatitude() { return latitude; }
//    public void setLatitude(Double latitude) { this.latitude = latitude; }
//
//    public Double getLongitude() { return longitude; }
//    public void setLongitude(Double longitude) { this.longitude = longitude; }
//
//    public String getRedAlert() { return redAlert; }
//    public void setRedAlert(String redAlert) { this.redAlert = redAlert; }
//}
