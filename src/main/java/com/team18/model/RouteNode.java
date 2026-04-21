package com.team18.model;

public class RouteNode {
   public Stop stop;
   public double g; //travel time so far in minutes
   public double h; //heuristic estimate
   public double f;
   public Edge edgeFromParent; //so we know how we got here (either walk or transit)
   public int arrivalTime;
   public RouteNode parent; //Reference to the previous node


   public RouteNode(Stop stop, double g, double h, int arrivalTime, RouteNode parent, Edge edgeFromParent){
      this.stop = stop;
      this.g = g;
      this.h = h;
      this.f = g + h;
      this.arrivalTime = arrivalTime;
      this.parent = parent;
      this.edgeFromParent = edgeFromParent;
   }
}
