package com.team18.model;

public class StopTime {
    public final Stop stop;
    public final int  arrivalTime;
    public final int departureTime;
    public final int stopSequence;

    public StopTime(Stop stop, int arrivalTime, int departureTime, int stopSequence) {
        this.stop = stop;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.stopSequence = stopSequence;
    }
}
