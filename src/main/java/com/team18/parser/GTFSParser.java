package com.team18.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    public Map<String, CalendarDates> calendar_dates = new HashMap<>();
    public Map<String, List<ShapePoint>> shapes = new HashMap<>();

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
    }

    public void parseStops(CSVParser csvp) throws IOException {
        if (!csvp.hasCols("stop_id", "stop_name", "stop_lat", "stop_lon")) {
            throw new IOException("Missing required columns in stops.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String id = getCol(row, "stop_id");
                String name = getCol(row, "stop_name");
                String latString = getCol(row, "stop_lat");
                String lonString = getCol(row, "stop_lon");

                if (id.isEmpty() || name.isEmpty() || latString.isEmpty() || lonString.isEmpty()) {
                    throw new IOException("Missing required data for a particular stop (id, name or coordinates): " + rowValues(row));
                }

                double lat;
                double lon;
                try {
                    lat = Double.parseDouble(latString);
                    lon = Double.parseDouble(lonString);
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid coordinate format for stop: " + rowValues(row));
                }

                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    throw new IOException("Out of valid range for stop" + id);
                }

                stops.put(id, new Stop(id, name, lat, lon));
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + rowValues(row) + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseAgencies(CSVParser csvp) throws IOException {
        if (!hasCol(csvp, "agency_name")) {
            throw new IOException("Missing required column agency_name in agency.txt");
        }
        boolean hasAgencyId = hasCol(csvp, "agency_id");

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String id = hasAgencyId ? getCol(row, "agency_id") : "default";
                String name = getCol(row, "agency_name");

                if (id.isEmpty() || name.isEmpty()) {
                    throw new IOException("Missing required data for a particular agency (id or name): " + rowValues(row));
                }

                agencies.put(id, name);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + rowValues(row) + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseRoutes(CSVParser csvp) throws IOException {
        if (!hasCol(csvp, "route_id")) {
            throw new IOException("Missing required column route_id in routes.txt");
        }
        if (!hasCol(csvp, "route_short_name") && !hasCol(csvp, "route_long_name")) {
            throw new IOException("Missing both columns: route_short_name and route_long_name. Min. of 1 required");
        }   
        if(!hasCol(csvp, "route_type")){
            throw new IOException("Missing required column route_type in routes.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String id = getCol(row, "route_id");
                if (id.isEmpty()) {
                    throw new IOException("Missing required data for a particular route (id): " + rowValues(row));
                }

                String operator;
                if (agencies.size() == 1) {
                    operator = agencies.values().iterator().next();
                } else if (hasCol(csvp, "agency_id")) {
                    String agencyID = getCol(row, "agency_id");
                    operator = agencies.get(agencyID);
                    if (operator == null) {
                        throw new IOException("Agency ID '" + agencyID + "' found in routes but not defined in agency.txt");
                    }
                } else {
                    throw new IOException("Multiple agencies exist in agency.txt but there is no column in routes.txt for agency_id");
                }

                String shortName = hasCol(csvp, "route_short_name") ? getCol(row, "route_short_name") : "";
                String longName = hasCol(csvp, "route_long_name") ? getCol(row, "route_long_name") : "";

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
                throw new IOException("Error parsing line: " + rowValues(row) + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseTrips(CSVParser csvp) throws IOException {
        if (!csvp.hasCols("trip_id", "service_id", "route_id")) {
            throw new IOException("Missing required columns in trips.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String id = getCol(row, "trip_id");
                String serviceId = getCol(row, "service_id");
                String routeId = getCol(row, "route_id");
                String headSign = hasCol(csvp, "trip_headsign") ? getCol(row, "trip_headsign") : "";
                String shapeId = hasCol(csvp, "shape_id") ? getCol(row, "shape_id") : "";

                if (id.isEmpty() || serviceId.isEmpty() || routeId.isEmpty()) {
                    throw new IOException("Missing required data for a particular trip (trip_id, service_id, route_id): " + rowValues(row));
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
                throw new IOException("Error parsing line: " + rowValues(row) + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseShapes(CSVParser csvp) throws IOException {
        if (!csvp.hasCols("shape_id", "shape_pt_lat", "shape_pt_lon", "shape_pt_sequence")) {
            throw new IOException("Missing required columns in shapes.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String shapeId = getCol(row, "shape_id");
                String latStr = getCol(row, "shape_pt_lat");
                String lonStr = getCol(row, "shape_pt_lon");
                String seqStr = getCol(row, "shape_pt_sequence");
                String distStr = hasCol(csvp, "shape_dist_traveled") ? getCol(row, "shape_dist_traveled") : "";

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
                throw new IOException("Error parsing line: " + rowValues(row) + " | " + e.getMessage(), e);
            }
        }

        for (List<ShapePoint> pts : shapes.values()) {
            pts.sort((a, b) -> Integer.compare(a.sequence, b.sequence));
        }
    }

    // TODO: implement an Interpolator since arrival_time and departure_time are not guaranteed for all GTFS data
    // TODO: implement different way of getting stopId if the GTFS data uses geojson and locations
    public void parseStopTimes(CSVParser csvp) throws IOException {
        if (!csvp.hasCols("trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence")) {
            throw new IOException("Missing required columns in stop_times.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String tripId = getCol(row, "trip_id");
                String stopId = getCol(row, "stop_id");
                String stopSeqString = getCol(row, "stop_sequence");
                String arrivalTimeString = getCol(row, "arrival_time");
                String departureTimeString = getCol(row, "departure_time");

                if (tripId.isEmpty() || stopId.isEmpty() || stopSeqString.isEmpty() || (arrivalTimeString.isEmpty() && departureTimeString.isEmpty())) {
                    throw new IOException("Missing required data for a particular trip: " + rowValues(row));
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
                throw new IOException("Error parsing line: " + rowValues(row) + " | " + e.getMessage(), e);
            }
        }

        for (Trip trip : trips.values()) {
            trip.stopTimes.sort((st1, st2) -> Integer.compare(st1.stopSequence, st2.stopSequence));
        }
    }

    public void parseCalendar(CSVParser csvp) throws IOException {
        if (!csvp.hasCols("service_id", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "start_date", "end_date")) {
            throw new IOException("Missing required columns in calendar.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String id = getCol(row, "service_id");
                String monday = getCol(row, "monday");
                String tuesday = getCol(row, "tuesday");
                String wednesday = getCol(row, "wednesday");
                String thursday = getCol(row, "thursday");
                String friday = getCol(row, "friday");
                String saturday = getCol(row, "saturday");
                String sunday = getCol(row, "sunday");
                String week = monday.concat(tuesday).concat(wednesday).concat(thursday).concat(friday).concat(saturday).concat(sunday);
                String startDate = getCol(row, "start_date");
                String endDate = getCol(row, "end_date");

                if (id.isEmpty()) {
                    throw new IOException("Missing required service id for a particular period: " + rowValues(row));
                }

                if (week.length() < 7) {
                    throw new IOException("Missing required activity information for a particular period: " + rowValues(row));
                }

                if (startDate.isEmpty() || endDate.isEmpty()) {
                    throw new IOException("Incomplete period for a particular service: " + rowValues(row));
                }

                calendar.put(id, new Calendar(id, week, startDate, endDate));
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + rowValues(row) + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseCalendarDates(CSVParser csvp) throws IOException {
        if (!csvp.hasCols("service_id", "date", "exception_type")) {
            throw new IOException("Missing required columns in calendar_dates.txt");
        }

        Row row;
        while ((row = csvp.nextRow()) != null) {
            try {
                String id = getCol(row, "service_id");
                String date = getCol(row, "date");
                String exceptionType = getCol(row, "exception_type");

                if (id.isEmpty()) {
                    throw new IOException("Missing required service id for a particular date: " + rowValues(row));
                }
                if (date.isEmpty()) {
                    throw new IOException("Missing date for a particular service: " + rowValues(row));
                }
                if (exceptionType.isEmpty()) {
                    throw new IOException("Missing exception type for a particular service/date: " + rowValues(row));
                }

                calendar_dates.put(id, new CalendarDates(id, date, exceptionType));
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + rowValues(row) + " | " + e.getMessage(), e);
            }
        }
    }

    private boolean hasCol(CSVParser csvp, String name) {
        return Arrays.asList(csvp.colNames).contains(name);
    }

    private String getCol(Row row, String name) {
        int index = Arrays.asList(row.names).indexOf(name);
        if (index < 0 || index >= row.values.length) {
            return "";
        }
        return row.values[index].trim();
    }

    private String rowValues(Row row) {
        return Arrays.toString(row.values);
    }
}
