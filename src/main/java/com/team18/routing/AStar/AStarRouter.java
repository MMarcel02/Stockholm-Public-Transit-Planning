package com.team18.routing.AStar;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.PriorityQueue;

import com.team18.model.Edge;
import com.team18.model.RouteNode;
import com.team18.model.RouteStep;
import com.team18.parser.GTFSParser;
import com.team18.routing.Router;
import com.team18.model.Stop;
import com.team18.util.GeoCalculator;

public class AStarRouter implements Router {
    private final GTFSParser parser;
    private final TransitGraph graph;

    private final double WALK_SPEED_MPS = 50.0 / 36.0;

    private final double MAX_VEHICLE_SPEED_MPS = 50.0;

    public AStarRouter(GTFSParser parser) {
        this.parser = parser;
        this.graph = new TransitGraph();
        this.graph.build(parser);
    }

    @Override
    public List<RouteStep> getFastestTrip(double latFrom, double lonFrom, double latTo, double lonTo, int startTimeSecondsAfterMidnight) {
        Stop src = findNearestStop(latFrom, lonFrom);
        Stop dest = findNearestStop(latTo, lonTo);

        if (src == null || dest == null) {
            System.err.println("Source or destination is null! Returning an empty list... ");
            return Collections.emptyList();
        }

        int directWalkSeconds = (int) Math.round(
                GeoCalculator.calculateEquirectangularDistance(latFrom, lonFrom, latTo, lonTo) / WALK_SPEED_MPS);
        int directWalkArrival = startTimeSecondsAfterMidnight + directWalkSeconds;

        RouteNode goal = runAStar(src, dest, latFrom, lonFrom, startTimeSecondsAfterMidnight);

        if (goal == null) {
            //walk directly
            return directWalk(latTo, lonTo, directWalkSeconds, startTimeSecondsAfterMidnight);
        }

        int finalWalkSeconds = (int) Math.round(
                GeoCalculator.calculateEquirectangularDistance(dest.lat, dest.lon, latTo, lonTo) / WALK_SPEED_MPS);
        int transitArrival = goal.arrivalTime + finalWalkSeconds;

        if (directWalkArrival <= transitArrival) {
            return directWalk(latTo, lonTo, directWalkSeconds, startTimeSecondsAfterMidnight);
        }

        return reconstructPath(goal, latTo, lonTo, startTimeSecondsAfterMidnight, finalWalkSeconds);
    }

    private RouteNode runAStar(Stop src, Stop dest, double originLat, double originLon, int startTimeSec) {
        PriorityQueue<RouteNode> open = new PriorityQueue<>(Comparator.comparingDouble(node -> node.f));

        Map<String, Integer> bestArrival = new HashMap<>();

        int initialWalkSeconds = (int) Math.round(
                GeoCalculator.calculateEquirectangularDistance(originLat, originLon, src.lat, src.lon) / WALK_SPEED_MPS);
        int srcArrival = startTimeSec + initialWalkSeconds;

        open.add(new RouteNode(src, initialWalkSeconds, heuristic(src, dest), srcArrival, null, null));
        bestArrival.put(src.id, srcArrival);

        while (!open.isEmpty()) {
            RouteNode current = open.poll();

            Integer settled = bestArrival.get(current.stop.id);
            if (settled != null && current.arrivalTime > settled) continue;

            if (current.stop.id.equals(dest.id)) return current;

            List<Edge> edges = graph.getAdjacency().get(current.stop.id);
            if (edges == null) continue; //a stop with no outgoing edges

            for (Edge edge : edges) {
                int neighbourArrival;

                if (edge.mode.equals("transit")) {
                    if (current.arrivalTime > edge.departureTime) continue;
                    neighbourArrival = edge.departureTime + edge.travelTimeSeconds;
                } else {
                    neighbourArrival = current.arrivalTime + edge.travelTimeSeconds;
                }

                Integer known = bestArrival.get(edge.dest.id);
                if (known != null && neighbourArrival >= known) continue;

                bestArrival.put(edge.dest.id, neighbourArrival);

                double g = neighbourArrival - startTimeSec;
                double h = heuristic(edge.dest, dest);
                open.add(new RouteNode(edge.dest, g, h, neighbourArrival, current, edge));
            }
        }

        return null;
    }

    private List<RouteStep> reconstructPath(RouteNode goal, double latTo, double lonTo,
                                            int startTimeSec, int finalWalkSeconds) {
        List<RouteNode> nodes = new ArrayList<>();
        for (RouteNode node = goal; node != null; node = node.parent) {
            nodes.add(node);
        }
        Collections.reverse(nodes);

        List<RouteStep> steps = new ArrayList<>();
        Stop srcStop = nodes.get(0).stop;

        int initialWalkSeconds = nodes.get(0).arrivalTime - startTimeSec;
        steps.add(new RouteStep(srcStop.lat, srcStop.lon, toMinutes(initialWalkSeconds), startTimeSec, srcStop.name));

        for (int i = 1; i < nodes.size(); i++) {
            RouteNode from = nodes.get(i - 1);
            RouteNode to = nodes.get(i);
            Edge edge = to.edgeFromParent;

            int legStart = from.arrivalTime;
            int legDuration = to.arrivalTime - from.arrivalTime;

            if (edge.mode.equals("transit")) {
                steps.add(new RouteStep(
                        to.stop.lat, to.stop.lon,
                        toMinutes(legDuration), legStart,
                        from.stop.name, to.stop.name,
                        edge.trip.route.operator, edge.trip.route.shortName, edge.trip.route.longName, edge.trip.headSign));
            } else {
                steps.add(new RouteStep(to.stop.lat, to.stop.lon, toMinutes(legDuration), legStart, to.stop.name));
            }
        }

        steps.add(new RouteStep(latTo, lonTo, toMinutes(finalWalkSeconds), goal.arrivalTime, null));

        return steps;
    }

    private List<RouteStep> directWalk(double latTo, double lonTo, int walkSeconds, int startTimeSec) {
        List<RouteStep> steps = new ArrayList<>();
        steps.add(new RouteStep(latTo, lonTo, toMinutes(walkSeconds), startTimeSec, null));
        return steps;
    }

    private double heuristic(Stop from, Stop to) {
        double distanceMeters = GeoCalculator.calculateEquirectangularDistance(from.lat, from.lon, to.lat, to.lon);
        return distanceMeters / MAX_VEHICLE_SPEED_MPS;
    }

    private int toMinutes(int seconds) {
        return (int) Math.round(seconds / 60.0);
    }

    private Stop findNearestStop(double lat, double lon) {
        Stop nearest = null;
        double best = Double.MAX_VALUE;

        for (Stop stop : parser.stops.values()) {
            double dist = GeoCalculator.calculateEquirectangularDistance(lat, lon, stop.lat, stop.lon);
            if (dist < best) {
                best = dist;
                nearest = stop;
            }
        }
        return nearest;
    }
}
