package com.team18.model;

import com.team18.util.ParsingUtil;
import com.team18.routing.raptor.RaptorRoute;
import java.util.LinkedHashMap;
import java.util.Map;

public class RouteStep {
    public final RouteStepType routeStepType;
    public final int waitTimeSecs;
    public final int tripTimeSecs;
    public final int startTimeSecondsAfterMidnight;
    
    public final Stop fromStop;
    public final Stop toStop;
    
    public final double latTo;
    public final double lonTo;

    public final double latFrom;
    public final double lonFrom;

    public final Route route;
    public final RaptorRoute raptorRoute;
    public final String shapeId; 
    public final String headSign; 
    

    // Walking constructor from Starting coordinates -> Initial Stop
    public RouteStep(double latFrom, double lonFrom, Stop toStop, int tripTimeSecs, int startTimeSecondsAfterMidnight) {
        this.routeStepType = RouteStepType.WALK_TO_STOP;
        this.waitTimeSecs = 0;
        this.tripTimeSecs = tripTimeSecs;
        this.startTimeSecondsAfterMidnight = startTimeSecondsAfterMidnight;
        
        this.fromStop = null;
        this.toStop = toStop;

        this.latFrom = latFrom;
        this.lonFrom = lonFrom;

        this.latTo = toStop.lat;
        this.lonTo = toStop.lon;
        
        this.route = null;
        this.raptorRoute = null;
        this.shapeId = null;
        this.headSign = null;
    }

    // Walking constructor from Stop to Stop 
    public RouteStep(Stop fromStop, Stop toStop, int tripTimeSecs, int startTimeSecondsAfterMidnight) {
        this.routeStepType = RouteStepType.TRANSFER;
        this.waitTimeSecs = 0;
        this.tripTimeSecs = tripTimeSecs;
        this.startTimeSecondsAfterMidnight = startTimeSecondsAfterMidnight;
        
        this.fromStop = fromStop;
        this.toStop = toStop;
        
        this.latFrom = fromStop.lat;
        this.lonFrom = fromStop.lon;
        
        this.latTo = toStop.lat;
        this.lonTo = toStop.lon;
        
        this.route = null;
        this.raptorRoute = null;
        this.shapeId = null;
        this.headSign = null;
    }

    // Walking constructor from Final Stop -> Destination coordinates
    public RouteStep(Stop fromStop, double latTo, double lonTo, int tripTimeSecs, int startTimeSecondsAfterMidnight) {
        this.routeStepType = RouteStepType.WALK_TO_DEST;
        this.waitTimeSecs = 0;
        this.tripTimeSecs = tripTimeSecs;
        this.startTimeSecondsAfterMidnight = startTimeSecondsAfterMidnight;
        
        this.fromStop = fromStop;
        this.toStop = null;
        
        this.latFrom = fromStop.lat;
        this.lonFrom = fromStop.lon;
        
        this.latTo = latTo;
        this.lonTo = lonTo;
        
        this.route = null;
        this.raptorRoute = null;
        this.shapeId = null;
        this.headSign = null;
    }

    // Walking constructor from Starting coordinates -> Destination coordinates (no transfer case)
    public RouteStep(double latFrom, double lonFrom, double latTo, double lonTo, int tripTimeSecs, int startTimeSecondsAfterMidnight) {
        this.routeStepType = RouteStepType.DIRECT_WALK;
        this.waitTimeSecs = 0;
        this.tripTimeSecs = tripTimeSecs;
        this.startTimeSecondsAfterMidnight = startTimeSecondsAfterMidnight;
        
        this.fromStop = null;
        this.toStop = null;

        this.latFrom = latFrom;
        this.lonFrom = lonFrom;
        
        this.latTo = latTo;
        this.lonTo = lonTo;
        
        this.route = null;
        this.raptorRoute = null;
        this.shapeId = null;
        this.headSign = null;
    }

    // Public transit constructor RAPTOR
    public RouteStep(Stop fromStop, Stop toStop, int waitTimeSecs, int tripTimeSecs, int startTimeSecondsAfterMidnight, RaptorRoute raptorRoute) {
        this.routeStepType = RouteStepType.TRANSIT;
        this.waitTimeSecs = waitTimeSecs;
        this.tripTimeSecs = tripTimeSecs;
        this.startTimeSecondsAfterMidnight = startTimeSecondsAfterMidnight;
        
        this.fromStop = fromStop;
        this.toStop = toStop;

        this.latFrom = fromStop.lat;
        this.lonFrom = fromStop.lon;

        this.latTo = toStop.lat;
        this.lonTo = toStop.lon;
        
        this.route = raptorRoute.parentRoute;
        this.raptorRoute = raptorRoute;
        this.shapeId = raptorRoute.shapeId;
        this.headSign = raptorRoute.headSign;
    }

    // Public transit constructor for A* (which has the underlying Route/Trip instead of a RaptorRoute)
    public RouteStep(Stop fromStop, Stop toStop, int tripTimeSecs, int startTimeSecondsAfterMidnight, Route route, String shapeId, String headSign) {
        this.routeStepType = RouteStepType.TRANSIT;
        this.waitTimeSecs = 0;
        this.tripTimeSecs = tripTimeSecs;
        this.startTimeSecondsAfterMidnight = startTimeSecondsAfterMidnight;

        this.fromStop = fromStop;
        this.toStop = toStop;

        this.latFrom = fromStop.lat;
        this.lonFrom = fromStop.lon;

        this.latTo = toStop.lat;
        this.lonTo = toStop.lon;

        this.route = route;
        this.raptorRoute = null;
        this.shapeId = shapeId;
        this.headSign = headSign;
    }

    // Convert to JSON format in project manual
    public Map<String, Object> toMap() {
        Map<String, Object> stepMap = new LinkedHashMap<>();

        stepMap.put("mode", routeStepType == RouteStepType.TRANSIT ? "ride" : "walk");

        Map<String, Object> point = new LinkedHashMap<>();
        point.put("lat", this.latTo);
        point.put("lon", this.lonTo);
        stepMap.put("to", point);

        double duration = (waitTimeSecs + tripTimeSecs) / 60.0;

        stepMap.put("duration", duration);
        stepMap.put("startTime", ParsingUtil.secondsAfterMidnightToTimeString(startTimeSecondsAfterMidnight));

        if (routeStepType == RouteStepType.TRANSIT) {
            stepMap.put("stop", fromStop.name);

            Map<String, Object> routeInfo = new LinkedHashMap<>();
            routeInfo.put("operator", route.operator); 
            routeInfo.put("shortName", route.shortName);
            routeInfo.put("longName", route.longName);
            routeInfo.put("headSign", headSign);

            stepMap.put("route", routeInfo);
        }

        return stepMap;
    }
}
