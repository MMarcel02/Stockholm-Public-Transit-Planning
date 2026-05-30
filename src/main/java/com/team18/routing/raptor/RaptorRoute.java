package com.team18.routing.raptor;

import com.team18.model.Route;
import com.team18.model.Stop;
import com.team18.model.Trip;
import java.util.List;

// A route in the RAPTOR algorithm is a unique sequence of stops, this is not guaranteed by routes.txt in all GTFS datasets
// so we use RaptorBuilder to make specific raptor routes

public class RaptorRoute {
    
    public final int id;
    public final List<Stop> stops;
    public final List<Trip> trips;
    public final Route parentRoute;

    public final String shapeId;
    public final String headSign;

    public RaptorRoute(int id, Route parentRoute, List<Stop> patternStops, List<Trip> patternTrips) {
        this.id = id;
        
        this.stops = patternStops; 
        this.trips = patternTrips;
        this.parentRoute = parentRoute;
        
        this.shapeId = patternTrips.get(0).shapeId;
        
        // We try to get the headSign from one of the trip objects (blank for most in Stockholm GTFS)
        if (!patternTrips.isEmpty() && patternTrips.get(0).headSign != null) {
            this.headSign = patternTrips.get(0).headSign;
        } else {
            this.headSign = "";
        }
    }   
}
