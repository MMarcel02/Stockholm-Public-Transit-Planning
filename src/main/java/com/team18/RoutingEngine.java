package com.team18;

// Needs to stay in this folder and with this title as defined in the project manual

import com.team18.util.GeoCalculator;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Map;

import com.leastfixedpoint.json.JSONReader;
import com.leastfixedpoint.json.JSONSyntaxError;
import com.leastfixedpoint.json.JSONWriter;

public class RoutingEngine {
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
                if (request.containsKey("ping")) {
                    sendOk(Map.of("pong", request.get("ping")));
                    continue;
                }

                // Crow flight calculator for now using both formulas to test for accuracy 
                // for accuracy for the speedup we can feed lots of routes and only look at the last few (as JVM needs to warm up)
                if (request.containsKey("routeFrom") && request.containsKey("to")) {
                    try {
                        Map<?,?> fromNode = (Map<?,?>) request.get("routeFrom");                        
                        Map<?,?> toNode = (Map<?,?>) request.get("to");                        
                        String startTime = (String) request.get("startingAt"); 

                        double latFrom = ((Number) fromNode.get("lat")).doubleValue();
                        double lonFrom = ((Number) fromNode.get("lon")).doubleValue();
                        double latTo = ((Number) toNode.get("lat")).doubleValue();
                        double lonTo = ((Number) toNode.get("lon")).doubleValue();

                        long startHaversine = System.nanoTime();
                        double distanceMetersHaversine = GeoCalculator.calculateHaversineDistance(latFrom, latTo, lonFrom, lonTo);
                        long endHaversine = System.nanoTime();
                        long timeHaversineNs = endHaversine - startHaversine;

                        long startEqui = System.nanoTime();
                        double distanceMetersEqui = GeoCalculator.calculateEquirectangularDistance(latFrom, latTo, lonFrom, lonTo);
                        long endEqui = System.nanoTime();
                        long timeEquiNs = endEqui - startEqui;

                        int walkMinutesHaversine = (int) Math.round(distanceMetersHaversine / 83.33);
                        
                        double errorPercentage = Math.abs(distanceMetersHaversine - distanceMetersEqui) / distanceMetersHaversine * 100.0;

                        double speedMultiplier = 0;
                        if (timeEquiNs > 0) {
                            speedMultiplier = (double) timeHaversineNs / timeEquiNs;
                        }

                        Map<String, Object> walkStep = new java.util.LinkedHashMap<>();
                        walkStep.put("mode", "walk");
                        walkStep.put("to", toNode);
                        walkStep.put("duration", walkMinutesHaversine);
                        walkStep.put("startTime", startTime);

                        // --- YOUR DEBUG PROFILING DATA ---
                        walkStep.put("debug_dist_haversine", distanceMetersHaversine);
                        walkStep.put("debug_dist_equi", distanceMetersEqui);
                        walkStep.put("debug_error_percent", errorPercentage);
                        walkStep.put("debug_time_haversine_ns", timeHaversineNs);
                        walkStep.put("debug_time_equi_ns", timeEquiNs);
                        walkStep.put("debug_speedup", speedMultiplier);

                        sendOk(new Object[]{ walkStep });

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
