package com.team18.routing.raptor;

import com.team18.model.Stop;
import com.team18.model.Route;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class RaptorNetwork {

    public final Map<String, Integer> stopStringToIntMap;
    public final Stop[] stopLookup;

    public final Map<String, Route> parentRouteLookup;
    public final Map<String, List<Integer>> parentRouteToRaptorRoutesMap;
    public final RaptorRoute[] raptorRouteLookup;

    public final int[] routesArr;
    public final boolean[] routesEnabledArr;
    public final int[] routeStopsArr;
    public final int[] stopTimesArr;
    public final int[] stopsArr;
    public final boolean[] stopsEnabledArr;
    public final int[] stopRoutes;
    public final int[] transfersArr;
    public final Map<LocalDate, List<String>> serviceByCalendar;
    public final Map<String, Trip> trips;

    public RaptorNetwork(
            Map<String, Integer> stopStringToIntMap,
            Stop[] stopLookup, 
            Map<String, Route> parentRouteLookup,
            Map<String, List<Integer>> parentRouteToRaptorRoutesMap,
            RaptorRoute[] raptorRouteLookup,
            int[] routesArr, 
            boolean[] routesEnabledArr,
            int[] routeStopsArr, 
            int[] stopTimesArr, 
            int[] stopsArr, 
            boolean[] stopsEnabledArr,
            int[] stopRoutes, 
            int[] transfersArr,
            Map<LocalDate, List<String>> serviceByCalendar,
            Map<String, Trip> trips
             ) {
            
        this.stopStringToIntMap = stopStringToIntMap;
        this.stopLookup = stopLookup;
        this.parentRouteLookup = parentRouteLookup;
        this.parentRouteToRaptorRoutesMap = parentRouteToRaptorRoutesMap;
        this.raptorRouteLookup = raptorRouteLookup;
        this.routesArr = routesArr;
        this.routesEnabledArr = routesEnabledArr;
        this.routeStopsArr = routeStopsArr;
        this.stopTimesArr = stopTimesArr;
        this.stopsArr = stopsArr;
        this.stopsEnabledArr = stopsEnabledArr;
        this.stopRoutes = stopRoutes;
        this.transfersArr = transfersArr;
        this.serviceByCalendar = serviceByCalendar;
        this.trips = trips;
    }

    public RaptorNetwork copyForMultithreading() {
        return new RaptorNetwork(
            stopStringToIntMap,
            stopLookup, 
            parentRouteLookup,
            parentRouteToRaptorRoutesMap,
            raptorRouteLookup,
            routesArr, 
            Arrays.copyOf(routesEnabledArr, routesEnabledArr.length), 
            routeStopsArr,
            stopTimesArr,
            stopsArr,
            Arrays.copyOf(stopsEnabledArr, stopsEnabledArr.length),  
            stopRoutes,
            transfersArr
        );
    }

    public void toggleRaptorRoute(int raptorRouteId) {
        routesEnabledArr[raptorRouteId] = !routesEnabledArr[raptorRouteId];
    }

    public void toggleParentRoute(String parentRouteId) {
        List<Integer> raptorRoutesToToggle = parentRouteToRaptorRoutesMap.get(parentRouteId);
        int totalRaptorRoutesToToggle = raptorRoutesToToggle.size();
        for (int i = 0; i < totalRaptorRoutesToToggle; i++) {
            int raptorRouteId = raptorRoutesToToggle.get(i);
            routesEnabledArr[raptorRouteId] = !routesEnabledArr[raptorRouteId];
        }
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

    public void disableByCalendar(LocalDate date) {
        List<String> ids = serviceByCalendar.get(date);
        for(String id : ids){
            for(RaptorRoute route : raptorRouteLookup){
                if(route.)
            }
        }
    }
}