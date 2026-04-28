package com.team18.routing.raptor;

import com.team18.model.Stop;

public class RaptorNetwork {

    public final Stop[] stopLookup;
    public final RaptorRoute[] raptorRouteLookup;

    public final int[] routesArr;
    public final int[] routeStopsArr;
    public final int[] stopTimesArr;
    public final int[] stopsArr;
    public final int[] stopRoutes;
    public final int[] transfersArr;

    public RaptorNetwork(
            Stop[] stopLookup, 
            RaptorRoute[] raptorRouteLookup,
            int[] routesArr, 
            int[] routeStopsArr, 
            int[] stopTimesArr, 
            int[] stopsArr, 
            int[] stopRoutes, 
            int[] transfersArr) {
        
        this.stopLookup = stopLookup;
        this.raptorRouteLookup = raptorRouteLookup;
        this.routesArr = routesArr;
        this.routeStopsArr = routeStopsArr;
        this.stopTimesArr = stopTimesArr;
        this.stopsArr = stopsArr;
        this.stopRoutes = stopRoutes;
        this.transfersArr = transfersArr;
    }
}