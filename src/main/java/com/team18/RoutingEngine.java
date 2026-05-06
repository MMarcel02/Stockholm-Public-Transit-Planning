package com.team18;

import com.team18.model.RouteStep;
import com.team18.parser.GTFSParser;
import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.raptor.RaptorNetwork;
import com.team18.routing.Router;
import com.team18.routing.AStar.AStarRouter;
import com.team18.routing.AStar.TransitGraph;
import com.team18.util.ParsingUtil;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import java.util.zip.*;

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
        GTFSParser parser = new GTFSParser();

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

                // Helpful resource
                //https://www.baeldung.com/java-read-zip-files
                if (request.containsKey("load")) {
                    String zipFilePath = (String) request.get("load");
                    try {
                        // GTFSParser parser = new GTFSParser();
                        parser.loadFromZip(zipFilePath);
                        // RaptorBuilder builder = new RaptorBuilder();
                        // this.raptorNetwork = builder.build(parser.agencies, parser.stops, parser.routes, parser.trips);
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

                if (request.containsKey("routeFrom") && request.containsKey("to") && request.containsKey("startingAt")) {
                    // if (this.raptorNetwork == null) {
                    //     sendError("Raptor network not loaded, check that 'load' request was sent earlier.");
                    //     continue;
                    // }

                    try {
                        Map<?,?> fromNode = (Map<?,?>) request.get("routeFrom");                        
                        Map<?,?> toNode = (Map<?,?>) request.get("to");                        
                        String startTime = (String) request.get("startingAt"); 
                        int startTimeSecondsAfterMidnight = ParsingUtil.parseStopTime(startTime);

                        double latFrom = ((Number) fromNode.get("lat")).doubleValue();
                        double lonFrom = ((Number) fromNode.get("lon")).doubleValue();
                        double latTo = ((Number) toNode.get("lat")).doubleValue();
                        double lonTo = ((Number) toNode.get("lon")).doubleValue();
                        
                        // Router raptor = new RaptorAlgorithm(raptorNetwork);
                        // List<RouteStep> journey = raptor.getFastestTrip(latFrom, lonFrom, latTo, lonTo, startTimeSecondsAfterMidnight);
                        // Object[] routeSteps = new Object[journey.size()];
                        // for (int i = 0; i < routeSteps.length; i++) {
                        //     routeSteps[i] = journey.get(i).toMap();
                        // }
                        
                        Router aStar = new AStarRouter(parser);
                        List<RouteStep> journey = aStar.getFastestTrip(latFrom, lonFrom, latTo, lonTo, startTimeSecondsAfterMidnight);
                        Object[] routeSteps = new Object[journey.size()];
                        for (int i = 0; i < routeSteps.length; i++) {
                            routeSteps[i] = journey.get(i).toMap();
                        }

                        sendOk(routeSteps);
                    } catch (ClassCastException | NullPointerException e) {
                        sendError("Coordinates must be formatted as numbers" + e.getMessage());
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
