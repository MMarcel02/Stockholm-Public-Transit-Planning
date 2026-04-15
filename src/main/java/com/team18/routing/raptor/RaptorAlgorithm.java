package com.team18.routing.raptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;

import com.team18.model.RouteStep;
import com.team18.model.Stop;
import com.team18.util.GeoCalculator;

public class RaptorAlgorithm {
    
    private final int MAX_ROUNDS = 8;

    public final Stop[] stopLookup;
    public final Map<String, Integer> stringToIntMap;
    public final RaptorRoute[] raptorRouteLookup;

    public final int[] routesArr;
    public final int[] routeStopsArr;
    public final int[] stopTimesArr;
    public final int[] stopsArr;
    public final int[] stopRoutes;
    public final int[] transfersArr;

    public RaptorAlgorithm(RaptorNetwork raptorNetwork) {
        this.stopLookup = raptorNetwork.stopLookup;
        this.stringToIntMap = raptorNetwork.stringToIntMap;
        this.raptorRouteLookup = raptorNetwork.raptorRouteLookup;
        this.routesArr = raptorNetwork.routesArr;
        this.routeStopsArr = raptorNetwork.routeStopsArr;
        this.stopTimesArr = raptorNetwork.stopTimesArr;
        this.stopsArr = raptorNetwork.stopsArr;
        this.stopRoutes = raptorNetwork.stopRoutes;
        this.transfersArr = raptorNetwork.transfersArr;
    }

    public List<RouteStep> compute(double latFrom, double lonFrom, double latTo, double lonTo, int startTimeSecondsAfterMidnight) {
        int totalStops = stopLookup.length;

        // Initialize all stop times to infinity
        int[] arrivalTimesPerRound = new int[(MAX_ROUNDS + 1) * totalStops];   
        int[] bestArrivalTime = new int[totalStops];
        Arrays.fill(bestArrivalTime, Integer.MAX_VALUE);
        Arrays.fill(arrivalTimesPerRound, Integer.MAX_VALUE);

        // Backtracking arrays so we can remember what path we took
        int[] priorStopPerRound = new int[(MAX_ROUNDS + 1) * totalStops];
        int[] routeTakenPerRound = new int[(MAX_ROUNDS + 1) * totalStops];
        Arrays.fill(priorStopPerRound, -1);
        Arrays.fill(routeTakenPerRound, -1);

        // TODO: Optimize to active bag pattern later
        Set<Integer> markedStops = new HashSet<>();

        int MAX_WALK_TIME_SECONDS = 1200; // 20 minutes (can play around with this)
        double WALK_SPEED_MPS = 5000.0 / 3600.0; // 5km/h in metres/second
        double MAX_WALK_DISTANCE = MAX_WALK_TIME_SECONDS * WALK_SPEED_MPS;

        int[] walkTimeToTarget = new int[totalStops];
        int[] targetArrivalTimePerRound = new int[MAX_ROUNDS + 1];
        int[] targetParentPerRound = new int[MAX_ROUNDS + 1];

        Arrays.fill(walkTimeToTarget, Integer.MAX_VALUE);
        Arrays.fill(targetArrivalTimePerRound, Integer.MAX_VALUE);
        Arrays.fill(targetParentPerRound, -1);

        int bestTimeAtTarget = Integer.MAX_VALUE;

        // Check if we can just walk directly to our destination reasonably
        double directDist = GeoCalculator.calculateEquirectangularDistance(latFrom, lonFrom, latTo, lonTo);
        if (directDist <= MAX_WALK_DISTANCE) {
            bestTimeAtTarget = startTimeSecondsAfterMidnight + (int)(directDist / WALK_SPEED_MPS);
            targetArrivalTimePerRound[0] = bestTimeAtTarget;
            targetParentPerRound[0] = -2;
        }

        // Identify nearby (within walking distance) stops to our start and end coordinates
        for (int i = 0; i < totalStops; i++) {
            Stop stop = stopLookup[i];

            double distToTarget = GeoCalculator.calculateEquirectangularDistance(stop.lat, stop.lon, latTo, lonTo);
            if (distToTarget <= MAX_WALK_DISTANCE) {
                walkTimeToTarget[i] = (int)(distToTarget / WALK_SPEED_MPS);
            }

            double distFromSouce = GeoCalculator.calculateEquirectangularDistance(latFrom, lonFrom, stop.lat, stop.lon);
            if (distFromSouce <= MAX_WALK_DISTANCE) {
                int arrivalTime = startTimeSecondsAfterMidnight + (int)(distFromSouce / WALK_SPEED_MPS);
                arrivalTimesPerRound[i] = arrivalTime;
                bestArrivalTime[i] = arrivalTime;

                markedStops.add(i);
                priorStopPerRound[i] = -2; // Special ID to note that we came from source coordinates
            }
        }


        int roundsCompleted = 0;

        for (int round = 1; round < MAX_ROUNDS; round++) {

            // Stage 1: setting upper bound for this round by copying prev round results
            int startIndexPrevRound = (round - 1) * totalStops;
            int startIndexCurrRound = round * totalStops;
            System.arraycopy(arrivalTimesPerRound, startIndexPrevRound, arrivalTimesPerRound, startIndexCurrRound, totalStops);
            System.arraycopy(priorStopPerRound, startIndexPrevRound, priorStopPerRound, startIndexCurrRound, totalStops);
            System.arraycopy(routeTakenPerRound, startIndexPrevRound, routeTakenPerRound, startIndexCurrRound, totalStops);

            Map<Integer, Integer> routesToProcess = new HashMap<>();
            
            for (int markedStopId : markedStops) {
                // look at all routes going through this stop and add them to routes to process as : routeId, stopId
                int startIndexOfStopRoutes = stopsArr[markedStopId*2];
                int endIndexOfStopRoutes = stopsArr[(markedStopId + 1)*2];
                for (int i = startIndexOfStopRoutes; i < endIndexOfStopRoutes; i ++) {
                    int routeId = stopRoutes[i];

                    // We only want to add a stop if it comes earlier in the route than the one we boarded previously
                    // Because we wouldve already processed all the later stops after boarding anyway
                    if (routesToProcess.containsKey(routeId)) {
                        int existingStopId = routesToProcess.get(routeId);
                        if (isStopEarlierInRoute(routeId, markedStopId, existingStopId)) {
                            routesToProcess.put(routeId, markedStopId);
                        }
                    } else {
                        routesToProcess.put(routeId, markedStopId);
                    }
                }
            }

            markedStops.clear();

            // Stage 2: Traverse each route
            for (Map.Entry<Integer, Integer> e : routesToProcess.entrySet()) {
                int routeId = e.getKey();
                int earliestBoardingStopId = e.getValue();
            
                int numTrips = routesArr[routeId*4];
                int numStops = routesArr[routeId*4 + 1];
                int stopsOffset = routesArr[routeId*4 + 2];
                int stopTimesOffset = routesArr[routeId*4 + 3];

                boolean foundBoardingStop = false;
                int relativeTripIndex = -1;
                
                for (int i = 0; i < numStops ; i++) {
                    int stopIdInRoute = routeStopsArr[stopsOffset + i];
                    
                    // 1. Wait until we reach the stop we ACTUALLY transferred to
                    if (stopIdInRoute == earliestBoardingStopId) foundBoardingStop = true;

                    if (foundBoardingStop) {
                        
                        // Found a trip, so rounding the bus
                        if (relativeTripIndex != -1) {
                            // stopTimesOffset gets us to the block of trips for this route
                            // then we grab our specific trip in the block with (relativeTripIndex * numStops * 2)
                            // we want the arrivalTime for each stop in this trip with (i*2)
                            int arrivalTimeIndex = stopTimesOffset + (relativeTripIndex * numStops * 2) + (i*2);
                            int arrivalTime = stopTimesArr[arrivalTimeIndex];
                            
                            // Target Pruning
                            // we only care about this trip if it actually gets us to our destination faster than we can already get there
                            // and if it is faster to get to this stop than we can already do
                            if (arrivalTime < bestArrivalTime[stopIdInRoute] && arrivalTime < bestTimeAtTarget) {
                                bestArrivalTime[stopIdInRoute] = arrivalTime;
                                arrivalTimesPerRound[(round * totalStops) + stopIdInRoute] = arrivalTime;
                                markedStops.add(stopIdInRoute);

                                priorStopPerRound[(round * totalStops) + stopIdInRoute] = earliestBoardingStopId;
                                routeTakenPerRound[(round * totalStops) + stopIdInRoute] = routeId;
                            }
                        }

                        // Get arrival time at this stop 
                        int prevRoundArrivalTimeIndex = ((round - 1) * totalStops) + stopIdInRoute;
                        int prevRoundArrivalTime = arrivalTimesPerRound[prevRoundArrivalTimeIndex];
                        boolean canCatchEarlierBus = false;

                        // Local Pruning
                        // If we're already on a bus, check if we arrived at this stop early enough in a previous round
                        // to catch an even earlier bus than the current one we are riding, if we did get off 
                        if (relativeTripIndex != -1) {
                            // stopTimesOffset gets us to the block of trips for this route
                            // then we grab our specific trip in the block with (relativeTripIndex * numStops * 2)
                            // we want the departureTime for each stop in this trip with (i*2) + 1
                            int departureTimeIndex = stopTimesOffset + (relativeTripIndex * numStops * 2) + (i * 2) + 1;
                            int departureTime = stopTimesArr[departureTimeIndex];
                            if (prevRoundArrivalTime <= departureTime) {
                                canCatchEarlierBus = true;
                            }
                        }

                        if (relativeTripIndex == -1 || canCatchEarlierBus) {
                            if (prevRoundArrivalTime != Integer.MAX_VALUE) {
                                int newTripIndex = earliestTrip(numTrips, numStops, stopTimesOffset, i, prevRoundArrivalTime);
                                if (newTripIndex != -1) { // -1 only if there arent any earlier trips 
                                    relativeTripIndex = newTripIndex;
                                    earliestBoardingStopId = stopIdInRoute; // Fixes the negative travel times
                                }
                            }
                        }
                    }
                }
            }

            // Stage 3: Footpaths
            Set<Integer> stopsReachedByTransit = new HashSet<>(markedStops);
            for (Integer stopId : stopsReachedByTransit) {
                
                int arrivalTimeAtStopInCurrRound = arrivalTimesPerRound[(round * totalStops) + stopId];
                int transferOffsetIndexStart = stopsArr[(stopId * 2) + 1];
                int transferOffsetIndexEnd = stopsArr[(stopId + 1) *2 + 1];
                
                // Checks if walking route is faster than taking public tansit between stops
                for (int i = transferOffsetIndexStart; i < transferOffsetIndexEnd; i += 2) {
                    int targetStopId = transfersArr[i];
                    int walkTimeSeconds = transfersArr[i + 1];
                    
                    int arrivalTimeAtTarget = arrivalTimeAtStopInCurrRound + walkTimeSeconds;
                    if (arrivalTimeAtTarget < bestArrivalTime[targetStopId] && arrivalTimeAtTarget < bestTimeAtTarget) {
                        arrivalTimesPerRound[(round * totalStops) + targetStopId] = arrivalTimeAtTarget;
                        bestArrivalTime[targetStopId] = arrivalTimeAtTarget;
                        markedStops.add(targetStopId);

                        priorStopPerRound[(round * totalStops) + targetStopId] = stopId;
                        routeTakenPerRound[(round * totalStops) + targetStopId] = -1;
                    }
                }
            }

            // Stage 4: Check if we can walk from here to our destination
            // Copy over target arrival from previous round
            targetArrivalTimePerRound[round] = targetArrivalTimePerRound[round -1];
            targetParentPerRound[round] = targetParentPerRound[round -1];

            for (int stopId : markedStops) {
                if (walkTimeToTarget[stopId] != Integer.MAX_VALUE) {
                    int arrivalAtTarget = arrivalTimesPerRound[(round * totalStops) + stopId] + walkTimeToTarget[stopId];
                    if (arrivalAtTarget < bestTimeAtTarget) {
                        bestTimeAtTarget = arrivalAtTarget;
                        targetArrivalTimePerRound[round] = arrivalAtTarget;
                        targetParentPerRound[round] = stopId;
                    }
                }
            }

            roundsCompleted++;

            // We have traversed the whole network and can't find any improvements
            if (markedStops.isEmpty()) {
                break;
            }
        }

        return reconstructJourney(latFrom, lonFrom, latTo, lonTo, startTimeSecondsAfterMidnight, 
                                arrivalTimesPerRound, priorStopPerRound, routeTakenPerRound, 
                                targetArrivalTimePerRound, targetParentPerRound, totalStops, roundsCompleted);
    }

    private int earliestTrip(int numTrips, int numStops, int stopTimesOffset, int stopIndexInRoute, int prevRoundArrivalTime) {
        for (int i = 0; i < numTrips; i++) {
            // stopTimesOffset gets us to the block of trips for this route
            // then we go through each trip in the block with (i * numStops * 2)
            // in each trip we only look at the departure time of a specific stop in the route (stopIndexInRoute * 2) + 1
            int departureTimeIndex = stopTimesOffset + (i * numStops * 2) + (stopIndexInRoute * 2) + 1;
            int departureTime = stopTimesArr[departureTimeIndex];

            if (prevRoundArrivalTime <= departureTime) {
                return i; // i is the relative trip index (e.g. 3rd trip of the day for this route)
            }
        }

        return -1; // Went through all trips and there arent any earlier ones meaning end of the day, no more buses on this route
    }

    private boolean isStopEarlierInRoute(int routeId, int markedStopId, int existingStopId) {
        
        int numStopsInRoute = routesArr[routeId*4 + 1];
        int stopOffsetRouteStops = routesArr[routeId*4 + 2];

        for (int i = 0; i < numStopsInRoute ; i++) {
            int stopIdInRoute = routeStopsArr[stopOffsetRouteStops + i];
            if (stopIdInRoute == markedStopId) {
                return true;
            } else if (stopIdInRoute == existingStopId) {
                return false;
            }
        }
        return false;
    }

    private List<RouteStep> reconstructJourney(double latFrom, double lonFrom, double latTo, double lonTo, 
                                               int startTimeSecondsAfterMidnight, int[] arrivalTimesPerRound, 
                                               int[] priorStopPerRound, int[] routeTakenPerRound, 
                                               int[] targetArrivalTimePerRound, int[] targetParentPerRound, 
                                               int totalStops, int roundsCompleted) {

        List<RouteStep> routeSteps = new ArrayList<>();

        // Need to find best round
        // Fastest arrival time with fewest transfers

        int bestRound = -1;
        int bestTime = Integer.MAX_VALUE;

        for (int i = 0; i <= roundsCompleted; i++) {
            if (targetArrivalTimePerRound[i] < bestTime) {
                bestTime = targetArrivalTimePerRound[i];
                bestRound = i;
            }
        }

        // We never reached our destination so return empty list
        if (bestRound == -1 || bestTime == Integer.MAX_VALUE) {
            return routeSteps; 
        }

        System.out.println("we actually made it out");

        // We will reconstruct our journey by backtracking starting from our destination
        int lastStopId = targetParentPerRound[bestRound];

        // If the fastest route is walking directly from start to finish and never taking any transit
        if (lastStopId == -2) {
            int durationMinutes = (bestTime - startTimeSecondsAfterMidnight) / 60;
            routeSteps.add(new RouteStep(latTo, lonTo, durationMinutes, startTimeSecondsAfterMidnight));
            return routeSteps;
        }

        // Add routeStep for final walk (from last station to actual destination)
        int arrivalAtLastStop = arrivalTimesPerRound[(bestRound * totalStops) + lastStopId];
        int finalWalkDuration = (bestTime - arrivalAtLastStop) / 60;
        if (finalWalkDuration > 0) {
            routeSteps.add(new RouteStep(latTo, lonTo, finalWalkDuration, arrivalAtLastStop));
        }

        int currentStopId = lastStopId;
        int currentRound = bestRound;

        while (currentStopId != -2 && currentRound > 0) {
            int parentIndex = (currentRound * totalStops) + currentStopId;
            int parentStopId = priorStopPerRound[parentIndex];
            int routeTakenId = routeTakenPerRound[parentIndex];

            // STOP BEFORE CRASHING: Break out of the loop before trying to find Stop ID -2
            if (parentStopId == -2) {
                break;
            }

            Stop parentStop = stopLookup[parentStopId];
            Stop targetStop = stopLookup[currentStopId];

            RouteStep step;

            // If we walked make walking RouteStep
            if (routeTakenId == -1) {
                int startTime = arrivalTimesPerRound[(currentRound * totalStops) + parentStopId];
                int endTime = arrivalTimesPerRound[parentIndex];
                int durationMinutes = (endTime - startTime) / 60;

                step = new RouteStep(targetStop.lat, targetStop.lon, durationMinutes, startTime);
            } else {
                int startTime = arrivalTimesPerRound[((currentRound - 1) * totalStops) + parentStopId];
                int endTime = arrivalTimesPerRound[parentIndex];
                int durationMinutes = (endTime - startTime) / 60;

                RaptorRoute routeInfo = raptorRouteLookup[routeTakenId];

                step = new RouteStep(
                    targetStop.lat, targetStop.lon, durationMinutes, startTime, 
                    parentStop.name, routeInfo.operator, routeInfo.shortName, 
                    routeInfo.longName, routeInfo.headSign
                );
            }
            routeSteps.add(0, step);
            currentStopId = parentStopId;

            if (routeTakenId != -1) {
                currentRound--;
            }
        }

        // Add routeStep for initial walk (from actual start to first station), final step so we break out of loop
        if (currentStopId >= 0) {
            int initialWalkDuration = (arrivalTimesPerRound[currentStopId] - startTimeSecondsAfterMidnight) / 60;
            Stop firstStop = stopLookup[currentStopId];
            routeSteps.add(0, new RouteStep(firstStop.lat, firstStop.lon, initialWalkDuration, startTimeSecondsAfterMidnight));
        }

        return routeSteps;
    }   
    
}
