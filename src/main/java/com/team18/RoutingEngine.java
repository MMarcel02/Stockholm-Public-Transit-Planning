package com.team18;

import com.team18.model.RouteStep;
import com.team18.parser.GTFSParser;
import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.raptor.RaptorNetwork;
import com.team18.routing.Router;

// Needs to stay in this folder and with this title as defined in the project manual

import com.team18.util.GeoCalculator;
import com.team18.util.ParsingUtil;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.NoSuchFileException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.*;
import java.io.InputStream;

import com.leastfixedpoint.json.JSONReader;
import com.leastfixedpoint.json.JSONSyntaxError;
import com.leastfixedpoint.json.JSONWriter;

public class RoutingEngine {
    private RaptorNetwork raptorNetwork;

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
                    try {
                        GTFSParser parser = new GTFSParser();
                        parser.loadFromZip(zipFilePath);
                        RaptorBuilder builder = new RaptorBuilder();
                        this.raptorNetwork = builder.build(parser.agencies, parser.stops, parser.routes, parser.trips);
                        sendOk("loaded");
                        continue;
                    } catch (FileNotFoundException | NoSuchFileException e) {
                        sendError("Fatal: File doesn't exist at the path provided: " + zipFilePath);
                        System.exit(1);
                    } catch (ZipException e) {
                        sendError("Fatal: File exists but isn't a valid zip: " + zipFilePath);
                        System.exit(1);
                    } catch (IOException e) {
                        sendError("Fatal: Loading error: " + e.getMessage());
                        System.exit(1);
                    } 
                }

                // Crow flight calculator for now using both formulas to test for accuracy 
                // for accuracy for the speedup we can feed lots of routes and only look at the last few (as JVM needs to warm up)
                if (request.containsKey("routeFrom") && request.containsKey("to") && request.containsKey("startingAt")) {
                    if (this.raptorNetwork == null) {
                        sendError("Raptor network not loaded, check that 'load' request was sent earlier.");
                        continue;
                    }

                    try {
                        Map<?,?> fromNode = (Map<?,?>) request.get("routeFrom");                        
                        Map<?,?> toNode = (Map<?,?>) request.get("to");                        
                        String startTime = (String) request.get("startingAt"); 
                        int startTimeSecondsAfterMidnight = ParsingUtil.parseStopTime(startTime);

                        double latFrom = ((Number) fromNode.get("lat")).doubleValue();
                        double lonFrom = ((Number) fromNode.get("lon")).doubleValue();
                        double latTo = ((Number) toNode.get("lat")).doubleValue();
                        double lonTo = ((Number) toNode.get("lon")).doubleValue();
                        
                        Router raptor = new RaptorAlgorithm(raptorNetwork);
                        List<RouteStep> journey = raptor.getFastestTrip(latFrom, lonFrom, latTo, lonTo, startTimeSecondsAfterMidnight);
                        Object[] routeSteps = new Object[journey.size()];
                        for (int i = 0; i < routeSteps.length; i++) {
                            routeSteps[i] = journey.get(i).toMap();
                        }
                        
                        sendOk(routeSteps);

                        // for (RouteStep routeStep : journey) {
                        //     Map<String, Object> routeStep = new java.util.LinkedHashMap<>();
                        //     routeStep.put("mode", "walk");
                        //     routeStep.put("to", toNode);
                        //     routeStep.put("startTime", startTime);
    
                        //     boolean isDebug = request.containsKey("debug") && request.get("debug").equals("true");
                            
                        //     if (isDebug) {
                        //         // this is the mode in which we can compare different approaches
                        //         long startHaversine = System.nanoTime();
                        //         double distanceMetersHaversine = GeoCalculator.calculateHaversineDistance(latFrom, lonFrom, latTo, lonTo);
                        //         long endHaversine = System.nanoTime();
                        //         long timeHaversineNs = endHaversine - startHaversine;
        
                        //         long startEqui = System.nanoTime();
                        //         double distanceMetersEqui = GeoCalculator.calculateEquirectangularDistance(latFrom, lonFrom, latTo, lonTo);
                        //         long endEqui = System.nanoTime();
                        //         long timeEquiNs = endEqui - startEqui;
        
                        //         int walkMinutesHaversine = (int) Math.round(distanceMetersHaversine / WALKING_SPEED);
                                
                        //         double errorPercentage = Math.abs(distanceMetersHaversine - distanceMetersEqui) / distanceMetersHaversine * 100.0;
        
                        //         double speedMultiplier = 0;
                        //         if (timeEquiNs > 0) {
                        //             speedMultiplier = (double) timeHaversineNs / timeEquiNs;
                        //         }
                                
                        //         routeStep.put("duration", walkMinutesHaversine);
                        //         routeStep.put("DEBUG_error_percent", errorPercentage);
                        //         routeStep.put("DEBUG_speedup", speedMultiplier);
                        //     } else {
                        //         // this is the default we will use in production
                        //         double distanceMetersEqui = GeoCalculator.calculateEquirectangularDistance(latFrom, lonFrom, latTo, lonTo);
                        //         routeStep.put("duration", (int) Math.round(distanceMetersEqui / WALKING_SPEED));
                        //     }
                        // }


                    } catch (ClassCastException | NullPointerException e) {
                        sendError("Coordinates must be formatted as numbers");
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendError("Invalid route request format: " + e.getMessage());
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
