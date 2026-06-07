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

public class Optimizer {

    public RaptorNetwork network;
    public RaptorAlgorithm raptor;
    public double[] demandPointCoordinates;
    public int[][] demandMatrix;
    
    public int totalDemandPoints;
    public int totalStops;
    public int[] bestArrivalTimeForEachStop;
    public int[][] stopsReachableFromDemandPoint; 
    public int[][] walkTimeFromAllStopsToAllDemandPoints;

    public double baselineTransitOperationalCost = 0.0;
    
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
                    walkTimeFromAllStopsToAllDemandPoints[i][j] = (int) (distToDestination / Config.WALK_SPEED_MPS);
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
            baselineTransitOperationalCost += route.operatingCostSEK;
        }
    }

    // Constructor for multithreading so dont need to calc the demand point stops reachable and walktimes for each additional thread
    public Optimizer(RaptorNetwork localNetwork, double[] demandPointCoordinates, int[][] demandMatrix, 
                      int[][] stopsReachable, int[][] walkTimes, double baselineTransitOperationalCost) {
        
        this.network = localNetwork;
        this.raptor = new RaptorAlgorithm(localNetwork); 
        
        this.demandPointCoordinates = demandPointCoordinates;
        this.demandMatrix = demandMatrix;
        
        this.totalDemandPoints = demandMatrix.length;
        this.totalStops = localNetwork.stopLookup.length;
        
        this.stopsReachableFromDemandPoint = stopsReachable;
        this.walkTimeFromAllStopsToAllDemandPoints = walkTimes;

        this.baselineTransitOperationalCost = baselineTransitOperationalCost;
    }

    public void multiThreadedOptimize() {
        Set<String> diasbledParentRoutes = new HashSet<>();
                
        double baselinePassengerCost = calculateTimePeriodPassengerCost(network) * Config.PASSENGER_COST_WEIGHT;
        double baselineTotalCost = baselineTransitOperationalCost + baselinePassengerCost;

        System.err.println("Time period (days): " + Config.REFERENCE_PERIOD.length);
        System.err.println("Baseline Operation cost: " + baselineTransitOperationalCost);
        System.err.println("Baseline Passenger cost: " + (baselinePassengerCost));

        for (int j = 0; j < 3; j++) {

            double baselineTotalCostForThisRound = baselineTotalCost;

            Map.Entry<String, Double> bestRouteToDisable = network.parentRouteLookup.values().parallelStream()
                .filter(route -> !diasbledParentRoutes.contains(route.id)
                && route.routeType == Route.RouteType.BUS
                )
                .map(route -> {

                    RaptorNetwork localNetwork = network.copyForMultithreading();

                    Optimizer localOptimizer = new Optimizer(
                        localNetwork, 
                        this.demandPointCoordinates, 
                        this.demandMatrix,
                        this.stopsReachableFromDemandPoint,
                        this.walkTimeFromAllStopsToAllDemandPoints,
                        this.baselineTransitOperationalCost
                    );

                    localNetwork.disableParentRouteOptimizer(route.id);

                    double newWeeklyOperationalCost = this.baselineTransitOperationalCost - route.operatingCostSEK;
                    double newWeeklyPassengerCost = localOptimizer.calculateTimePeriodPassengerCost(localNetwork) * Config.PASSENGER_COST_WEIGHT;

                    double newTotalCost = newWeeklyOperationalCost + newWeeklyPassengerCost;
                    double costDifference = newTotalCost - baselineTotalCostForThisRound;

                    return Map.entry(route.id, costDifference);
                })   
                .min(Map.Entry.comparingByValue())
                .orElse(null);

            if (bestRouteToDisable != null) {
                String bestRouteToDisableId = bestRouteToDisable.getKey();
                diasbledParentRoutes.add(bestRouteToDisableId);
                network.disableParentRouteOptimizer(bestRouteToDisableId);
                
                Route disabledRoute = network.parentRouteLookup.get(bestRouteToDisableId);
                this.baselineTransitOperationalCost -= disabledRoute.operatingCostSEK;
                
                double costImpact = bestRouteToDisable.getValue();
                System.err.println("Money saved: " + costImpact);
                baselineTotalCost += costImpact;
            }
        }

        Map<String, Route> parentRouteLookup = network.parentRouteLookup;
        for (String routeId : diasbledParentRoutes) {
            Route route = parentRouteLookup.get(routeId);
            
            System.err.println("id: " + route.id + " shortname: " + route.shortName + " longname: " + route.longName + " operator: " + route.operator);
        }
    }

    public double calculateTimePeriodPassengerCost(RaptorNetwork localNetwork) {
        double totalWeeklyCost = 0.0;
        for (int i = 0; i < 1; i++) {
            localNetwork.setByCalendar(Config.REFERENCE_PERIOD[2]);
            totalWeeklyCost += calculateDailyPassengerCost();
        }
        return totalWeeklyCost;
    }

    public double calculateDailyPassengerCost() {
        double totalDailyCost = 0.0;

        for (int t = 0; t < Config.REFERENCE_TIMES.length; t++) {
            int startTime = Config.REFERENCE_TIMES[t];
            double weight = Config.REFERENCE_WEIGHTS[t];
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
                        cost = (distToDestination / Config.WALK_SPEED_MPS) * Config.VOT;
                        double carTimeSecs = distToDestination * Config.CAR_DISTANCE_MULTIPLIER / Config.CAR_SPEED_MPS;
                        double timeCost = carTimeSecs * Config.VOT;
                        double carCost = distToDestination * Config.CAR_COST_PER_METRE + Config.FLAT_CAR_PENALTY;
                        cost = timeCost + carCost;
                    }
    
                    totalDailyCost += cost * demandMatrix[i][j] * weight;                
                }
            }
        }
        return totalDailyCost;
    }
}
