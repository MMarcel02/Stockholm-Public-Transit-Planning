package com.team18.routing.AStar;

import java.util.List;
import java.util.ArrayList;

import com.team18.model.Edge;
import com.team18.model.RouteNode;
import com.team18.parser.GTFSParser;
import com.team18.model.Stop;
import com.team18.util.GeoCalculator;

public class AStarRouter {

    public static List<RouteNode> openList = new ArrayList<RouteNode>();
    public static List<RouteNode> closedList = new ArrayList<RouteNode>();
    public static List<RouteNode> finalRoute = new ArrayList<RouteNode>();
    public static int openListIndex = 0;

    public static void aStarRouteCalculator(Stop source, Stop destination){
        TransitGraph graph = new TransitGraph();
        GTFSParser parser = new GTFSParser();
        graph.build(parser);
        //Initialising the open list
        RouteNode node = new RouteNode(source, 0, 0, 0, null, null);
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

            Stop q = openList.get(qIndex).stop; //q is our current stop
            List<Edge> successorList = graph.adjacency.get(q.id); //use the id of q to get its adjacent stops from the graph
            double gOld = openList.get(openListIndex).g;    //gOld is the travel time up to the current stop
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
                    RouteNode newNode = new RouteNode(successorList.get(i).dest, gCurrent, hCurrent, 0, openList.get(qIndex), null);
                    openList.add(newNode);
                     }
                }
            }
            closedList.add(openList.get(qIndex));
            openList.remove(qIndex);
            //the closed list will be our final route

        }
        
    }
    
    public static boolean checkOpenList(List<Edge> list, int index, double fCurrent){
        int i = 0;
        boolean check = false;
        while(openList.isEmpty()){
            if(openList.get(i).stop.equals(list.get(index).dest) && openList.get(i).f <= fCurrent){
                check = true;
            }
        }
        return check;
    }

    public static boolean checkClosedList(List<Edge> list, int index){
        int i = 0;
        boolean check = false;
        while(closedList.isEmpty()){
            if(closedList.get(i).stop.equals(list.get(i).dest)){
                check = true;
            }
        }
        return check;
    }
        
}
