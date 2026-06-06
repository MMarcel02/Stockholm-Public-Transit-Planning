package com.team18.optimizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.team18.model.Route;
import com.team18.model.Stop;
import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.routing.raptor.RaptorNetwork;
import com.team18.util.GeoCalculator;
import com.team18.util.ParsingUtil;
import com.team18.optimizer.Config;

public class Optimizer {

    public RaptorNetwork network;
    public RaptorAlgorithm raptor;
    public double[] demandPointCoordinates;
    public int[][] demandMatrix;
    
    public int startTime = ParsingUtil.timeStringToSecondsAfterMidnight("14:00"); // and this to make better optimizer, maybe with rraptor
    public int totalDemandPoints;
    public int totalStops;
    public int[] bestArrivalTimeForEachStop;
    public int[][] stopsReachableFromDemandPoint; 
    public int[][] walkTimeFromAllStopsToAllDemandPoints;

    public double baselineWeeklyTransitOperationalCost = 0.0;
    
    public Optimizer(RaptorNetwork network, double[] demandPointCoordinates, int[][] demandMatrix) {
        this.network = network;
        this.raptor = new RaptorAlgorithm(network);
        this.demandMatrix = demandMatrix;
        this.demandPointCoordinates = demandPointCoordinates;
        
        this.totalDemandPoints = demandMatrix.length;
        this.totalStops = network.stopLookup.length;
        this.walkTimeFromAllStopsToAllDemandPoints = new int[totalDemandPoints][totalStops];

        this.stopsReachableFromDemandPoint = new int[totalDemandPoints][];
        
        for (int i = 0; i < totalDemandPoints; i++) {
            ArrayList<Integer> tempReachable = new ArrayList<>();
            for (int j = 0; j < totalStops; j++) {
                Stop stop = network.stopLookup[j];
                
                double distToDestination = GeoCalculator.calculateEquirectangularDistance(stop.lat, stop.lon, demandPointCoordinates[i*2], demandPointCoordinates[i*2+1]);
                
                if (distToDestination <= Config.MAX_WALK_DISTANCE_INITIAL_AND_FINAL_METRES) {
                    walkTimeFromAllStopsToAllDemandPoints[i][j] = (int) Math.round(distToDestination / Config.WALK_SPEED_MPS);
                    tempReachable.add(j);
                }
            }

            int stopsReached = tempReachable.size();
            stopsReachableFromDemandPoint[i] = new int[stopsReached];
            for (int j = 0; j < stopsReached; j++) {
                stopsReachableFromDemandPoint[i][j] = tempReachable.get(j);
            }
        }

        for (Route route : network.parentRouteLookup.values()) {
            baselineWeeklyTransitOperationalCost += (5*route.weekdayOperatingCostSEK) + (2*route.weekendOperatingCostSEK);
        }
    }

    // Constructor for multithreading so dont need to calc the demand point stops reachable and walktimes for each additional thread
    public Optimizer(RaptorNetwork localNetwork, double[] demandPointCoordinates, int[][] demandMatrix, 
                      int[][] stopsReachable, int[][] walkTimes, double baselineWeeklyTransitOperationalCost) {
        
        this.network = localNetwork;
        this.raptor = new RaptorAlgorithm(localNetwork); 
        
        this.demandPointCoordinates = demandPointCoordinates;
        this.demandMatrix = demandMatrix;
        
        this.totalDemandPoints = demandMatrix.length;
        this.totalStops = localNetwork.stopLookup.length;
        
        this.stopsReachableFromDemandPoint = stopsReachable;
        this.walkTimeFromAllStopsToAllDemandPoints = walkTimes;

        this.baselineWeeklyTransitOperationalCost = baselineWeeklyTransitOperationalCost;
    }

    public void multiThreadedOptimize() {
        Set<String> diasbledParentRoutes = new HashSet<>();

        network.setByCalendar(Config.REF_WEEKDAY);
        double baseAvgWeekdayPassengerCost = calculateDailyPassengerCost();
                    
        network.setByCalendar(Config.REF_WEEKEND);
        double baseAvgWeekendPassengerCost = calculateDailyPassengerCost();
        double baselineWeeklyPassengerCost =  (baseAvgWeekdayPassengerCost * 5) + (baseAvgWeekendPassengerCost * 2);

        // If we value operational cost and passenger cost equally 
        double ratio = baselineWeeklyTransitOperationalCost / baselineWeeklyPassengerCost;

        double baselineTotalCost = baselineWeeklyTransitOperationalCost + ratio*baselineWeeklyPassengerCost;

        System.err.println("Baseline Operation cost: " + baselineWeeklyTransitOperationalCost);
        System.err.println("Baseline Weekly passenger cost: " + (ratio*baselineWeeklyPassengerCost));

        for (int j = 0; j < 3; j++) {

            double baselineTotalCostForThisRound = baselineTotalCost;

            Map.Entry<String, Double> bestRouteToDisable = network.parentRouteToRaptorRoutesMap.keySet().parallelStream()
                .filter(parentRotueId -> !diasbledParentRoutes.contains(parentRotueId))
                .map(parentRotueId -> {

                    RaptorNetwork localNetwork = network.copyForMultithreading();

                    Optimizer localOptimizer = new Optimizer(
                        localNetwork, 
                        this.demandPointCoordinates, 
                        this.demandMatrix,
                        this.stopsReachableFromDemandPoint,
                        this.walkTimeFromAllStopsToAllDemandPoints,
                        this.baselineWeeklyTransitOperationalCost
                    );

                    localNetwork.disableParentRouteOptimizer(parentRotueId);
                    Route route = localNetwork.parentRouteLookup.get(parentRotueId);
                    double weeklyRouteTransitOperationalCost = (5*route.weekdayOperatingCostSEK) + (2*route.weekendOperatingCostSEK);
                    double newWeeklyOperationalCost = localOptimizer.baselineWeeklyTransitOperationalCost - weeklyRouteTransitOperationalCost;

                    localNetwork.setByCalendar(Config.REF_WEEKDAY);
                    double avgWeekdayPassengerCost = localOptimizer.calculateDailyPassengerCost();
                    
                    localNetwork.setByCalendar(Config.REF_WEEKEND);
                    double avgWeekendPassengerCost = localOptimizer.calculateDailyPassengerCost();

                    double newWeeklyPassengerCost = (avgWeekdayPassengerCost * 5) + (avgWeekendPassengerCost * 2);

                    double newTotalCost = newWeeklyOperationalCost + ratio*newWeeklyPassengerCost;
                    double costDifference = newTotalCost - baselineTotalCostForThisRound;

                    return Map.entry(parentRotueId, costDifference);
                })   
                .min(Map.Entry.comparingByValue())
                .orElse(null);

            if (bestRouteToDisable != null) {
                String bestRouteToDisableId = bestRouteToDisable.getKey();
                diasbledParentRoutes.add(bestRouteToDisableId);
                network.disableParentRouteOptimizer(bestRouteToDisableId);
                
                Route disabledRoute = network.parentRouteLookup.get(bestRouteToDisableId);
                this.baselineWeeklyTransitOperationalCost -= (5 * disabledRoute.weekdayOperatingCostSEK) + (2 * disabledRoute.weekendOperatingCostSEK);
                
                double costImpact = bestRouteToDisable.getValue();
                System.err.println("Money saved weekly: " + costImpact);
                baselineTotalCost += costImpact;
            }
        }

        Map<String, Route> parentRouteLookup = network.parentRouteLookup;
        for (String routeId : diasbledParentRoutes) {
            Route route = parentRouteLookup.get(routeId);
            
            System.err.println("id: " + route.id + " shortname: " + route.shortName + " longname: " + route.longName + " operator: " + route.operator);
        }
    }

    public double calculateDailyPassengerCost() {
        double totalDailyCost = 0.0;

        for (int i = 0; i < totalDemandPoints; i++) {
            bestArrivalTimeForEachStop = raptor.getBestArrivalTimeToAllStops(demandPointCoordinates[i*2], demandPointCoordinates[i*2+1], startTime);

            for (int j = 0; j < totalDemandPoints; j++) {
                if (i == j || demandMatrix[i][j] == 0) {
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
                
                double distToDestination = GeoCalculator.calculateEquirectangularDistance(demandPointCoordinates[i*2], demandPointCoordinates[i*2+1], demandPointCoordinates[j*2], demandPointCoordinates[j*2+1]);
                double cost = 0;

                if (bestArrivalTimeAtDemandPoint != Integer.MAX_VALUE && (bestArrivalTimeAtDemandPoint - startTime) <= Config.MAX_TRANSIT_TIME_SECONDS) {
                    cost = (bestArrivalTimeAtDemandPoint - startTime) * Config.VOT;
                } else if (distToDestination < Config.MAX_WALK_DISTANCE_INITIAL_AND_FINAL_METRES) {
                    cost = (distToDestination / Config.WALK_SPEED_MPS) * Config.VOT;
                } else {
                        double carTime = distToDestination * Config.CAR_DISTANCE_MULTIPLIER / Config.CAR_SPEED_MPS;
                        double timeCost = carTime * Config.VOT;
                        double carCost = distToDestination * Config.CAR_COST_PER_METRE + Config.FLAT_CAR_PENALTY;
                        cost = timeCost + carCost;
                }

                totalDailyCost += cost * demandMatrix[i][j];                
            }
        }
        return totalDailyCost;
    }
}
