package com.team18.model;

import java.util.List;
import java.util.ArrayList;

public class Route {
    public enum RouteType {
        TRAM,
        METRO,
        TRAIN,
        BUS,
        FERRY;

        public static RouteType fromGtfsCode(int code) {
            switch (code) {

                case 0: return TRAM;
                case 1: return METRO;
                case 2: return TRAIN;
                case 3: return BUS;
                case 4: return FERRY;
                default:

                    if (code >= 100 && code <= 199) return TRAIN;
                    if (code >= 200 && code <= 299) return BUS;
                    if (code >= 400 && code <= 402) return METRO;
                    if (code >= 700 && code <= 799) return BUS;
                    if (code == 800) return BUS;
                    if (code >= 900 && code <= 999) return TRAM;
                    if (code >= 1000 && code <= 1099) return FERRY;
                    if (code >= 1200 && code <= 1299) return FERRY;
                    throw new IllegalArgumentException("Unsupported GTFS route_type code: " + code);
            }
        }
    }

    public final String id;
    public final String operator;
    public final String shortName;
    public final String longName;
    public final RouteType routeType;

    public List<Trip> trips = new ArrayList<>();

    public Route(String id, String operator, String shortName, String longName, RouteType routeType) {
        this.id = id;
        this.operator = operator;
        this.shortName = shortName;
        this.longName = longName;
        this.routeType = routeType;
    }

    public VehicleData vehicleData() {
        return VehicleData.forType(routeType);
    }
}
