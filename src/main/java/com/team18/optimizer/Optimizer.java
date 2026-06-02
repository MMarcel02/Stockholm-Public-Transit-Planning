package com.team18.optimizer;

import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.routing.raptor.RaptorNetwork;
import com.team18.routing.raptor.RaptorRoute;
import com.team18.util.GeoCalculator;
import com.team18.util.ParsingUtil;
import com.team18.model.Stop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Optimizer {

    public RaptorNetwork network;
    public RaptorAlgorithm raptor;
    public double[] demandPointCoordinates;
    public int[][] demandMatrix;
    public int[][] tripDurationMatrix;
    
    public int MAX_WALK_DISTANCE = 1000; // should prolly standardize this into some global static class since used multiple places
    public double WALK_SPEED_MPS = 50.0 / 36.0; // same for this
    public int startTime = ParsingUtil.timeStringToSecondsAfterMidnight("8:00"); // and this to make better optimizer, maybe with rraptor
    
    public int totalDemandPoints;
    public int totalStops;
    public int[] bestArrivalTimeForEachStop;
    public int[][] stopsReachableFromDemandPoint; 
    public int[][] walkTimeFromAllStopsToAllDemandPoints;
    
    
    public Optimizer(RaptorNetwork network, double[] demandPointCoordinates, int[][] demandMatrix) {
        this.network = network;
        this.raptor = new RaptorAlgorithm(network);
        this.demandMatrix = demandMatrix;
        this.demandPointCoordinates = demandPointCoordinates;
        this.tripDurationMatrix = new int[demandMatrix.length][demandMatrix.length];
        
        this.totalDemandPoints = demandMatrix.length;
        this.totalStops = network.stopLookup.length;
        this.walkTimeFromAllStopsToAllDemandPoints = new int[totalDemandPoints][totalStops];

        // Populate walks only once 
        this.stopsReachableFromDemandPoint = new int[totalDemandPoints][];
        
        for (int i = 0; i < totalDemandPoints; i++) {
            ArrayList<Integer> tempReachable = new ArrayList<>();
            for (int j = 0; j < totalStops; j++) {
                Stop stop = network.stopLookup[j];
                
                double distToDestination = GeoCalculator.calculateEquirectangularDistance(stop.lat, stop.lon, demandPointCoordinates[i*2], demandPointCoordinates[i*2+1]);
                
                if (distToDestination <= MAX_WALK_DISTANCE) {
                    walkTimeFromAllStopsToAllDemandPoints[i][j] = (int) Math.round(distToDestination / WALK_SPEED_MPS);
                    tempReachable.add(j);
                }
            }

            int stopsReached = tempReachable.size();
            stopsReachableFromDemandPoint[i] = new int[stopsReached];
            for (int j = 0; j < stopsReached; j++) {
                stopsReachableFromDemandPoint[i][j] = tempReachable.get(j);
            }
        }

    }

    public void optimize() {
        Set<Integer> routesToDisable = new HashSet<>();
        
        for (int j = 0; j < 1; j++) {
            int bestRouteToDisableSoFar = -1;
            double bestAvgWithOneRouteDisabled = Integer.MAX_VALUE;

            // rework this to disable parent routes and not individual raptor routes

            for (int i = 0; i < network.routesEnabledArr.length; i++) {
                if (routesToDisable.contains(i)) continue;
                network.toggleRoute(i);
                double newAvg = calculateAvgTripDuration(); 
                if (newAvg < bestAvgWithOneRouteDisabled) {
                    bestAvgWithOneRouteDisabled = newAvg;
                    bestRouteToDisableSoFar = i;    
                }
                network.toggleRoute(i);
                System.out.println("inner loop" + i + "out of" + network.routesEnabledArr.length);
            }
            System.out.println("outer loop finished");
            routesToDisable.add(bestRouteToDisableSoFar);
            network.toggleRoute(bestRouteToDisableSoFar);
        }

        for (Integer route : routesToDisable) {
            RaptorRoute rRoute = network.raptorRouteLookup[route];
            System.out.println(rRoute.parentRoute.id + " " + rRoute.parentRoute.operator + " " + rRoute.parentRoute.shortName + " " + rRoute.parentRoute.longName);
        }
    }

    public double calculateAvgTripDuration() {
        long weightedTotalNetworkTime = 0;
        long totalTrips = 0;
                
        for (int i = 0; i < totalDemandPoints; i++) {

            bestArrivalTimeForEachStop = raptor.getBestArrivalTimeToAllStops(demandPointCoordinates[i*2], demandPointCoordinates[i*2+1], startTime);

            for (int j = 0; j < totalDemandPoints; j++) {
                if (i == j) {
                    tripDurationMatrix[i][j] = 0; // put 0 for now, this is for time to walk inside of a square  
                    continue;
                }

                int bestArrivalTimeAtDemandPoint = Integer.MAX_VALUE;
                int[] stopsReachable = stopsReachableFromDemandPoint[j];
                int[] walkTimes = walkTimeFromAllStopsToAllDemandPoints[j];
                
                for (int k = 0; k < stopsReachable.length; k++) {
                    int stopId = stopsReachable[k];
                    int arrivalTimeAtStop =  bestArrivalTimeForEachStop[stopId]; 
                    int walkTime = walkTimes[stopId];

                    if (arrivalTimeAtStop != Integer.MAX_VALUE && walkTime != Integer.MAX_VALUE) {
                        int arrivalTime = arrivalTimeAtStop + walkTime;
                        if (arrivalTime < bestArrivalTimeAtDemandPoint) {
                            bestArrivalTimeAtDemandPoint = arrivalTime;
                        }  
                    }
                }
                
                int tripDuration;
                if (bestArrivalTimeAtDemandPoint != Integer.MAX_VALUE) {
                    tripDuration = bestArrivalTimeAtDemandPoint - startTime;                 
                } else {
                    double distToDestination = GeoCalculator.calculateEquirectangularDistance(demandPointCoordinates[i*2], demandPointCoordinates[i*2+1], demandPointCoordinates[j*2], demandPointCoordinates[j*2+1]);
                    tripDuration = (int) (distToDestination / WALK_SPEED_MPS);
                }

                weightedTotalNetworkTime += (long) tripDuration * demandMatrix[i][j];
                totalTrips += demandMatrix[i][j];
            }
        }

        return (double) weightedTotalNetworkTime / totalTrips;
    }

    public void avgTimeToCalcAvgTripDuration() {
        long curr = System.currentTimeMillis();
        int amountToTime = 5;
        for (int i = 0; i < amountToTime; i++) {
            network.toggleRoute(i);    
            calculateAvgTripDuration();
        }
        long finish = System.currentTimeMillis();
        double avg = (finish - curr) / (double) amountToTime;

        System.out.println("Avg to calc whole network avg (ms): " + avg); // currently about 4500 ms 

        // int amountToTime = 100;
        // int startTime = ParsingUtil.timeStringToSecondsAfterMidnight("8:00");
        // long curr = System.currentTimeMillis();

        // for (int i = 0; i < amountToTime; i++) {
        //     int[] bestArrivalTimeForEachStop = raptor.getBestArrivalTimeToAllStops(demandPointCoordinates[i*2], demandPointCoordinates[i*2+1], startTime);
        // }
        // long finish = System.currentTimeMillis();
        // double avg = (finish - curr) / (double) amountToTime;

        // System.out.println("Avg to calc raptor part for each demand point (ms): " + avg); // currently about 0.4 ms
    }

}
