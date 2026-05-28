package com.team18.routing.AStar;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import com.team18.model.Edge;
import com.team18.parser.GTFSParser;
import com.team18.model.Trip;
import com.team18.model.StopTime;
import com.team18.util.GeoCalculator;

public class TransitGraph {
    private Map<String, List<Edge>> adjacency = new java.util.HashMap<>();

    private final double WALKING_SPEED = 83.33;

    public void build(GTFSParser parser){
        //Main method
        System.err.println("Building the graph... ");

        buildTransitEdges(parser);
        buildWalkingEdges(parser);
    }

    public void buildTransitEdges(GTFSParser parser){
        for(Trip trip : parser.trips.values()){
            List<StopTime> stopTimes = new ArrayList<>(trip.stopTimes);

            for(int i = 0; i < stopTimes.size() - 1; ++i){
                StopTime current = stopTimes.get(i);
                StopTime next = stopTimes.get(i + 1);

                //Get total travel time
                int travelTime = next.arrivalTime - current.departureTime;

                double walkingTime = (GeoCalculator.calculateEquirectangularDistance(current.stop.lat, current.stop.lon, next.stop.lat, next.stop.lon) / WALKING_SPEED) / 60.0;

                if (travelTime < 0) continue;

                Edge transitEdge = new Edge(next.stop, "transit",  trip.tripId, current.departureTime, travelTime, walkingTime, trip);

                List<Edge> edges = adjacency.get(current.stop.id);
                if(edges == null){
                    edges = new ArrayList<>();

                    adjacency.put(current.stop.id, edges);
                }

                edges.add(transitEdge);
            }
        }
    }

    private void buildWalkingEdges(GTFSParser parser){
        for(Trip trip : parser.trips.values()){
            List<StopTime> stopTimes = new ArrayList<>(trip.stopTimes);

            for(int i = 0; i < stopTimes.size(); ++i){
                StopTime current = stopTimes.get(i);
                StopTime next = stopTimes.get(i + 1);

                int travelTime = next.arrivalTime - current.departureTime;

                double walkingTime = (GeoCalculator.calculateEquirectangularDistance(current.stop.lat, current.stop.lon, next.stop.lat, next.stop.lon) / WALKING_SPEED) / 60.0;

                if(travelTime < 0) continue;

                Edge walkingEdge = new Edge(next.stop, "walking", trip.tripId, current.departureTime, travelTime, walkingTime, trip);

                List<Edge> edges = adjacency.get(current.stop.id);
                if(edges == null){
                    edges = new ArrayList<>();
                    adjacency.put(current.stop.id, edges);
                }

                edges.add(walkingEdge);
            }
        }
    }

    // View-only on the adjacency Map, if needed
    public Map<String, List<Edge>> getAdjacency(){
        return Collections.unmodifiableMap(adjacency);
    }
}