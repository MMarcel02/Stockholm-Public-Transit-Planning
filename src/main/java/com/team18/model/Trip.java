package com.team18.model;

import java.util.ArrayList;
import java.util.List;

public class Trip {
    public final String tripId;
    public final Route route;
    public final String serviceId;

    public List<StopTime> stopTimes = new ArrayList<>();

    public Trip(String tripId, Route route, String serviceId) {
        this.tripId = tripId;
        this.route = route;
        this.serviceId = serviceId;
    }
}
