package com.team18.model;

public class Stop {
    public final String id;
    public final String name;
    public final double lat;
    public final double lon;

    public Stop(String id, String name, double lat, double lon) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
    }
}