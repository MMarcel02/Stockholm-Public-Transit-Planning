package com.team18.routing.raptor;

import com.team18.model.Stop;

import java.util.HashMap;

public class RaptorNetwork {

    public final HashMap<String, Integer> stopStringToIntMap;
    public final Stop[] stopLookup;
    public final RaptorRoute[] raptorRouteLookup;

    public final int[] routesArr;
    public final int[] routeStopsArr;
    public final int[] stopTimesArr;
    public final int[] stopsArr;
    public final boolean[] stopsEnabledArr;
    public final int[] stopRoutes;
    public final int[] transfersArr;

    public RaptorNetwork(
            HashMap<String, Integer> stopStringToIntMap,
            Stop[] stopLookup, 
            RaptorRoute[] raptorRouteLookup,
            int[] routesArr, 
            int[] routeStopsArr, 
            int[] stopTimesArr, 
            int[] stopsArr, 
            boolean[] stopsEnabledArr,
            int[] stopRoutes, 
            int[] transfersArr) {
        
        this.stopStringToIntMap = stopStringToIntMap;
        this.stopLookup = stopLookup;
        this.raptorRouteLookup = raptorRouteLookup;
        this.routesArr = routesArr;
        this.routeStopsArr = routeStopsArr;
        this.stopTimesArr = stopTimesArr;
        this.stopsArr = stopsArr;
        this.stopsEnabledArr = stopsEnabledArr;
        this.stopRoutes = stopRoutes;
        this.transfersArr = transfersArr;
    }
}