package com.team18.routing.raptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import com.team18.model.Route;
import com.team18.model.Stop;
import com.team18.model.StopTime;
import com.team18.model.Trip;
import com.team18.util.GeoCalculator;


public class RaptorBuilder {
    private final int MAX_WALK_TIME_SECONDS = 1800; // 30 minutes (can play around with this)
    private final double WALK_SPEED_MPS = 50.0 / 36.0; // 5km/h in metres/second
    private final double MAX_WALK_DISTANCE = MAX_WALK_TIME_SECONDS * WALK_SPEED_MPS;


    public RaptorNetwork build(Map<String, String> agencies, Map<String, Stop> stops, Map<String, Route> routes, Map<String, Trip> trips) {

        // Stage 0: Generate RaptorRoutes
        //         Each RaptorRoute is a unique order of stops (not neccessairly the same as the routes in GTFS data)
        //         Sort all trips in each RaptorRoute by earliest departure time
        Map<List<Stop>, List<Trip>> raptorMap = new HashMap<>();


        for (Trip trip : trips.values()) {
            List<Stop> stopsInRoute = new ArrayList<>();

            for (StopTime stopTime : trip.stopTimes) {
                stopsInRoute.add(stopTime.stop);
            }

            raptorMap.putIfAbsent(stopsInRoute, new ArrayList<>());
            raptorMap.get(stopsInRoute).add(trip);
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
        for (List<Stop> stopsInRoute : raptorMap.keySet()) {
            totalRouteStops += stopsInRoute.size();
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
        HashMap<String, Integer> stopStringToIntMap = new HashMap<>();

        int currentStopId = 0;
        for (Stop stop : stops.values()) {
            stopLookup[currentStopId] = stop;
            stopStringToIntMap.put(stop.id, currentStopId);
            currentStopId++;
        }

        // Temp list of list of routes belonging to each stop, will flatten once populated
        List<List<Integer>> tempStopRoutes = new ArrayList<List<Integer>>(stops.size());
        for (int i = 0; i < stops.size(); i++) {
            tempStopRoutes.add(new ArrayList<>());
        }

        // Temp list of list of int[targetStopId, walkTimeSeconds], will flatten once populated
        List<List<int[]>> tempTransfers = new ArrayList<List<int[]>>(stops.size());
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

                if (distanceBetweenStops <= MAX_WALK_DISTANCE) {
                    int walkTimeSeconds = (int) (distanceBetweenStops / WALK_SPEED_MPS);
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

        for (Map.Entry<List<Stop>, List<Trip>> entry : raptorMap.entrySet()) {
            List<Stop> stopsInRoute = entry.getKey();
            List<Trip> tripsInRoute = entry.getValue();
            Route parentRoute = tripsInRoute.get(0).route;

            raptorRouteLookup[currRouteIndex] = new RaptorRoute(currRouteIndex, parentRoute, stopsInRoute, tripsInRoute);

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
            raptorRouteLookup, 
            routesArr, 
            routesEnabledArr,
            routeStopsArr, 
            stopTimesArr, 
            stopsArr, 
            stopsEnabledArr,
            stopRoutes, 
            transfersArr
        );
    }

}

    