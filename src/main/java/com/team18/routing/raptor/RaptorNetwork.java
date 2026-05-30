package com.team18.routing.raptor;

import com.team18.model.Stop;

import java.util.Arrays;
import java.util.HashMap;

public class RaptorNetwork {

    public final HashMap<String, Integer> stopStringToIntMap;
    public final Stop[] stopLookup;
    public final RaptorRoute[] raptorRouteLookup;

    public final int[] routesArr;
    public final boolean[] routesEnabledArr;
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
            boolean[] routesEnabledArr,
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
        this.routesEnabledArr = routesEnabledArr;
        this.routeStopsArr = routeStopsArr;
        this.stopTimesArr = stopTimesArr;
        this.stopsArr = stopsArr;
        this.stopsEnabledArr = stopsEnabledArr;
        this.stopRoutes = stopRoutes;
        this.transfersArr = transfersArr;
    }


    public void toggleRoute(int routeId) {
        routesEnabledArr[routeId] = !routesEnabledArr[routeId];
    }

    public void disableAllRoutes() {
        Arrays.fill(routesEnabledArr, false);
    }

    public void enableAllRoutes() {
        Arrays.fill(routesEnabledArr, true);
    }

    public void toggleStop(String stopIdToToggle) {
        int stopId = stopStringToIntMap.get(stopIdToToggle);
        stopsEnabledArr[stopId] = !stopsEnabledArr[stopId];
    }

    public void disableAllStops() {
        Arrays.fill(stopsEnabledArr, false);
    }

    public void enableAllStops() {
        Arrays.fill(stopsEnabledArr, true);
    }
}