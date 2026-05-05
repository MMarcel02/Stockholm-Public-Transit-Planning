package com.team18.routing.AStar;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import com.team18.model.Edge;
import com.team18.parser.GTFSParser;
import com.team18.model.Trip;
import com.team18.model.StopTime;
import com.team18.util.GeoCalculator;

public class TransitGraph {
    Map<String, List<Edge>> adjacency = new java.util.HashMap<>();

    private final double WALKING_SPEED = 83.33;

    public void build(GTFSParser parser){
        //Main method
        System.err.println("Building the graph... ");

        buildTransitEdges(parser);
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

                Edge edge = new Edge(next.stop, "transit",  trip.tripId, current.departureTime, travelTime, walkingTime, trip.route.operator, trip.headSign, trip.route.shortName, trip.route.longName);

                List<Edge> edges = new ArrayList<Edge>();
                edges.add(edge);

                adjacency.put(current.stop.id, edges);
            }
        }
    }

    private void buildWalkingEdges(GTFSParser parser){

    }

}