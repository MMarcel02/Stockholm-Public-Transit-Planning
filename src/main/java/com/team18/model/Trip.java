package com.team18.model;

import java.util.ArrayList;
import java.util.List;

public class Trip {
    public final String id;
    public final Route route;
    public final String serviceId;
    public final String headSign;
    public final String shapeId;
    public double operatingCostSEK;

    public List<StopTime> stopTimes = new ArrayList<>();

    public Trip(String id, Route route, String serviceId, String headSign, String shapeId) {
        this.id = id;
        this.route = route;
        this.serviceId = serviceId;
        this.headSign = headSign;
        this.shapeId = shapeId;

        this.operatingCostSEK = 0.0;
    }
}
