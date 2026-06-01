package com.team18.optimizer;

import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.raptor.RaptorNetwork;
import com.team18.routing.raptor.RaptorRoute;
import com.team18.util.GeoCalculator;
import com.team18.util.ParsingUtil;
import com.team18.model.RouteStep;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Optimizer {
    
    
    // Demand matrix like so demand[ij][ij]
    // hashmap of routes
    // 675 * 675 so about 450k pairs
    // method of  calculating avg trip duration 

    
    // Sample area of populations:
    //              [2, 2]
    //              [2, 3]
    
    // Sample area of coordinates paired with populations:
    //              [59,17,59,17.5,59,18,59,18.5]
    //
    
    // Use the gravity formula to populate the demand cells
    // For the attraction of square j we can probably also use the population of the square
    // Demand matrix[i][j]
    //                  To
    //              [2, 1, 1, 2]
    //    From      [2, 1, 1, 2]
    //              [2, 1, 1, 2]
    //              [2, 1, 1, 3]
    
    // Average trip duration matrix[i][j]:
    //                  To
    //              [2, 1, 1, 2]
    //    From      [2, 1, 1, 2]
    //              [2, 1, 1, 2]
    //              [2, 1, 1, 3]
    
    // Naive solution is to call raptor.getFastest

    RaptorNetwork network;
    RaptorAlgorithm raptor;
    int numOfZones;
    double[] zoneCoordinates;
    int[][] demandMatrix;
    int[][] secondsDurationMatrix;
    
    public Optimizer(RaptorNetwork network, int[][] demandMatrix) {
        this.network = network;
        this.raptor = new RaptorAlgorithm(network);
        this.demandMatrix = demandMatrix;
        this.numOfZones = demandMatrix.length; 
        this.zoneCoordinates = new double[numOfZones * 2];
        this.secondsDurationMatrix = new int[numOfZones][numOfZones];
    }

    public void optimize() {
        // double lowerBound = calculateAvgTripDuration();
        Set<Integer> routesToDisable = new HashSet<>();
        
        for (int j = 0; j < 3; j++) {
            int bestRouteToDisableSoFar = -1;
            double bestAvgWithOneRouteDisabled = Integer.MAX_VALUE;
            for (int i = 0; i < network.routesEnabledArr.length; i++) {
                if (routesToDisable.contains(i)) continue;
                network.toggleRoute(i);
                double newAvg = calculateAvgTripDuration(); 
                if (newAvg < bestAvgWithOneRouteDisabled) {
                    bestAvgWithOneRouteDisabled = newAvg;
                    bestRouteToDisableSoFar = i;    
                }
                network.toggleRoute(i);
            }
            routesToDisable.add(bestRouteToDisableSoFar);
            network.toggleRoute(bestRouteToDisableSoFar);
        }

        for (Integer route : routesToDisable) {
            RaptorRoute rRoute = network.raptorRouteLookup[route];
            System.out.println(rRoute.parentRoute.id + " " + rRoute.parentRoute.operator + " " + rRoute.parentRoute.shortName + " " + rRoute.parentRoute.longName);
        }
    }

    public double calculateAvgTripDuration() {
        int startTime = ParsingUtil.timeStringToSecondsAfterMidnight("8:00");

        for (int i = 0; i < secondsDurationMatrix.length; i++) {
            for (int j = 0; j < secondsDurationMatrix.length; j++) {
                
                if (i == j) continue;

                double latStart = zoneCoordinates[i*2];
                double lonStart = zoneCoordinates[i*2 +1];

                double latEnd = zoneCoordinates[j*2];
                double lonEnd = zoneCoordinates[j*2 +1];

                List<RouteStep> steps = raptor.getFastestTrip(latStart, lonStart, latEnd, lonEnd, startTime);
                if (steps.isEmpty()) {
                    double distance = GeoCalculator.calculateEquirectangularDistance(latStart, lonStart, latEnd, lonEnd);
                    int timeToWalk = (int) Math.round(distance * (3.6/5.0)); // inverted walking speed of 5km/h
                    secondsDurationMatrix[i][j] = timeToWalk; 
                } else {
                    int arrivalTime = steps.getLast().startTimeSecondsAfterMidnight + (int) Math.round(steps.getLast().durationMinutes*60);
                    secondsDurationMatrix[i][j] = arrivalTime - startTime;
                }
            }
        }

        long weightedTotalNetworkTime = 0;
        long totalTrips = 0;
        for (int i = 0; i < secondsDurationMatrix.length; i++) {
            for (int j = 0; j < secondsDurationMatrix.length; j++) {
                weightedTotalNetworkTime += secondsDurationMatrix[i][j] * demandMatrix[i][j];
                totalTrips += demandMatrix[i][j];
            }   
        } 

        double avgTripDuration = (double) weightedTotalNetworkTime / totalTrips;
        return avgTripDuration;
    }

}
