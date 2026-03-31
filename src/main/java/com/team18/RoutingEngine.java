package com.team18;

// Needs to stay in this folder and with this title as defined in the project manual

import com.team18.util.GeoCalculator;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.NoSuchFileException;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.*;
import java.io.InputStream;

import com.leastfixedpoint.json.JSONReader;
import com.leastfixedpoint.json.JSONSyntaxError;
import com.leastfixedpoint.json.JSONWriter;

public class RoutingEngine {
    private final double WALKING_SPEED = 83.33; // For walking speed of 5km/h but in metres/minute, since duration is in minutes

    private JSONReader requestReader = new JSONReader(new InputStreamReader(System.in));
    private JSONWriter<OutputStreamWriter> responseWriter = new JSONWriter<>(new OutputStreamWriter(System.out));
    public static void main(String[] args) throws IOException {
        new RoutingEngine().run();
    }

    public void run() throws IOException {
        System.err.println("Starting");
        while (true) {
            Object json;
            try {
                json = requestReader.read();
            } catch (JSONSyntaxError e) {
                sendError("Bad JSON input");
                break;
            } catch (EOFException e) {
                System.err.println("End of input detected");
                break;
            }

            if (json instanceof Map<?,?>) {
                Map<?,?> request = (Map<?,?>) json;

                // Just a sanity check to make sure routing engine is processing requests
                // should be removed in final version as spec only wants load and route requests
                if (request.containsKey("ping")) {
                    sendOk(Map.of("pong", request.get("ping")));
                    continue;
                }

                // Helpful resource
                //https://www.baeldung.com/java-read-zip-files
                if (request.containsKey("load")) {
                    String zipFilePath = (String) request.get("load");
                    try (ZipFile zipFile = new ZipFile(zipFilePath)) {
                        Enumeration<? extends ZipEntry> entries = zipFile.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry entry = entries.nextElement();

                            // the enumeration already goes through whats inside the directories
                            // so if we have a directory we can just skip it to avoid errors
                            if (entry.isDirectory()) continue;
                            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                                BufferedReader reader = new BufferedReader(inputStreamReader);

                                // Here we go line by line, we will implement our graph generation here by parsing the files
                                // The line counting is only to prove it works, we will get rid of this in the actual implementation
                                int lines = 0;
                                while (reader.readLine() != null) {
                                    lines++; 
                                }
                                System.err.println("Loaded: " + entry.getName() + " Lines: " + lines);
                            }
                        }
                        sendOk("loaded");
                        continue;
                    } catch (FileNotFoundException | NoSuchFileException e) {
                        sendError("File doesn't exist at the path provided: " + zipFilePath);
                        sendError("Terminating...");
                        break;
                    } catch (ZipException e) {
                        sendError("File exists but isn't a valid zip: " + zipFilePath);
                        sendError("Terminating...");
                        break;
                    } catch (IOException e) {
                        sendError("File exists and is a valid zip, something went wrong while reading: " + zipFilePath);
                        sendError("Terminating...");
                        break;
                    }
                }

                // Crow flight calculator for now using both formulas to test for accuracy 
                // for accuracy for the speedup we can feed lots of routes and only look at the last few (as JVM needs to warm up)
                if (request.containsKey("routeFrom") && request.containsKey("to") && request.containsKey("startingAt")) {
                    try {
                        Map<?,?> fromNode = (Map<?,?>) request.get("routeFrom");                        
                        Map<?,?> toNode = (Map<?,?>) request.get("to");                        
                        String startTime = (String) request.get("startingAt"); 

                        double latFrom = ((Number) fromNode.get("lat")).doubleValue();
                        double lonFrom = ((Number) fromNode.get("lon")).doubleValue();
                        double latTo = ((Number) toNode.get("lat")).doubleValue();
                        double lonTo = ((Number) toNode.get("lon")).doubleValue();

                        Map<String, Object> routeStep = new java.util.LinkedHashMap<>();
                        routeStep.put("mode", "walk");
                        routeStep.put("to", toNode);
                        routeStep.put("startTime", startTime);

                        boolean isDebug = request.containsKey("debug") && request.get("debug").equals("true");
                        
                        if (isDebug) {
                            // this is the mode in which we can compare different approaches
                            long startHaversine = System.nanoTime();
                            double distanceMetersHaversine = GeoCalculator.calculateHaversineDistance(latFrom, lonFrom, latTo, lonTo);
                            long endHaversine = System.nanoTime();
                            long timeHaversineNs = endHaversine - startHaversine;
    
                            long startEqui = System.nanoTime();
                            double distanceMetersEqui = GeoCalculator.calculateEquirectangularDistance(latFrom, lonFrom, latTo, lonTo);
                            long endEqui = System.nanoTime();
                            long timeEquiNs = endEqui - startEqui;
    
                            int walkMinutesHaversine = (int) Math.round(distanceMetersHaversine / WALKING_SPEED);
                            
                            double errorPercentage = Math.abs(distanceMetersHaversine - distanceMetersEqui) / distanceMetersHaversine * 100.0;
    
                            double speedMultiplier = 0;
                            if (timeEquiNs > 0) {
                                speedMultiplier = (double) timeHaversineNs / timeEquiNs;
                            }
                            
                            routeStep.put("duration", walkMinutesHaversine);
                            routeStep.put("DEBUG_error_percent", errorPercentage);
                            routeStep.put("DEBUG_speedup", speedMultiplier);
                        } else {
                            // this is the default we will use in production
                            double distanceMetersEqui = GeoCalculator.calculateEquirectangularDistance(latFrom, lonFrom, latTo, lonTo);
                            routeStep.put("distance", distanceMetersEqui);
                            routeStep.put("duration", (int) Math.round(distanceMetersEqui / WALKING_SPEED));
                        }

                        sendOk(new Object[]{ routeStep });

                    } catch (ClassCastException | NullPointerException e) {
                        sendError("Coordinates must be formatted as numbers");
                    } catch (Exception e) {
                        sendError("Invalid route request format");
                    }
                continue;
                }    
            }

            sendError("Bad request");
        }
    }

    private void sendOk(Object value) throws IOException {
        responseWriter.write(Map.of("ok", value));
        responseWriter.getWriter().write('\n');
        responseWriter.getWriter().flush();
    }

    private void sendError(String message) throws IOException {
        responseWriter.write(Map.of("error", message));
        responseWriter.getWriter().write('\n');
        responseWriter.getWriter().flush();
    }
}
