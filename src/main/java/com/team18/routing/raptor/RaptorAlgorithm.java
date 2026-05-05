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

    private final int MAX_WALK_TIME_SECONDS = 1800; // 30 minutes (can play around with this)
    private final double WALK_SPEED_MPS = 50.0 / 36.0; // 5km/h in metres/second
    private final double MAX_WALK_DISTANCE = MAX_WALK_TIME_SECONDS * WALK_SPEED_MPS;

    private final int MAX_ROUNDS = 8;

    private final Stop[] stopLookup;
    private final RaptorRoute[] raptorRouteLookup;

    private final int[] routesArr;
    private final int[] routeStopsArr;
    private final int[] stopTimesArr;
    private final int[] stopsArr;
    private final int[] stopRoutes;
    private final int[] transfersArr;

    public RaptorAlgorithm(RaptorNetwork raptorNetwork) {
        this.stopLookup = raptorNetwork.stopLookup;
        this.raptorRouteLookup = raptorNetwork.raptorRouteLookup;
        this.routesArr = raptorNetwork.routesArr;
        this.routeStopsArr = raptorNetwork.routeStopsArr;
        this.stopTimesArr = raptorNetwork.stopTimesArr;
        this.stopsArr = raptorNetwork.stopsArr;
        this.stopRoutes = raptorNetwork.stopRoutes;
        this.transfersArr = raptorNetwork.transfersArr;
    }

    public List<RouteStep> compute(double latFrom, double lonFrom, double latTo, double lonTo, int startTimeSecondsAfterMidnight) {
        // Stage 0: Initialization:
        //         Create needed arrays and fill with default values
        //         Update arrival time and mark all stops we can walk to from our source
        //         Identify and update walkTimeToDestination all stops within walking distance
        //         Check if we can walk directly to our destination

        // To keep track of our timing:
        // arrivalTimesPerRound[] : best arrival time for every stop in each round
        // bestArrivalTime[] : best overall arrival time for every stop

        int totalStops = stopLookup.length;
        int[] arrivalTimesPerRound = new int[(MAX_ROUNDS + 1) * totalStops];
        int[] bestArrivalTime = new int[totalStops];
        Arrays.fill(bestArrivalTime, Integer.MAX_VALUE);
        Arrays.fill(arrivalTimesPerRound, Integer.MAX_VALUE);

        // To dynamically track which stops and routes we are checking:
        // Set<stopId> markedStops
        // Map<routeId, earliestBoardingStopId> routesToProcess

        Set<Integer> markedStops = new HashSet<>();
        Map<Integer, Integer> routesToProcess = new HashMap<>();

        // To keep track of our walks (needed since destination is not a stop but a random coordinate)
        // walkTimeToDestination[] : walk time in seconds for every stop to destination coordinates
        // destinationBestArrivalTimePerRound[] : earliest arrival time at destination in each round
        // destinationPriorStopPerRound[] : last stop we reached before we walked to our destination

        int[] walkTimeToDestination = new int[totalStops];
        int[] destinationBestArrivalTimePerRound = new int[MAX_ROUNDS + 1];
        int[] destinationPriorStopPerRound = new int[MAX_ROUNDS + 1];
        Arrays.fill(walkTimeToDestination, Integer.MAX_VALUE);
        Arrays.fill(destinationBestArrivalTimePerRound, Integer.MAX_VALUE);
        Arrays.fill(destinationPriorStopPerRound, -1);

        // To reconstruct our jouney later:
        // priorStopPerRound[] : for every stop in each round, records the stop we were at before it
        // routeTakenPerRound[] : for every stop in each round, records the route we took to arrive at it

        int[] priorStopPerRound = new int[(MAX_ROUNDS + 1) * totalStops];
        int[] routeTakenPerRound = new int[(MAX_ROUNDS + 1) * totalStops];
        Arrays.fill(priorStopPerRound, -1);
        Arrays.fill(routeTakenPerRound, -1);


        // Identify stops within walking distance to our source and destination coordinates
        //          Stops we can walk to initially are identified by a -2 in priorStopPerRound
        //          So we know about it during reconstruction

        for (int i = 0; i < totalStops; i++) {
            Stop stop = stopLookup[i];

            double distFromSouce = GeoCalculator.calculateEquirectangularDistance(latFrom, lonFrom, stop.lat, stop.lon);
            if (distFromSouce <= MAX_WALK_DISTANCE) {
                int arrivalTime = startTimeSecondsAfterMidnight + (int)(distFromSouce / WALK_SPEED_MPS);
                arrivalTimesPerRound[i] = arrivalTime;
                bestArrivalTime[i] = arrivalTime;

                markedStops.add(i);
                priorStopPerRound[i] = -2;
            }

            double distToDestination = GeoCalculator.calculateEquirectangularDistance(stop.lat, stop.lon, latTo, lonTo);
            if (distToDestination <= MAX_WALK_DISTANCE) {
                walkTimeToDestination[i] = (int)(distToDestination / WALK_SPEED_MPS);
            }
        }

        // Check if we can walk directly to our destination
        //          This case is uniquely identified by the -2 in destinationPriorStopPerRound
        //          So we know about it during reconstruction

        int bestTimeAtDestination = Integer.MAX_VALUE;
        double directDist = GeoCalculator.calculateEquirectangularDistance(latFrom, lonFrom, latTo, lonTo);
        if (directDist <= MAX_WALK_DISTANCE) {
            bestTimeAtDestination = startTimeSecondsAfterMidnight + (int)(directDist / WALK_SPEED_MPS);
            destinationBestArrivalTimePerRound[0] = bestTimeAtDestination;
            destinationPriorStopPerRound[0] = -2;
        }

        int roundsCompleted = 0;

        for (int round = 1; round < MAX_ROUNDS; round++) {

            // Stage 1: Copy results and mark
            //          Copy previous rounds results to set our worst case time for this round
            //          Empty our routesToProcess to set up for current round
            //          Mark all routes going through each marked stop as a routeToProcess
            //          If the same route is going through a few marked stops, we only board the earliest marked stop in the route
            //          Empty out markedStops to set up for next round

            destinationBestArrivalTimePerRound[round] = destinationBestArrivalTimePerRound[round -1];
            destinationPriorStopPerRound[round] = destinationPriorStopPerRound[round -1];

            int startIndexPrevRound = (round - 1) * totalStops;
            int startIndexCurrRound = round * totalStops;
            System.arraycopy(arrivalTimesPerRound, startIndexPrevRound, arrivalTimesPerRound, startIndexCurrRound, totalStops);
            System.arraycopy(priorStopPerRound, startIndexPrevRound, priorStopPerRound, startIndexCurrRound, totalStops);
            System.arraycopy(routeTakenPerRound, startIndexPrevRound, routeTakenPerRound, startIndexCurrRound, totalStops);

            routesToProcess.clear();

            for (int markedStopId : markedStops) {
                int startIndexOfStopRoutes = stopsArr[markedStopId*2];
                int endIndexOfStopRoutes = stopsArr[(markedStopId + 1)*2];
                for (int i = startIndexOfStopRoutes; i < endIndexOfStopRoutes; i ++) {
                    int routeId = stopRoutes[i];
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

            // Stage 2: Traverse each route:
            //          Deconstruct each route to get its data (routesArr[] is interleaved)
            //          Go through all stops in the route until we reach our earliestBoardingStopId
            //          Board the earliest next trip leaving that stop

            for (Map.Entry<Integer, Integer> entry : routesToProcess.entrySet()) {
                int routeId = entry.getKey();
                int earliestBoardingStopId = entry.getValue();

                int numTripsInRoute = routesArr[routeId*4];
                int numStopsInRoute = routesArr[routeId*4 + 1];
                int stopsOffset = routesArr[routeId*4 + 2];
                int stopTimesOffset = routesArr[routeId*4 + 3];

                boolean foundBoardingStop = false;
                int relativeTripIndex = -1;

                for (int relativeStopIndex = 0; relativeStopIndex < numStopsInRoute ; relativeStopIndex++) {
                    int stopIdInRoute = routeStopsArr[stopsOffset + relativeStopIndex];
                    if (stopIdInRoute == earliestBoardingStopId) foundBoardingStop = true;

                    if (foundBoardingStop) {

                        // On a trip:
                        //          Go through all stops on this route after our boarding stop
                        //          Only care about stops that this trip gets to faster than we already did before
                        //          If it does, we record the new improved time and we mark that stop to check in the next round
                        // Target Pruning:
                        //          Additional optimization rule (arrivalTime < bestTimeAtDestination)
                        //          We can disregard looking at stops that we arrive at later than we can already get to our destination
                        //          No point looking at a stop that we get to at 8:30, if our best time at destination is already 8:00
                        // Recording history for later reconstruction:
                        //          For each stop we visit along this trip after boarding:
                        //              Record the stop we boarded this trip at
                        //              Record the route we took to get here

                        if (relativeTripIndex != -1) {
                            int arrivalTimeIndex = stopTimesOffset + (relativeTripIndex * numStopsInRoute * 2) + (relativeStopIndex * 2);
                            int arrivalTime = stopTimesArr[arrivalTimeIndex];

                            if (arrivalTime < bestArrivalTime[stopIdInRoute] && arrivalTime < bestTimeAtDestination) {
                                bestArrivalTime[stopIdInRoute] = arrivalTime;
                                arrivalTimesPerRound[(round * totalStops) + stopIdInRoute] = arrivalTime;
                                markedStops.add(stopIdInRoute);

                                priorStopPerRound[(round * totalStops) + stopIdInRoute] = earliestBoardingStopId;
                                routeTakenPerRound[(round * totalStops) + stopIdInRoute] = routeId;
                            }
                        }

                        // Boarding a trip:
                        //          Take the next earliest trip on this route from this stop (after our arrival time since can't move back in time)
                        //          If there's no next trip (e.g. end of the day) then we move onto the next route
                        // Local Pruning
                        //           Additional optimization rule
                        //           If on a trip, check if we arrived at this stop earlier in prev round than we left at in current round
                        //           If we did it might be the case that there's an ealier bus we can take instead of our current one
                        //           So we check for next earliest trip in this case too
                        //           E.g. We first board at Stop A at 8:00, next trip is at 8:05, we board it and go along to next stop, Stop B
                        //                we arrive at Stop B at 8:20 and see that it departs at 8:22.
                        //                We check and see that in our previous round we arrived at Stop B at 8:15 (doesn't matter how)
                        //                so instead of continuing on the 8:22 trip departing B, we take the earliest trip on this route
                        //                from this stop. Starting from 8:15 and take than instead (e.g. its possible theres one at 8:18)
                        //                This trip is our new trip that we will check all the stops after B along

                        int prevRoundArrivalTimeIndex = ((round - 1) * totalStops) + stopIdInRoute;
                        int prevRoundArrivalTime = arrivalTimesPerRound[prevRoundArrivalTimeIndex];
                        boolean canCatchEarlierBus = false;

                        if (relativeTripIndex != -1) {
                            int departureTimeIndex = stopTimesOffset + (relativeTripIndex * numStopsInRoute * 2) + (relativeStopIndex * 2) + 1;
                            int departureTime = stopTimesArr[departureTimeIndex];
                            if (prevRoundArrivalTime <= departureTime) {
                                canCatchEarlierBus = true;
                            }
                        }

                        if (relativeTripIndex == -1 || canCatchEarlierBus) {
                            if (prevRoundArrivalTime != Integer.MAX_VALUE) {
                                int newRelativeTripIndex = -1;

                                for (int tripIndex = 0; tripIndex < numTripsInRoute; tripIndex++) {
                                    int departureTimeIndex = stopTimesOffset + (tripIndex * numStopsInRoute * 2) + (relativeStopIndex * 2) + 1;
                                    int departureTime = stopTimesArr[departureTimeIndex];

                                    if (prevRoundArrivalTime <= departureTime) {
                                        newRelativeTripIndex = tripIndex;
                                        break;
                                    }
                                }

                                if (newRelativeTripIndex != -1) {
                                    relativeTripIndex = newRelativeTripIndex;
                                    earliestBoardingStopId = stopIdInRoute;
                                }
                            }
                        }
                    }
                }
            }


            // Stage 3: Footpaths
            //         For every stop we reached and marked to check in the next round, we look at all the stops we could walk to from there
            //         If we can walk to any stop faster than we can already get there, we update the timing for that stop and
            //         we mark that stop to be checked in the next round as well. We record that we took a walk as a -1 in routeTakenPerRound
            Set<Integer> stopsReachedByTransit = new HashSet<>(markedStops);
            for (Integer stopId : stopsReachedByTransit) {

                int arrivalTimeAtStopInCurrRound = arrivalTimesPerRound[(round * totalStops) + stopId];
                int transferOffsetIndexStart = stopsArr[(stopId * 2) + 1];
                int transferOffsetIndexEnd = stopsArr[(stopId + 1) * 2 + 1];

                for (int transferIndex = transferOffsetIndexStart; transferIndex < transferOffsetIndexEnd; transferIndex += 2) {
                    int targetStopId = transfersArr[transferIndex];
                    int walkTimeSeconds = transfersArr[transferIndex + 1];
                    int arrivalTimeAtTarget = arrivalTimeAtStopInCurrRound + walkTimeSeconds;

                    if (arrivalTimeAtTarget < bestArrivalTime[targetStopId] && arrivalTimeAtTarget < bestTimeAtDestination) {
                        arrivalTimesPerRound[(round * totalStops) + targetStopId] = arrivalTimeAtTarget;
                        bestArrivalTime[targetStopId] = arrivalTimeAtTarget;
                        markedStops.add(targetStopId);

                        priorStopPerRound[(round * totalStops) + targetStopId] = stopId;
                        routeTakenPerRound[(round * totalStops) + targetStopId] = -1;
                    }
                }
            }

            // Stage 4: Check if walking from here to our destination is faster than our best time already
            for (int stopId : markedStops) {
                if (walkTimeToDestination[stopId] != Integer.MAX_VALUE) {
                    int arrivalAtDestination = arrivalTimesPerRound[(round * totalStops) + stopId] + walkTimeToDestination[stopId];
                    if (arrivalAtDestination < bestTimeAtDestination) {
                        bestTimeAtDestination = arrivalAtDestination;
                        destinationBestArrivalTimePerRound[round] = arrivalAtDestination;
                        destinationPriorStopPerRound[round] = stopId;
                    }
                }
            }

            roundsCompleted++;

            // Tiny optimization, basically if we went through the whole network in under MAX_ROUNDS, then we can break out early
            if (markedStops.isEmpty()) {
                break;
            }
        }

        return reconstructJourney(latFrom, lonFrom, latTo, lonTo, startTimeSecondsAfterMidnight,
                arrivalTimesPerRound, priorStopPerRound, routeTakenPerRound,
                destinationBestArrivalTimePerRound, destinationPriorStopPerRound, totalStops, roundsCompleted);
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
                                               int[] destinationBestArrivalTimePerRound, int[] destinationPriorStopPerRound,
                                               int totalStops, int roundsCompleted) {

        // Stage 5: Reconstruction
        //          Identify best round, if never reached destination return empty list
        //          Reconstructing starting from the destination, make routeStep for ending walk
        //          Generate routeSteps for each trip inside (whether walking or transit)
        //          Make routeStep for initial walk (from source to first station)

        List<RouteStep> routeSteps = new ArrayList<>();
        int bestRound = -1;
        int bestTime = Integer.MAX_VALUE;

        for (int i = 0; i <= roundsCompleted; i++) {
            if (destinationBestArrivalTimePerRound[i] < bestTime) {
                bestTime = destinationBestArrivalTimePerRound[i];
                bestRound = i;
            }
        }

        if (bestRound == -1 || bestTime == Integer.MAX_VALUE) {
            return routeSteps;
        }

        int lastStopId = destinationPriorStopPerRound[bestRound];

        // This is the walking case (walking from source to destination is the fastest journey)
        if (lastStopId == -2) {
            int durationMinutes = (bestTime - startTimeSecondsAfterMidnight) / 60;
            routeSteps.add(new RouteStep(latTo, lonTo, durationMinutes, startTimeSecondsAfterMidnight, "destination"));
            return routeSteps;
        }

        // RouteStep for final walk
        int arrivalAtLastStop = arrivalTimesPerRound[(bestRound * totalStops) + lastStopId];
        int finalWalkDuration = (bestTime - arrivalAtLastStop) / 60;
        if (finalWalkDuration > 0) {
            routeSteps.add(new RouteStep(latTo, lonTo, finalWalkDuration, arrivalAtLastStop, "destination"));
        }

        int currentStopId = lastStopId;
        int currentRound = bestRound;

        while (currentStopId != -2 && currentRound > 0) {
            int parentIndex = (currentRound * totalStops) + currentStopId;
            int parentStopId = priorStopPerRound[parentIndex];
            int routeTakenId = routeTakenPerRound[parentIndex];

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

                step = new RouteStep(targetStop.lat, targetStop.lon, durationMinutes, startTime, targetStop.name);
            } else {
                int startTime = arrivalTimesPerRound[((currentRound - 1) * totalStops) + parentStopId];
                int endTime = arrivalTimesPerRound[parentIndex];
                int durationMinutes = (endTime - startTime) / 60;

                RaptorRoute routeInfo = raptorRouteLookup[routeTakenId];

                step = new RouteStep(
                        targetStop.lat, targetStop.lon, durationMinutes, startTime,
                        parentStop.name, targetStop.name, routeInfo.operator, routeInfo.shortName,
                        routeInfo.longName, routeInfo.headSign
                );
            }
            routeSteps.add(0, step);
            currentStopId = parentStopId;

            if (routeTakenId != -1) {
                currentRound--;
            }
        }

        // Make routeStep for initial walk
        if (currentStopId >= 0) {
            int initialWalkDuration = (arrivalTimesPerRound[currentStopId] - startTimeSecondsAfterMidnight) / 60;
            Stop firstStop = stopLookup[currentStopId];
            routeSteps.add(0, new RouteStep(firstStop.lat, firstStop.lon, initialWalkDuration, startTimeSecondsAfterMidnight, firstStop.name));
        }

        return routeSteps;
    }

}
