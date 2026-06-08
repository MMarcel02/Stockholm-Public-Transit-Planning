package com.team18.optimizer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.team18.model.Route;
import com.team18.model.Stop;
import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.routing.raptor.RaptorNetwork;
import com.team18.util.GeoCalculator;

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

    public Map<String, Double> multiThreadedOptimize() {
        Map<String, Double> diasbledParentRoutes = new HashMap<>();
                
        network.setByCalendar(Config.REPRESENTATIVE_WEEKDAY);
        double baselinePassengerCost = calculateDailyPassengerCost() * Config.PASSENGER_COST_WEIGHT;
        double baselineTotalCost = baselineTransitOperationalCost + baselinePassengerCost;

        System.err.println("Baseline Operation cost: " + baselineTransitOperationalCost);
        System.err.println("Baseline Passenger cost: " + (baselinePassengerCost));

        for (int j = 0; j < 3; j++) {

            double baselineTotalCostForThisRound = baselineTotalCost;

            Map.Entry<String, Double> bestRouteToDisable = network.parentRouteLookup.values().parallelStream()
                .filter(route -> !diasbledParentRoutes.keySet().contains(route.id)
                && route.routeType == Route.RouteType.BUS
                && !Config.CRITICAL_BUS_ROUTES.contains(route.shortName)
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

                    double newDailyOperationalCost = this.baselineTransitOperationalCost - route.operatingCostSEK;

                    localNetwork.setByCalendar(Config.REPRESENTATIVE_WEEKDAY);
                    double newDailyPassengerCost = localOptimizer.calculateDailyPassengerCost() * Config.PASSENGER_COST_WEIGHT;

                    double newTotalCost = newDailyOperationalCost + newDailyPassengerCost;
                    double costDifference = newTotalCost - baselineTotalCostForThisRound;

                    return Map.entry(route.id, costDifference);
                })   
                .min(Map.Entry.comparingByValue())
                .orElse(null);

            if (bestRouteToDisable != null) {
                String bestRouteToDisableId = bestRouteToDisable.getKey();
                diasbledParentRoutes.put(bestRouteToDisableId, bestRouteToDisable.getValue());
                network.disableParentRouteOptimizer(bestRouteToDisableId);
                
                Route disabledRoute = network.parentRouteLookup.get(bestRouteToDisableId);
                this.baselineTransitOperationalCost -= disabledRoute.operatingCostSEK;
                
                double costImpact = bestRouteToDisable.getValue();
                System.err.println(disabledRoute.routeType + " id: " + disabledRoute.id + " shortname: " + disabledRoute.shortName + " longname: " + disabledRoute.longName + " operator: " + disabledRoute.operator);
                System.err.println("Money difference: " + costImpact);
                baselineTotalCost += costImpact;
            }
        }
        return diasbledParentRoutes;
    }

    // Can call this in the GUI to get value for each route
    // should probably use the representative day in the gui but could also technically use any 
    public Map<String, Double> getRouteRemovedToCostImpact (LocalDate onDate) {
        network.setByCalendar(onDate);
        double baselinePassengerCost = calculateDailyPassengerCost() * Config.PASSENGER_COST_WEIGHT;
        double baselineTotalCost = baselineTransitOperationalCost + baselinePassengerCost;

        System.err.println("Baseline Operation cost: " + baselineTransitOperationalCost);
        System.err.println("Baseline Passenger cost: " + (baselinePassengerCost));

        return network.parentRouteLookup.values().parallelStream()
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

                double newDailyOperationalCost = this.baselineTransitOperationalCost - route.operatingCostSEK;

                localNetwork.setByCalendar(onDate);
                double newDailyPassengerCost = localOptimizer.calculateDailyPassengerCost() * Config.PASSENGER_COST_WEIGHT;

                double newTotalCost = newDailyOperationalCost + newDailyPassengerCost;
                double costDifference = newTotalCost - baselineTotalCost;

                return Map.entry(route.id, costDifference);
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // Old, still leaving it here for now in case we use more than one day for evaluation
    public double calculateTimePeriodPassengerCost(RaptorNetwork localNetwork) {
        double totalPeriodCost = 0.0;
        for (int i = 0; i < Config.REFERENCE_PERIOD.length; i++) { // 
            localNetwork.setByCalendar(Config.REFERENCE_PERIOD[i]);
            totalPeriodCost += calculateDailyPassengerCost();
        }
        return totalPeriodCost;
    }

    public double calculateDailyPassengerCost() {
        double totalDailyCost = 0.0;

        for (int t = 0; t < Config.REFERENCE_TIMES.length; t++) {
            int startTime = Config.REFERENCE_TIMES[t];
            double weight = Config.REFERENCE_WEIGHTS[t];
            for (int i = 0; i < totalDemandPoints; i++) {
                bestArrivalTimeForEachStop = raptor.getBestArrivalTimeToAllStops(demandPointCoordinates[i*2], demandPointCoordinates[i*2+1], startTime);
    
                for (int j = 0; j < totalDemandPoints; j++) {
                    if (i == j || demandMatrix[i][j] < 50) {
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
                    double cost;
    
                    if (bestArrivalTimeAtDemandPoint != Integer.MAX_VALUE && (bestArrivalTimeAtDemandPoint - startTime) <= Config.MAX_TRANSIT_TIME_SECONDS) {
                        cost = (bestArrivalTimeAtDemandPoint - startTime) * Config.VOT_TRANSIT;
                    } else if (distToDestination < Config.MAX_WALK_DISTANCE_INITIAL_AND_FINAL_METRES) {
                        cost = (distToDestination * Config.WALK_DISTANCE_MULTIPLIER / Config.WALK_SPEED_MPS) * Config.VOT_WALKING;
                    } else {
                        double carTimeSecs = distToDestination * Config.CAR_DISTANCE_MULTIPLIER / Config.CAR_SPEED_MPS;
                        double timeCost = carTimeSecs * Config.VOT_CAR;
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
