package com.team18.routing.raptor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.team18.model.Route;
import com.team18.model.Stop;
import com.team18.model.StopTime;
import com.team18.model.Trip;
import com.team18.optimizer.Config;
import com.team18.util.GeoCalculator;

public class RaptorBuilder {
    public class RouteKey {
        public List<Stop> stops;
        public String serviceId;

        public RouteKey(List<Stop> stops, String serviceId) {
            this.stops = stops;
            this.serviceId = serviceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RouteKey routeKey = (RouteKey) o;

            return Objects.equals(stops, routeKey.stops) && 
                Objects.equals(serviceId, routeKey.serviceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stops, serviceId);
        }
    }

    public RaptorNetwork build(Map<String, String> agencies, Map<String, Stop> stops, Map<String, Route> routes, Map<String, Trip> trips, Map<LocalDate, Set<String>> serviceByCalendar) {

        // Stage 0: Generate RaptorRoutes
        //         Each RaptorRoute is a unique order of stops (not neccessairly the same as the routes in GTFS data)
        //         Sort all trips in each RaptorRoute by earliest departure time
        Map<RouteKey, List<Trip>> raptorMap = new HashMap<>();
        
        for (Trip trip : trips.values()) {
            List<Stop> stopsInRoute = new ArrayList<>();
            for (StopTime stopTime : trip.stopTimes) {
                stopsInRoute.add(stopTime.stop);
            }

            RouteKey routeKey = new RouteKey(stopsInRoute, trip.serviceId);

            raptorMap.putIfAbsent(routeKey, new ArrayList<>());
            raptorMap.get(routeKey).add(trip);
        }

        for (List<Trip> raptorTrips : raptorMap.values()) {
            raptorTrips.sort((trip1, trip2) -> {
                int firstTripForTrip1 = trip1.stopTimes.get(0).departureTime;
                int firstTripForTrip2 = trip2.stopTimes.get(0).departureTime;
                return Integer.compare(firstTripForTrip1, firstTripForTrip2);
            });
        }

        // Stage 1: Build RaptorNetwork
        //          Calculate size needed for all primitve arrays
        int totalRaptorRoutes = raptorMap.size();
        
        int totalRouteStops = 0;
        for (RouteKey routeKey : raptorMap.keySet()) {
            totalRouteStops += routeKey.stops.size();
        }

        // Calculate how many total stop time events there are in all trips
        int totalStopTimes = 0;
        for (List<Trip> raptorTrips : raptorMap.values()) {
            for (Trip trip : raptorTrips) {
                totalStopTimes += trip.stopTimes.size();
            }
        }
        
        // Need an int ID for raptor, but have String ID in GTFS, so we make new int ids and a lookup table
        Stop[] stopLookup = new Stop[stops.size()];
        Map<String, Integer> stopStringToIntMap = new HashMap<>();

        int currentStopId = 0;
        for (Stop stop : stops.values()) {
            stopLookup[currentStopId] = stop;
            stopStringToIntMap.put(stop.id, currentStopId);
            currentStopId++;
        }

        // Temp list of list of routes belonging to each stop, will flatten once populated
        List<List<Integer>> tempStopRoutes = new ArrayList<>(stops.size());
        for (int i = 0; i < stops.size(); i++) {
            tempStopRoutes.add(new ArrayList<>());
        }

        // Temp list of list of int[targetStopId, walkTimeSeconds], will flatten once populated
        List<List<int[]>> tempTransfers = new ArrayList<>(stops.size());
        for (int i = 0; i < stops.size(); i++) {
            tempTransfers.add(new ArrayList<>());
        }
        
        int totalTransferCount = 0;
        for (int i = 0; i < stops.size(); i++) {
            Stop initialStop = stopLookup[i];
            for (int j = 0; j < stops.size(); j++) {
                if (i == j) continue;
                Stop targetStop = stopLookup[j];

                double distanceBetweenStops = GeoCalculator.calculateEquirectangularDistance(initialStop.lat, initialStop.lon, targetStop.lat, targetStop.lon);

                if (distanceBetweenStops <= Config.MAX_WALK_DISTANCE_TRANSFERS_METRES) {
                    int walkTimeSeconds = (int) (distanceBetweenStops / Config.WALK_SPEED_MPS) + Config.TRANSFER_PENALTY;
                    tempTransfers.get(i).add(new int[]{j, walkTimeSeconds});
                    totalTransferCount++;
                }
            }
        }

        // Stage 2: Initialize all primitive arrays
        //          raptorRouteLookup[] indexed by routeId, for fetching our actual route information after algorithm finishes
        //          routesArr[] each route has 4 values in routesArr [#trips, #stops, pointer to routeStops, pointer to stopTimes]
        //          routeStopsArr[] all stops for every route
        //          stopTimesArr[] all stopTimes for every trip for every route, each stop time has 2 values [arrivalTime, departureTime]
        //          stopsArr[] all stops, each stop has 2 values [routeOffset, transferOffset]
        //          stopRoutes[] all routes for each stop 
        //          transfersArr[] all transfers within walking distance of each stop, each transfer has 2 values [targetStopId, walkTimeSeconds]
        
        Map<String, List<Integer>> parentRouteToRaptorRoutesMap = new HashMap<>();
        RaptorRoute[] raptorRouteLookup = new RaptorRoute[totalRaptorRoutes];
        int[] routesArr = new int[totalRaptorRoutes * 4];   
        int[] routeStopsArr = new int[totalRouteStops];     
        int[] stopTimesArr = new int[totalStopTimes * 2];   
        int[] stopsArr = new int[(stops.size() * 2 ) + 2];
        int[] stopRoutes = new int[totalRouteStops];        
        int[] transfersArr = new int[totalTransferCount * 2]; 
        
        int currRouteIndex = 0;
        int currStopsOffset = 0;
        int currStopTimesOffset = 0;

        for (Map.Entry<RouteKey, List<Trip>> entry : raptorMap.entrySet()) {
            RouteKey routeKey = entry.getKey();
            List<Stop> stopsInRoute = routeKey.stops;
            List<Trip> tripsInRoute = entry.getValue();
            Route parentRoute = tripsInRoute.get(0).route;

            RaptorRoute raptorRoute = new RaptorRoute(currRouteIndex, parentRoute, stopsInRoute, tripsInRoute, routeKey.serviceId);
            parentRouteToRaptorRoutesMap.putIfAbsent(parentRoute.id, new ArrayList<>());
            List<Integer> raptorRoutesBelongingToParent = parentRouteToRaptorRoutesMap.get(parentRoute.id);
            raptorRoutesBelongingToParent.add(currRouteIndex);
            raptorRouteLookup[currRouteIndex] = raptorRoute;

            routesArr[currRouteIndex*4] = tripsInRoute.size();
            routesArr[currRouteIndex*4 + 1] = stopsInRoute.size();
            routesArr[currRouteIndex*4 + 2] = currStopsOffset;
            routesArr[currRouteIndex*4 + 3] = currStopTimesOffset;

            for (Stop stop : stopsInRoute) {
                int internalStopId = stopStringToIntMap.get(stop.id);
                routeStopsArr[currStopsOffset] = internalStopId;
                currStopsOffset++;
                tempStopRoutes.get(internalStopId).add(currRouteIndex);
            }

            for (Trip trip : tripsInRoute) {
                for (StopTime stopTime : trip.stopTimes) {
                    stopTimesArr[currStopTimesOffset] = stopTime.arrivalTime;
                    stopTimesArr[currStopTimesOffset + 1] = stopTime.departureTime;
                    currStopTimesOffset += 2;
                }
            }
            currRouteIndex++;
        }

        // Stage 3: Flatten dynamic arrays to primitive arrays

        // Flattening our tempStopRoutes 
        int currStopRoutesOffset = 0;

        for (int stopId = 0; stopId < tempStopRoutes.size(); stopId++) {
            List<Integer> routesThroughStop = tempStopRoutes.get(stopId);
            stopsArr[stopId * 2] = currStopRoutesOffset;
            
            for (int routeId : routesThroughStop) {
                stopRoutes[currStopRoutesOffset] = routeId;
                currStopRoutesOffset++;
            }
        }
        
        // Flattening our tempTransfer 
        int currTransferOffset = 0;
        
        for (int stopId = 0; stopId < tempTransfers.size(); stopId++) {
            stopsArr[stopId * 2 + 1] = currTransferOffset;
            
            List<int[]> transfersForStop = tempTransfers.get(stopId);

            for (int[] transfer : transfersForStop) {
                transfersArr[currTransferOffset] = transfer[0];
                transfersArr[currTransferOffset + 1] = transfer[1];
                currTransferOffset +=2;
            }
        }

        stopsArr[stops.size() * 2] = currStopRoutesOffset;
        stopsArr[stops.size() * 2 + 1] = currTransferOffset;

        // Stage 4: Setting up dynamic arrays to see which stops / routes are active

        boolean[] stopsEnabledArr = new boolean[stops.size()];
        Arrays.fill(stopsEnabledArr, true);

        boolean[] routesEnabledArr = new boolean[totalRaptorRoutes];
        Arrays.fill(routesEnabledArr, true);

        return new RaptorNetwork(
            stopStringToIntMap,
            stopLookup, 
            routes,
            parentRouteToRaptorRoutesMap,
            raptorRouteLookup, 
            routesArr, 
            routesEnabledArr,
            routeStopsArr, 
            stopTimesArr, 
            stopsArr, 
            stopsEnabledArr,
            stopRoutes, 
            transfersArr,
            serviceByCalendar
        );
    }
}

    
