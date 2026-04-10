package com.team18.model;

import java.util.List;
import java.util.ArrayList;

public class Route {
    public final String id;
    public final String operator;  
    public final String shortName; 
    public final String longName;  
    
    public List<Trip> trips = new ArrayList<>();

    public Route(String id, String operator, String shortName, String longName) {
        this.id = id;
        this.operator = operator;
        this.shortName = shortName;
        this.longName = longName;
    }
}