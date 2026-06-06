package com.team18.routing.raptor;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.team18.model.Route;
import com.team18.model.Stop;

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
    public final Map<LocalDate, Set<String>> serviceByCalendar;

    public Set<String> disabledParentRoutesByOptimizer = new HashSet<>();

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
            Map<LocalDate, Set<String>> serviceByCalendar
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
    }

    public RaptorNetwork copyForMultithreading() {
        RaptorNetwork localNetwork = new RaptorNetwork(
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
            transfersArr,
            serviceByCalendar
        );

        localNetwork.disabledParentRoutesByOptimizer = new HashSet<>(this.disabledParentRoutesByOptimizer);
        return localNetwork;
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

    public void disableParentRouteOptimizer(String routeId) {
        disabledParentRoutesByOptimizer.add(routeId);
    }

    public void setByCalendar(LocalDate date) {
        Set<String> ids = serviceByCalendar.get(date);
        for(RaptorRoute route : raptorRouteLookup){
            boolean isScheduled = ids.contains(route.serviceId);
            boolean isDisabledByOptimizer = disabledParentRoutesByOptimizer.contains(route.parentRoute.id);
            
            routesEnabledArr[route.id] = isScheduled && !isDisabledByOptimizer;
        }
    }
}