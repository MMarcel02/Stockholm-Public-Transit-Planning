package com.team18.routing.AStar;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

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

    public AStarRouter(GTFSParser parser) {
        this.parser = parser;
        this.graph = new TransitGraph();
        this.graph.build(parser);
    }

    @Override
    public List<RouteStep> getFastestTrip(double latFrom, double lonFrom, double latTo, double lonTo, int startTimeSecondsAfterMidnight){
        List<RouteNode> openList = new ArrayList<RouteNode>();
        List<RouteStep> closedList = new ArrayList<RouteStep>();

        Stop src = findNearestStop(latFrom, lonFrom);
        Stop dest = findNearestStop(latTo, lonTo);

        if(src == null || dest == null){
            System.err.println("Source or destination is null! Returning an empty list... ");
            return Collections.emptyList();
        }

        aStarRouteCalculator(openList, closedList, src, dest, startTimeSecondsAfterMidnight);

        return closedList;
    }

    public void aStarRouteCalculator(List<RouteNode> openList, List<RouteStep> closedList, Stop src, Stop dest, int startTimeSec){
        
        //Init the openList
        RouteNode node = new RouteNode(src, 0, heuristic(src, dest), startTimeSec, null, null);
        openList.add(node);

        while(!openList.isEmpty()){
            double fCompare = openList.get(0).f;
            int qIndex = 0;
            for(int i = 1; i < openList.size(); ++i){
                if(fCompare > openList.get(i).f){
                    fCompare = openList.get(i).f;
                    qIndex = i;
                }
            }

            RouteNode q = openList.get(qIndex); //q is our current stop
            
            List<Edge> successorList = graph.adjacency.get(q.stop.id); //use the id of q to get its adjacent stops from the graph

            double gOld = openList.get(qIndex).g; //gOld is the travel time up to the current stop
            for(int i = 0; i < successorList.size(); ++i){
                double gCurrent = gOld + successorList.get(i).travelTimeSeconds; //gCurrent is the travel time up to the current successor

                if(successorList.get(i).dest.lat == dest.lat && successorList.get(i).dest.lon == dest.lon){
                    break;
                }
                else{
                    double hCurrent = GeoCalculator.calculateHaversineDistance(successorList.get(i).dest.lat, successorList.get(i).dest.lon, dest.lat, dest.lon);
                    double fCurrent = gCurrent + hCurrent;
                    
                    if(!checkOpenList(openList, successorList, i, fCurrent) || !checkClosedList(closedList, successorList, i)){
                        RouteNode newNode = new RouteNode(successorList.get(i).dest, gCurrent, hCurrent, 0, openList.get(qIndex), successorList.get(i));
                        openList.add(newNode);
                    }
                }
            }

            // TODO: Line below looks a bit nutty, needs shortening.
            if(openList.get(qIndex).parent == null){

                RouteStep routeStep = new RouteStep(openList.get(qIndex).stop.lat, openList.get(qIndex).stop.lon, 0, startTimeSec, null, openList.get(qIndex).stop.name, successorList.get(0).trip.route.operator, successorList.get(0).trip.route.shortName, successorList.get(0).trip.route.longName, successorList.get(0).trip.headSign);
                closedList.add(routeStep);

            } else {

                RouteStep routeStep = new RouteStep(openList.get(qIndex).stop.lat, openList.get(qIndex).stop.lon, openList.get(qIndex).edgeFromParent.travelTimeSeconds, openList.get(qIndex).edgeFromParent.departureTime, openList.get(qIndex).stop.name, openList.get(qIndex).edgeFromParent.dest.name, openList.get(qIndex).edgeFromParent.trip.route.operator, openList.get(qIndex).edgeFromParent.trip.route.shortName, openList.get(qIndex).edgeFromParent.trip.route.longName, openList.get(qIndex).edgeFromParent.trip.headSign);
                closedList.add(routeStep);  
                          
            }
            
            openList.remove(qIndex);
            //the closed list will be our final route
        }
    }

    public static double heuristic(Stop from, Stop to) {
        return (double)GeoCalculator.calculateHaversineDistance(from.lat, from.lon, to.lat, to.lon);
    }
    
    public boolean checkOpenList(List<RouteNode> openList, List<Edge> list, int index, double fCurrent){
        int i = 0;
        boolean check = false;
        while(openList.isEmpty()){
            if(openList.get(i).stop.equals(list.get(index).dest) && openList.get(i).f <= fCurrent){
                check = true;
            }
        }
        return check;
    }

    public boolean checkClosedList(List<RouteStep> closedList, List<Edge> list, int index){
        int i = 0;
        boolean check = false;
        while(closedList.isEmpty()){
            if(closedList.get(i).latTo == (list.get(index).dest.lat) && closedList.get(i).lonTo == (list.get(index).dest.lon)){
                check = true;
            }
        }
        return check;
    }

    private Stop findNearestStop(double lat, double lon){
        Stop nearest = null;
        double best = Double.MAX_VALUE;

        for(Stop stop : parser.stops.values()){
            double dist = GeoCalculator.calculateEquirectangularDistance(lat, lon, stop.lat, stop.lon);
            if(dist < best){
                best = dist;
                nearest = stop;
            }
        }
        return nearest;
    }
        
}
