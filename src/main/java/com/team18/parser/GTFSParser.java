package com.team18.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.team18.model.Calendar;
import com.team18.model.CalendarDates;
import com.team18.model.Route;
import com.team18.model.Route.RouteType;
import com.team18.model.ShapePoint;
import com.team18.model.Stop;
import com.team18.model.StopTime;
import com.team18.model.Trip;
import com.team18.parser.CSVParser.Row;
import com.team18.util.ParsingUtil;

public class GTFSParser {

    public Map<String, String> agencies = new HashMap<>();
    public Map<String, Stop> stops = new HashMap<>();
    public Map<String, Route> routes = new HashMap<>();
    public Map<String, Trip> trips = new HashMap<>();
    public Map<String, Calendar> calendar = new HashMap<>();
    public Map<CalendarDates, String> calendar_dates = new HashMap<>();
    public Map<String, List<ShapePoint>> shapes = new HashMap<>();
    public Map<LocalDate, List<String>> serviceByCalendar = new HashMap<>();

    public void loadFromZip(String zipFilePath) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipFilePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            Map<String, ZipEntry> entryMap = new HashMap<>();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if (entry.getName().endsWith("agency.txt")) entryMap.put("agency", entry);
                if (entry.getName().endsWith("stops.txt")) entryMap.put("stops", entry);
                if (entry.getName().endsWith("routes.txt")) entryMap.put("routes", entry);
                if (entry.getName().endsWith("trips.txt")) entryMap.put("trips", entry);
                if (entry.getName().endsWith("stop_times.txt")) entryMap.put("stop_times", entry);
                if (entry.getName().endsWith("calendar.txt")) entryMap.put("calendar", entry);
                if (entry.getName().endsWith("calendar_dates.txt")) entryMap.put("calendar_dates", entry);
                if (entry.getName().endsWith("shapes.txt")) entryMap.put("shapes", entry);
            }

            String[] requiredFiles = {"agency", "stops", "routes", "trips", "stop_times"};
            for (String reqFile : requiredFiles) {
                if (!entryMap.containsKey(reqFile)) {
                    throw new IOException("Missing file: " + reqFile + ".txt");
                }
            }

            parseEntry(zipFile, entryMap.get("agency"), "agency");
            parseEntry(zipFile, entryMap.get("stops"), "stops");
            parseEntry(zipFile, entryMap.get("routes"), "routes");
            parseEntry(zipFile, entryMap.get("trips"), "trips");
            parseEntry(zipFile, entryMap.get("stop_times"), "stop_times");
            if (entryMap.containsKey("calendar")) {
                parseEntry(zipFile, entryMap.get("calendar"), "calendar");
            }
            if (entryMap.containsKey("calendar_dates")) {
                parseEntry(zipFile, entryMap.get("calendar_dates"), "calendar_dates");
            }
            if (entryMap.containsKey("shapes")) {
                parseEntry(zipFile, entryMap.get("shapes"), "shapes");
            }
        }
    }

    public void parseEntry(ZipFile zipFile, ZipEntry zipEntry, String type) throws IOException {
        System.err.println("Parsing: " + type);
        try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader reader = new BufferedReader(inputStreamReader);
            CSVParser csvp = new CSVParser(reader);

            switch (type) {
                case "agency":
                    parseAgencies(csvp);
                    break;
                case "stops":
                    parseStops(csvp);
                    break;
                case "routes":
                    parseRoutes(csvp);
                    break;
                case "trips":
                    parseTrips(csvp);
                    break;
                case "stop_times":
                    parseStopTimes(csvp);
                    break;
                case "calendar":
                    parseCalendar(csvp);
                    break;
                case "calendar_dates":
                    parseCalendarDates(csvp);
                    break;
                case "shapes":
                    parseShapes(csvp);
                    break;
                default:
                    throw new IOException("Unknown file type: " + type);
            }
        }

        parseServiceIDbyCalendar();
    }

    public void parseStops(CSVParser csvp) throws IOException {
        if (!csvp.hasAll("stop_id", "stop_name", "stop_lat", "stop_lon")) {
            throw new IOException("Missing required columns in stops.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String id = row.getCol("stop_id");
                String name = row.getCol("stop_name");
                String latString = row.getCol("stop_lat");
                String lonString = row.getCol("stop_lon");

                if (id.isEmpty() || name.isEmpty() || latString.isEmpty() || lonString.isEmpty()) {
                    throw new IOException("Missing required data for a particular stop (id, name or coordinates): " + row.toString());
                }

                double lat;
                double lon;
                try {
                    lat = Double.parseDouble(latString);
                    lon = Double.parseDouble(lonString);
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid coordinate format for stop: " + row.toString());
                }

                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    throw new IOException("Out of valid range for stop" + id);
                }

                stops.put(id, new Stop(id, name, lat, lon));
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + row.toString() + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseAgencies(CSVParser csvp) throws IOException {
        if (!csvp.hasAll("agency_name")) {
            throw new IOException("Missing required column agency_name in agency.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String id = row.getCol("agency_id", "default");
                String name = row.getCol("agency_name");

                if (id.isEmpty() || name.isEmpty()) {
                    throw new IOException("Missing required data for a particular agency (id or name): " + row.toString());
                }

                agencies.put(id, name);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + row.toString() + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseRoutes(CSVParser csvp) throws IOException {
        if (!csvp.hasAll("route_id")) {
            throw new IOException("Missing required column route_id in routes.txt");
        }
        if (!csvp.hasAny("route_short_name", "route_long_name")) {
            throw new IOException("Missing both columns: route_short_name and route_long_name. Min. of 1 required");
        }   
        if(!hasCol(csvp, "route_type")){
            throw new IOException("Missing required column route_type in routes.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String id = row.getCol("route_id");
                if (id.isEmpty()) {
                    throw new IOException("Missing required data for a particular route (id): " + row.toString());
                }

                String operator;
                if (agencies.size() == 1) {
                    operator = agencies.values().iterator().next();
                } else if (csvp.hasAll("agency_id")) {
                    String agencyID = row.getCol("agency_id");
                    operator = agencies.get(agencyID);
                    if (operator == null) {
                        throw new IOException("Agency ID '" + agencyID + "' found in routes but not defined in agency.txt");
                    }
                } else {
                    throw new IOException("Multiple agencies exist in agency.txt but there is no column in routes.txt for agency_id");
                }

                String shortName = row.getCol("route_short_name");
                String longName = row.getCol("route_long_name");

                String routeTypeString = getCol(row, "route_type");
                if (routeTypeString.isEmpty()) {
                    throw new IOException("Missing required route_type for a particular route (id): " + rowValues(row));
                }

                RouteType routeType;
                try {
                    routeType = RouteType.fromGtfsCode(Integer.parseInt(routeTypeString));
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid route_type format for route '" + id + "': " + routeTypeString);
                } catch (IllegalArgumentException e) {
                    throw new IOException(e.getMessage() + " (route '" + id + "')");
                }

                routes.put(id, new Route(id, operator, shortName, longName, routeType));
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + row.toString() + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseTrips(CSVParser csvp) throws IOException {
        if (!csvp.hasAll("trip_id", "service_id", "route_id")) {
            throw new IOException("Missing required columns in trips.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String id = row.getCol("trip_id");
                String serviceId = row.getCol("service_id");
                String routeId = row.getCol("route_id");
                String headSign = row.getCol("trip_headsign");
                String shapeId = row.getCol("shape_id");

                if (id.isEmpty() || serviceId.isEmpty() || routeId.isEmpty()) {
                    throw new IOException("Missing required data for a particular trip (trip_id, service_id, route_id): " + row.toString());
                }

                Route route = routes.get(routeId);
                if (route == null) {
                    throw new IOException("RouteID not found in routes: " + routeId);
                }

                Trip newTrip = new Trip(id, route, serviceId, headSign, shapeId.isEmpty() ? null : shapeId);
                trips.put(id, newTrip);
                route.trips.add(newTrip);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + row.toString() + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseShapes(CSVParser csvp) throws IOException {
        if (!csvp.hasAll("shape_id", "shape_pt_lat", "shape_pt_lon", "shape_pt_sequence")) {
            throw new IOException("Missing required columns in shapes.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String shapeId = row.getCol("shape_id");
                String latStr = row.getCol("shape_pt_lat");
                String lonStr = row.getCol("shape_pt_lon");
                String seqStr = row.getCol("shape_pt_sequence");
                String distStr = row.getCol("shape_dist_traveled");

                if (shapeId.isEmpty() || latStr.isEmpty() || lonStr.isEmpty() || seqStr.isEmpty()) {
                    continue;
                }

                double lat = Double.parseDouble(latStr);
                double lon = Double.parseDouble(lonStr);
                int seq = Integer.parseInt(seqStr);
                Double dist = null;
                if (!distStr.isEmpty()) {
                    try {
                        dist = Double.parseDouble(distStr);
                    } catch (NumberFormatException ignored) {
                        dist = null;
                    }
                }

                shapes.putIfAbsent(shapeId, new ArrayList<>());
                shapes.get(shapeId).add(new ShapePoint(lat, lon, seq, dist));
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + row.toString() + " | " + e.getMessage(), e);
            }
        }

        for (List<ShapePoint> pts : shapes.values()) {
            pts.sort((a, b) -> Integer.compare(a.sequence, b.sequence));
        }
    }

    // TODO: implement an Interpolator since arrival_time and departure_time are not guaranteed for all GTFS data
    // TODO: implement different way of getting stopId if the GTFS data uses geojson and locations
    public void parseStopTimes(CSVParser csvp) throws IOException {
        if (!csvp.hasAll("trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence")) {
            throw new IOException("Missing required columns in stop_times.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String tripId = row.getCol("trip_id");
                String stopId = row.getCol("stop_id");
                String stopSeqString = row.getCol("stop_sequence");
                String arrivalTimeString = row.getCol("arrival_time");
                String departureTimeString = row.getCol("departure_time");

                if (tripId.isEmpty() || stopId.isEmpty() || stopSeqString.isEmpty() || (arrivalTimeString.isEmpty() && departureTimeString.isEmpty())) {
                    throw new IOException("Missing required data for a particular trip: " + row.toString());
                }

                Trip trip = trips.get(tripId);
                Stop stop = stops.get(stopId);

                if (trip == null) throw new IOException("TripID not found in trips: " + tripId);
                if (stop == null) throw new IOException("StopID not found in trips: " + stopId);

                int arrTime = ParsingUtil.timeStringToSecondsAfterMidnight(arrivalTimeString);
                int depTime = ParsingUtil.timeStringToSecondsAfterMidnight(departureTimeString);

                if (depTime == -1 && arrTime != -1) {
                    depTime = arrTime;
                } else if (depTime != -1 && arrTime == -1) {
                    arrTime = depTime;
                }

                int stopSeq;
                try {
                    stopSeq = Integer.parseInt(stopSeqString);
                } catch (NumberFormatException e) {
                    throw new IOException("Stop sequence not a valid integer: " + stopSeqString);
                }

                StopTime newStopTime = new StopTime(stop, arrTime, depTime, stopSeq);
                trip.stopTimes.add(newStopTime);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + row.toString() + " | " + e.getMessage(), e);
            }
        }

        for (Trip trip : trips.values()) {
            trip.stopTimes.sort((st1, st2) -> Integer.compare(st1.stopSequence, st2.stopSequence));
        }
    }

    public void parseCalendar(CSVParser csvp) throws IOException {
        if (!csvp.hasAll("service_id", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "start_date", "end_date")) {
            throw new IOException("Missing required columns in calendar.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String id = row.getCol("service_id");
                String monday = row.getCol("monday");
                String tuesday = row.getCol("tuesday");
                String wednesday = row.getCol("wednesday");
                String thursday = row.getCol("thursday");
                String friday = row.getCol("friday");
                String saturday = row.getCol("saturday");
                String sunday = row.getCol("sunday");
                String startDate = row.getCol("start_date");
                String endDate = row.getCol("end_date");

                boolean[] week = new boolean[7];

                if(monday.equals("1")) week[0] = true;
                if(tuesday.equals("1")) week[0] = true;
                if(wednesday.equals("1")) week[0] = true;
                if(thursday.equals("1")) week[0] = true;
                if(friday.equals("1")) week[0] = true;
                if(saturday.equals("1")) week[0] = true;
                if(sunday.equals("1")) week[0] = true;

                if (id.isEmpty()) {
                    throw new IOException("Missing required service id for a particular period: " + row.toString());
                }

                if (startDate.isEmpty() || endDate.isEmpty()) {
                    throw new IOException("Incomplete period for a particular service: " + row.toString());
                }

                calendar.put(id, new Calendar(id, week, startDate, endDate));
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + row.toString() + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseCalendarDates(CSVParser csvp) throws IOException {
        if (!csvp.hasAll("service_id", "date", "exception_type")) {
            throw new IOException("Missing required columns in calendar_dates.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String id = row.getCol("service_id");
                String date = row.getCol("date");
                String exceptionType = row.getCol("exception_type");

                if (id.isEmpty()) {
                    throw new IOException("Missing required service id for a particular date: " + row.toString());
                }
                if (date.isEmpty()) {
                    throw new IOException("Missing date for a particular service: " + row.toString());
                }
                if (exceptionType.isEmpty()) {
                    throw new IOException("Missing exception type for a particular service/date: " + row.toString());
                }

                calendar_dates.put(new CalendarDates(id, parseDate(date)), exceptionType);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + row.toString() + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseServiceIDbyCalendar(){
        
        for(Calendar calendarDate : calendar.values()){
            LocalDate startDate = parseDate(calendarDate.startDate);
            LocalDate endDate = parseDate(calendarDate.endDate);

            for(LocalDate i = startDate; !i.isAfter(endDate); i = i.plusDays(1)){
                int dayIndex = i.getDayOfWeek().getValue()-1;
                boolean enabled = calendarDate.week[dayIndex];
                CalendarDates exceptionDates = new CalendarDates(calendarDate.id, i);
                String exceptionType = calendar_dates.get(exceptionDates);
                if(exceptionType != null) {
                    if(exceptionType.equals("1")){
                        enabled = true;
                    }
                    else if(exceptionType.equals("2")){
                        enabled = false;
                    }
                }
                List<String> ids = serviceByCalendar.get(i);
                if(ids == null){
                    ids = new ArrayList<>();
                }
                if(enabled){
                    ids.add(calendarDate.id);
                }
                serviceByCalendar.put(i, ids);
            }
        }

    }

    private LocalDate parseDate (String date){

        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyyMMdd");

        return LocalDate.parse(date, format);
    }

}
