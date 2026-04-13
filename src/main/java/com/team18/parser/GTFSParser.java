package com.team18.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import com.team18.model.Route;
import com.team18.model.Stop;
import com.team18.model.StopTime;
import com.team18.model.Trip;

// Can check what GTFS data is required and formatting guidelines at link below
//https://resources.transitapp.com/article/458-guidelines-for-producing-gtfs-static-data-for-transit#agencytxt-DwlWP

public class GTFSParser {

    public Map<String, String> agencies = new HashMap<>();
    public Map<String, Stop> stops = new HashMap<>();
    public Map<String, Route> routes = new HashMap<>();
    public Map<String, Trip> trips = new HashMap<>();
    public Map<String, List<StopTime>> stopDepartures = new HashMap<>();

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

            }

            String[] requiredFiles = {"agency","stops","routes","trips","stop_times"};
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

        }
    }


    public void parseEntry(ZipFile zipFile, ZipEntry zipEntry, String type) throws IOException{
        System.out.println("Parsing: " + type);
        try (InputStream inputStream = zipFile.getInputStream(zipEntry)) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader reader = new BufferedReader(inputStreamReader);
            switch (type) {
                case "agency":
                    parseAgencies(reader);
                    break;
                case "stops":
                    parseStops(reader);
                    break;
                case "routes":
                    parseRoutes(reader);
                    break;
                case "trips":
                    parseTrips(reader);
                    break;
                case "stop_times":
                    parseStopTimes(reader);
                    break;
                default:
                    throw new IOException("Unknown file type: " + type);
            }
        } 
    }

    public void parseStops(BufferedReader reader) throws IOException {
        //First line has info about each column, the data inbetween cities is not always in the same columns
        String firstLine = reader.readLine();
        if (firstLine == null) return;
        String[] colNames = firstLine.split(",");
        int idIndex = -1, nameIndex = -1, latIndex = -1, lonIndex = -1;
        for (int i = 0; i < colNames.length; i++) {
            String col = colNames[i].trim();
            if (col.equals("stop_id")) idIndex = i;
            else if(col.equals("stop_name")) nameIndex = i;
            else if(col.equals("stop_lat")) latIndex = i;
            else if(col.equals("stop_lon")) lonIndex = i;
        }
        
        if (idIndex == -1 || nameIndex == -1 || latIndex == -1 || lonIndex == -1) {
            throw new IOException("Missing required columns in stops.txt");
        }

        String line;
        while ((line = reader.readLine()) != null) {
            // AI generated regex to make sure we dont split along , inside the name (if something like that exists)
            String[] lineSplit = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

            try {
                String id = lineSplit[idIndex].replace("\"", "").trim();
                String name = lineSplit[nameIndex].replace("\"", "").trim();
                String latString = lineSplit[latIndex].trim();
                String lonString = lineSplit[lonIndex].trim();

                if (id.isEmpty() || name.isEmpty() || latString.isEmpty() || lonString.isEmpty()) {
                    throw new IOException("Missing required data for a particular stop (id, name or coordinates): " + line);
                }

                double lat;
                double lon;

                try {
                    lat = Double.parseDouble(latString);
                    lon = Double.parseDouble(lonString);
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid coordinate format for stop: " + line);
                }

                if(lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    throw new IOException("Out of valid range for stop" + id);
                }

                stops.put(id, new Stop(id, name, lat, lon));

            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + line + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseAgencies(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null) return;
        String[] colNames = firstLine.split(",");
        int idIndex = -1, nameIndex = -1;
        for (int i = 0; i < colNames.length; i++) {
            String col = colNames[i].trim();
            if (col.equals("agency_id")) idIndex = i;
            else if(col.equals("agency_name")) nameIndex = i;
        }

        // agency_id is optional if only one agency according to GTFS
        if (nameIndex == -1) {
            throw new IOException("Missing required column agency_name in agency.txt");
        }

        String line;
        while ((line = reader.readLine()) != null) {
            String[] lineSplit = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

            try {
                String id;
                if (idIndex != -1) {
                    id = lineSplit[idIndex].replace("\"", "").trim();
                } else {
                    id = "default"; 
                }
                String name = lineSplit[nameIndex].replace("\"", "").trim();

                if (id.isEmpty() || name.isEmpty()) {
                    throw new IOException("Missing required data for a particular agency (id or name): " + line);
                }

                agencies.put(id, name);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + line + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseRoutes(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null) return;
        String[] colNames = firstLine.split(",");
        int idIndex = -1, agencyIndex = -1, shortNameIndex = -1, longNameIndex = -1;
        for (int i = 0; i < colNames.length; i++) {
            String col = colNames[i].trim();
            if (col.equals("route_id")) idIndex = i;
            else if(col.equals("agency_id")) agencyIndex = i;
            else if(col.equals("route_short_name")) shortNameIndex = i;
            else if(col.equals("route_long_name")) longNameIndex = i;
        }

        if (idIndex == -1) {
            throw new IOException("Missing required column route_id in routes.txt");
        }

        if (shortNameIndex == -1 && longNameIndex == -1) {
            throw new IOException("Missing both columns: route_short_name and route_long_name. Min. of 1 required");
        }

        String line;
        while ((line = reader.readLine()) != null) {
            String[] lineSplit = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

            try {
                String id = lineSplit[idIndex].replace("\"", "").trim();
                
                if (id.isEmpty()) {
                    throw new IOException("Missing required data for a particular route (id): " + line);
                }

                String operator;
                if (agencies.size() == 1) {
                    // In GTFS data, agency_id column is only needed if more than one agency
                    operator = agencies.values().iterator().next(); 
                } else if (agencyIndex != -1) {
                    String agencyID = lineSplit[agencyIndex].replace("\"", "").trim();
                    operator = agencies.get(agencyID);
                    if (operator == null) {
                        throw new IOException("Agency ID '" + agencyID + "' found in routes but not defined in agency.txt");
                    }
                } else {
                    throw new IOException("Multiple agencies exist in agency.txt but there is no column in routes.txt for agency_id");
                }

                // Conditional check in case one column is missing (only one is guaranteed in GTFS datasets)
                String shortName = (shortNameIndex != -1) ? lineSplit[shortNameIndex].replace("\"", "").trim() : "";
                String longName = (longNameIndex != -1) ? lineSplit[longNameIndex].replace("\"", "").trim() : "";

                routes.put(id, new Route(id, operator, shortName, longName));
                
            } catch (IOException e) {
                throw e; 
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + line + " | " + e.getMessage(), e);
            }
        }
    }

    public void parseTrips(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null) return;
        String[] colNames = firstLine.split(",");
        int idIndex = -1, serviceIdIndex = -1, routeIdIndex = -1;
        for (int i = 0; i < colNames.length; i++) {
            String col = colNames[i].trim();
            if (col.equals("trip_id")) idIndex = i;
            else if(col.equals("service_id")) serviceIdIndex = i;
            else if(col.equals("route_id")) routeIdIndex = i;
        }

        if (idIndex == -1 || serviceIdIndex == -1 || routeIdIndex == -1) {
            throw new IOException("Missing required columns in trips.txt");
        }

        String line;
        while ((line = reader.readLine()) != null) {
            String[] lineSplit = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

            try {
                String id = lineSplit[idIndex].replace("\"", "").trim();
                String serviceId = lineSplit[serviceIdIndex].replace("\"", "").trim();
                String routeId = lineSplit[routeIdIndex].replace("\"", "").trim();

                if (id.isEmpty() || serviceId.isEmpty() || routeId.isEmpty()) {
                    throw new IOException("Missing required data for a particular trip (trip_id, service_id, route_id): " + line);
                }

                Route route = routes.get(routeId);
                if (route == null) {
                    throw new IOException("RouteID not found in routes: " + routeId);
                }

                Trip newTrip = new Trip(id, route, serviceId);
                trips.put(id, newTrip);
                route.trips.add(newTrip);

            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error parsing line: " + line + " | " + e.getMessage(), e);
            }
        }
    }

    // TODO: implement an Interpolator since arrival_time and departure_time are not guaranteed for all GTFS data
    // TODO: implement different way of getting stopId if the GTFS data uses geojson and locations
    public void parseStopTimes(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null) return;
        String[] colNames = firstLine.split(",");
        int tripIdIndex = -1, arrTimeIndex = -1, depTimeIndex = -1, stopIdIndex = -1, stopSeqIndex = -1;
        for (int i = 0; i < colNames.length; i++) {
            String col = colNames[i].trim();
            if (col.equals("trip_id")) tripIdIndex = i;
            else if(col.equals("arrival_time")) arrTimeIndex = i;
            else if(col.equals("departure_time")) depTimeIndex = i;
            else if(col.equals("stop_id")) stopIdIndex = i;
            else if(col.equals("stop_sequence")) stopSeqIndex = i;
        }

        if (tripIdIndex == -1 || arrTimeIndex == -1 || depTimeIndex == -1 ||stopIdIndex == -1 || stopSeqIndex == -1) {
            throw new IOException("Missing required columns in stop_times.txt");
        }

        String line;
        while ((line = reader.readLine()) != null) {
            String[] lineSplit = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

            try {
                String tripId = lineSplit[tripIdIndex].replace("\"", "").trim();
                String stopId = lineSplit[stopIdIndex].replace("\"", "").trim();
                String stopSeqString = lineSplit[stopSeqIndex].replace("\"", "").trim();
                String arrivalTimeString = lineSplit[arrTimeIndex].replace("\"", "").trim();
                String departureTimeString = lineSplit[depTimeIndex].replace("\"", "").trim();

                if (tripId.isEmpty() || stopId.isEmpty() || stopSeqString.isEmpty() || (arrivalTimeString.isEmpty() && departureTimeString.isEmpty())) {
                    throw new IOException("Missing required data for a particular trip: " + line);
                }

                Trip trip = trips.get(tripId);
                Stop stop = stops.get(stopId);

                if (trip == null) throw new IOException("TripID not found in trips: " + tripId);
                if (stop == null) throw new IOException("StopID not found in trips: " + stopId);

                int arrTime = parseStopTime(arrivalTimeString);
                int depTime = parseStopTime(departureTimeString);

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
                throw new IOException("Error parsing line: " + line + " | " + e.getMessage(), e);
            }
        }

        //Populate stopDepartures, from that we can see the departures from the stop
        for(Trip trip : trips.values()){
            for(StopTime stopTime : trip.stopTimes){
                if(!stopDepartures.containsKey(stopTime.stop.id)){
                    stopDepartures.put(stopTime.stop.id, new ArrayList<>());
                }
                stopDepartures.get(stopTime.stop.id).add(stopTime);
            }
        }
    }

    public int parseStopTime(String timeString) throws IOException{
        if (timeString == null || timeString.isEmpty()) {
            return -1;
        }
        try {
            String[] timeSplit = timeString.split(":");
            int hours = Integer.parseInt(timeSplit[0]);
            int minutes = Integer.parseInt(timeSplit[1]);
            int seconds = Integer.parseInt(timeSplit[2]);
            int secondsAfterMidnight = hours*3600 + minutes*60 + seconds;
            return secondsAfterMidnight;
        } catch (NumberFormatException e) {
            throw new IOException("Time string failed parsing into number");
        }
    }
}
