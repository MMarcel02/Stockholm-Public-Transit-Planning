package com.team18.routing.AStar;

import java.util.List;
import java.util.ArrayList;

import com.team18.model.Edge;
import com.team18.model.RouteNode;
import com.team18.model.RouteStep;
import com.team18.parser.GTFSParser;
import com.team18.model.Stop;
import com.team18.util.GeoCalculator;

public class AStarRouter {

    private List<RouteNode> openList = new ArrayList<RouteNode>();
    private List<RouteStep> closedList = new ArrayList<RouteStep>();
    private static int openListIndex = 0;

    public void aStarRouteCalculator(GTFSParser parser, Stop source, Stop destination, int startTimeSec){
        TransitGraph graph = new TransitGraph();

        graph.build(parser);

        //Initialising the open list
        RouteNode node = new RouteNode(source, 0, heuristic(source, destination), startTimeSec, null, null);
        openList.add(openListIndex, node);

        openListIndex++;
        
        while(!openList.isEmpty()){
            double fCompare = openList.get(0).f;
            int qIndex = 0;
            for(int i = 1; i < openList.size(); i++){   
                if(fCompare > openList.get(i).f){
                    fCompare = openList.get(i).f;
                    qIndex = i;
                }
            }

            RouteNode q = openList.get(qIndex); //q is our current stop

            List<Edge> successorList = graph.adjacency.get(q.stop.id); //use the id of q to get its adjacent stops from the graph

            double gOld = openList.get(qIndex).g;    //gOld is the travel time up to the current stop
            for(int i = 0; i < successorList.size(); i++){
                double gCurrent = gOld + successorList.get(i).travelTimeSeconds; //gCurrent is the travel time up to the current successor

                //if the successor is our destination, then the algorithm stops
                if(successorList.get(i).dest.lat == destination.lat && successorList.get(i).dest.lon == destination.lon){
                    break;
                }
                else{
                    double hCurrent = GeoCalculator.calculateHaversineDistance(successorList.get(i).dest.lat, successorList.get(i).dest.lon, destination.lat, destination.lon);
                    double fCurrent = gCurrent + hCurrent;

                    //if our current successor is already in the open list with a smaller f, or is in the closed list it will be skipped
                    if(!checkOpenList(successorList, i, fCurrent) || !checkClosedList(successorList, i)){
                        
                        RouteNode newNode = new RouteNode(successorList.get(i).dest, gCurrent, hCurrent, 0, openList.get(qIndex), successorList.get(i));
                        openList.add(newNode);
                    }
                }
            }

            // TODO: Line 66 looks a bit nutty, needs shortening.
            RouteStep routeStep = new RouteStep(openList.get(qIndex).stop.lat, openList.get(qIndex).stop.lon, openList.get(qIndex).edgeFromParent.travelTimeSeconds, openList.get(qIndex).edgeFromParent.departureTime, openList.get(qIndex).stop.name, openList.get(qIndex).edgeFromParent.trip.route.operator, openList.get(qIndex).edgeFromParent.trip.route.shortName, openList.get(qIndex).edgeFromParent.trip.route.longName, openList.get(qIndex).edgeFromParent.trip.headSign);
            closedList.add(routeStep);
            openList.remove(qIndex);
            //the closed list will be our final route

        }
        
    }

    public static double heuristic(Stop from, Stop to) {
        return (double)GeoCalculator.calculateHaversineDistance(from.lat, from.lon, to.lat, to.lon);
    }
    
    public boolean checkOpenList(List<Edge> list, int index, double fCurrent){
        int i = 0;
        boolean check = false;
        while(openList.isEmpty()){
            if(openList.get(i).stop.equals(list.get(index).dest) && openList.get(i).f <= fCurrent){
                check = true;
            }
        }
        return check;
    }

    public boolean checkClosedList(List<Edge> list, int index){
        int i = 0;
        boolean check = false;
        while(closedList.isEmpty()){
            if(closedList.get(i).latTo == (list.get(index).dest.lat) && closedList.get(i).lonTo == (list.get(index).dest.lon)){
                check = true;
            }
        }
        return check;
    }
        
}
