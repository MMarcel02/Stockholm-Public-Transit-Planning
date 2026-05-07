package com.team18.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class RouteStep {
    public boolean walking;
    public double latTo;
    public double lonTo;
    public double durationMinutes;
    public int startTimeSecondsAfterMidnight;

    public String fromStopName;
    public String toStopName;
    public String operatorName;
    public String shortName;
    public String longName;
    public String headSign;

    // Walking constructor
    public RouteStep(double latTo, double lonTo, double durationMinutes, int startTimeSecondsAfterMidnight, String toStopName) {
        this.walking = true;
        this.latTo = latTo;
        this.lonTo = lonTo;
        this.durationMinutes = durationMinutes;
        this.startTimeSecondsAfterMidnight = startTimeSecondsAfterMidnight;
        this.toStopName = toStopName;
    }

    // Public transit constructor
    public RouteStep(double latTo, double lonTo, double durationMinutes, int startTimeSecondsAfterMidnight,
                     String fromStopName, String toStopName, String operatorName, String shortName, String longName, String headSign) {
        this.walking = false;
        this.latTo = latTo;
        this.lonTo = lonTo;
        this.durationMinutes = durationMinutes;
        this.startTimeSecondsAfterMidnight = startTimeSecondsAfterMidnight;
        this.fromStopName = fromStopName;
        this.toStopName = toStopName;
        this.operatorName = operatorName;
        this.shortName = shortName;
        this.longName = longName;
        this.headSign = headSign;
    }

    // Convert to JSON format in project manual
    public Map<String, Object> toMap() {
        Map<String, Object> stepMap = new LinkedHashMap<>();

        stepMap.put("mode", walking ? "walk" : "ride");

        Map<String, Object> point = new LinkedHashMap<>();
        point.put("lat", latTo);
        point.put("lon", lonTo);
        stepMap.put("to", point);

        stepMap.put("duration", durationMinutes);
        stepMap.put("startTime", formatTime(startTimeSecondsAfterMidnight));

        if (!walking) {
            stepMap.put("stop", fromStopName);

            Map<String, Object> routeInfo = new LinkedHashMap<>();
            routeInfo.put("operator", operatorName);
            routeInfo.put("shortName", shortName);
            routeInfo.put("longName", longName);
            routeInfo.put("headSign", headSign);

            stepMap.put("route", routeInfo);
        }

        return stepMap;
    }

    private String formatTime(int secondsAfterMidnight) {
        int hours = (secondsAfterMidnight / 3600) % 24;
        int minutes = (secondsAfterMidnight % 3600) / 60;
        return String.format("%02d:%02d", hours, minutes);
    }
}