package com.team18.model;

// One point in a GTFS shapes.txt polyline.
public class ShapePoint {
    public final double lat;
    public final double lon;
    public final int sequence;
    public final Double distTraveled; // nullable; many feeds omit shape_dist_traveled

    public ShapePoint(double lat, double lon, int sequence, Double distTraveled) {
        this.lat = lat;
        this.lon = lon;
        this.sequence = sequence;
        this.distTraveled = distTraveled;
    }
}

