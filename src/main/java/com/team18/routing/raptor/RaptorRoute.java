package com.team18.routing.raptor;

import com.team18.model.Route;
import com.team18.model.Stop;
import com.team18.model.Trip;
import java.util.List;

// A route in the RAPTOR algorithm has some specific requirements, so we reformat our parsed Route objects to fit this,
// also we're passing over everything needed for our routeInfo so that we can garbage collect the old route, stops, trips objects

public class RaptorRoute {
    
    public final String id;
    public final List<Stop> stops;
    public final List<Trip> trips;

    public final String operator;
    public final String shortName;
    public final String longName;
    public final String headSign;

    public RaptorRoute(String id, Route parentRoute, List<Stop> patternStops, List<Trip> patternTrips) {
        this.id = id;
        
        this.stops = patternStops; 
        this.trips = patternTrips;
        
        this.operator = parentRoute.operator;
        this.shortName = parentRoute.shortName;
        this.longName = parentRoute.longName;
        
        // We try to get the headSign from one of the trip objects (blank for most in Stockholm GTFS)
        if (!patternTrips.isEmpty() && patternTrips.get(0).headSign != null) {
            this.headSign = patternTrips.get(0).headSign;
        } else {
            this.headSign = "";
        }
    }
}