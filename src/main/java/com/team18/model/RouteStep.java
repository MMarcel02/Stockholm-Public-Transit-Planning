package com.team18.model;

public class RouteStep {
    private String mode;
    private String destinationName;
    private int duration;

    public RouteStep(String mode, String destinationName, int duration) {
        this.mode = mode;
        this.destinationName = destinationName;
        this.duration = duration; // in minutes
    }

    public String getMode() {
        return mode;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public int getDuration() {
        return duration;
    }
}