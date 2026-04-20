package com.team18.model;

//Represents one possible move from a stop
public class Edge {
    public Stop dest;
    public String mode;
    public String tripId;
    public int departureTime;
    public int travelTimeSeconds;

    public Edge(Stop dest, String mode, String tripId, int departureTime, int travelTimeSeconds) {
        this.dest = dest;
        this.mode = mode;
        this.tripId = tripId;
        this.departureTime = departureTime;
        this.travelTimeSeconds = travelTimeSeconds;
    }
}
