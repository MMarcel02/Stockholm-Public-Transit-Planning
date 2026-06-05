package com.team18.routing.AStar;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import com.team18.model.Edge;
import com.team18.parser.GTFSParser;
import com.team18.model.Stop;
import com.team18.model.Trip;
import com.team18.model.StopTime;
import com.team18.util.GeoCalculator;
import com.team18.model.Calendar;
import com.team18.model.CalendarDates;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class TransitGraph {
    private Map<String, List<Edge>> adjacency = new java.util.HashMap<>();

    private final double WALKING_SPEED = 83.33;

    private final double WALK_SPEED_MPS = 50.0 / 36.0;        //5 km/h in metres/second
    private final int MAX_WALK_TIME_SECONDS = 1800;          //30 minutes
    private final double MAX_WALK_DISTANCE = MAX_WALK_TIME_SECONDS * WALK_SPEED_MPS;

    public void build(GTFSParser parser){
        //Main method
        System.err.println("Building the graph... ");

        buildTransitEdges(parser);
        buildWalkingEdges(parser);
    }

    public void buildTransitEdges(GTFSParser parser){
        Map<String, CalendarDates> exceptionDates = parser.calendar_dates;
        Map<String, Calendar> calendarDates = parser.calendar;
        LocalDate currentDate = LocalDate.now();
        LocalDate dayToday = LocalDate.now();
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd");
        DateTimeFormatter getDayToday = DateTimeFormatter.ofPattern("E");
        String formattedDate = currentDate.format(dateFormat);
        String formattedDay = dayToday.format(getDayToday);
        
        for(Trip trip : parser.trips.values()){
            List<StopTime> stopTimes = new ArrayList<>(trip.stopTimes);

            for(int i = 0; i < stopTimes.size() - 1; ++i){
                
                StopTime current = stopTimes.get(i);
                StopTime next = stopTimes.get(i + 1);

                int travelTime = next.arrivalTime - current.departureTime;

                double walkingTime = (GeoCalculator.calculateEquirectangularDistance(current.stop.lat, current.stop.lon, next.stop.lat, next.stop.lon) / WALKING_SPEED) / 60.0;

                if (travelTime < 0) continue;

                int dayIndex = setDayIndex(formattedDay);
                
                int exceptionStartDate = Integer.parseInt(calendarDates.get(trip.serviceId).startDate);
                int exceptionEndDate = Integer.parseInt(calendarDates.get(trip.serviceId).endDate);
                String weekStates = calendarDates.get(trip.serviceId).week;
                if(exceptionStartDate <= Integer.parseInt(formattedDate) && exceptionEndDate >= Integer.parseInt((formattedDate))){

                    for(int j = exceptionStartDate; j <= exceptionEndDate; j++){


                        if(weekStates.charAt(dayIndex) == '0'){
                            if (exceptionDates.get(trip.serviceId).date == formattedDate && exceptionDates.get(trip.serviceId).exceptionType != "1"){
                                continue;
                            }
                        }

                        dayIndex++;
                        if(dayIndex > 6){
                            dayIndex = 0;
                        }

                    }
                }

                Edge transitEdge = new Edge(next.stop, "transit",  trip.id, current.departureTime, travelTime, walkingTime, trip);

                List<Edge> edges = adjacency.get(current.stop.id);
                if(edges == null){
                    edges = new ArrayList<>();

                    adjacency.put(current.stop.id, edges);
                }

                edges.add(transitEdge);
            }
        }
    }

    private void buildWalkingEdges(GTFSParser parser){
        List<Stop> stops = new ArrayList<>(parser.stops.values());

        for(int i = 0; i < stops.size(); ++i){
            Stop from = stops.get(i);

            for(int j = 0; j < stops.size(); ++j){
                if(i == j) continue;
                Stop to = stops.get(j);

                double distance = GeoCalculator.calculateEquirectangularDistance(from.lat, from.lon, to.lat, to.lon);
                if(distance > MAX_WALK_DISTANCE) continue;

                int walkSeconds = (int) Math.round(distance / WALK_SPEED_MPS);

                Edge walkingEdge = new Edge(to, "walking", null, -1, walkSeconds, walkSeconds, null);

                adjacency.computeIfAbsent(from.id, key -> new ArrayList<>()).add(walkingEdge);
            }
        }
    }

    // View-only on the adjacency Map, if needed
    public Map<String, List<Edge>> getAdjacency(){
        return Collections.unmodifiableMap(adjacency);
    }

    public int setDayIndex(String formattedDay){
        int dayIndex = -1;
        switch(formattedDay){
            case "Mon":{
                dayIndex = 0;
                break;
            }
            case "Tue":{
                dayIndex = 1;
                break;
            }
            case "Wed":{
                dayIndex = 2;
                break;
            }
            case "Thu":{
                dayIndex = 3;
                break;
            }
            case "Fri":{
                dayIndex = 4;
                break;
            }
            case "Sat":{
                dayIndex = 5;
                break;
            }
            case "Sun":{
                dayIndex = 6;
                break;
            }
            default:{
                dayIndex = -1;
                break;
            }
        }
        return dayIndex;
    }
}