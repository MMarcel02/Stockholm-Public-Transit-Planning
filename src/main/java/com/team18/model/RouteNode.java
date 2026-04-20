package com.team18.model;

//Represents one possible move from a stop
public class RouteNode {
   public Stop stop;
   public double g; //travel time so far in minutes
   public double h; //heuristic estimate
   public double f;
   RouteNode parent; //Reference to the previous node

   public RouteNode(Stop stop, double g, double h){
      this.stop = stop;
      this.g = g;
      this.h = h;
      this.f = g + h;
   }
}
