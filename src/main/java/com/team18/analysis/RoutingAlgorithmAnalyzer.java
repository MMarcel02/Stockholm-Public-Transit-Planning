package com.team18.analysis;

import com.team18.parser.GTFSParser;
import com.team18.routing.Router;
import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.raptor.RaptorNetwork;
import com.team18.model.RouteStep;
import com.team18.util.ParsingUtil;
import com.team18.routing.AStar.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoutingAlgorithmAnalyzer {

    private static final String ROUTES_FILE = "stockholm_inner_urban_routes.jsonl";
    // private static final String ROUTES_FILE = "stockholm_outer_urban_routes.jsonl";
    private static final String GTFS_ZIP = "data/stockholm/sl_center.zip";
    private static final int WARMUP_ITERATIONS = 500;

    private static class RouteRequest {
        double latFrom, lonFrom, latTo, lonTo;
        int startTimeSeconds;

        public RouteRequest(double latFrom, double lonFrom, double latTo, double lonTo, int startTimeSeconds) {
            this.latFrom = latFrom;
            this.lonFrom = lonFrom;
            this.latTo = latTo;
            this.lonTo = lonTo;
            this.startTimeSeconds = startTimeSeconds;
        }
    }

    public static void main(String[] args) {
        List<RouteRequest> requests = loadRequests(ROUTES_FILE);

        if (requests.isEmpty()) {
            System.err.println("No routes loaded. Exiting.");
            return;
        }

        System.out.println("Loaded " + requests.size() + " route requests into memory\n");

        System.out.println("Parsing GTFS Zip...");
        long startParse = System.currentTimeMillis();
        GTFSParser parser = new GTFSParser();
        try {
            parser.loadFromZip(GTFS_ZIP);
        } catch (Exception e) {
            System.err.println("Failed to load GTFS data: " + e.getMessage());
            return;
        }
        long parseTime = System.currentTimeMillis() - startParse;
        System.out.printf("GTFS Parsing Time: %,d ms%n%n", parseTime);

        System.out.println("Building Raptor Network...");
        long startRaptorBuild = System.currentTimeMillis();
        RaptorBuilder raptorBuilder = new RaptorBuilder();
        RaptorNetwork raptorNetwork = raptorBuilder.build(parser.agencies, parser.stops, parser.routes, parser.trips);
        long raptorBuildTime = System.currentTimeMillis() - startRaptorBuild;
        System.out.printf("Raptor Network Build Time: %,d ms%n", raptorBuildTime);

        Router raptorRouter = new RaptorAlgorithm(raptorNetwork);
        System.out.println("Analyzing all trips with RAPTOR...");
        Report raptorReport = timeRouting(raptorRouter, requests);
        raptorReport.printReport();

        System.out.println("Building A* adjacency list...");
        long startAStarBuild = System.currentTimeMillis();
        TransitGraph graph = new TransitGraph();
        graph.build(parser);
        long aStarBuildTime = System.currentTimeMillis() - startAStarBuild;
        System.out.printf("A* Build Time: %,d ms%n", aStarBuildTime);

        // Router aStarRouter = new AStarRouter(parser);
        // System.out.println("Analyzing all trips with A*...");
        // Report aStarReport = timeRouting(aStarRouter, requests);
        // aStarReport.printReport();
        // raptorReport.printComparison(aStarReport);
    }

    private static List<RouteRequest> loadRequests(String filename) {
        List<RouteRequest> requests = new ArrayList<>();
        Pattern routePattern = Pattern.compile("\"routeFrom\": \\{\"lat\": ([\\d.]+), \"lon\": ([\\d.]+)\\}, \"to\": \\{\"lat\": ([\\d.]+), \"lon\": ([\\d.]+)\\}, \"startingAt\": \"([\\d:]+)\"");

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("load")) continue;

                Matcher matcher = routePattern.matcher(line);
                if (matcher.find()) {
                    double latFrom = Double.parseDouble(matcher.group(1));
                    double lonFrom = Double.parseDouble(matcher.group(2));
                    double latTo = Double.parseDouble(matcher.group(3));
                    double lonTo = Double.parseDouble(matcher.group(4));
                    int startTime = ParsingUtil.timeStringToSecondsAfterMidnight(matcher.group(5));

                    requests.add(new RouteRequest(latFrom, lonFrom, latTo, lonTo, startTime));
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading test routes: " + e.getMessage());
        }
        return requests;
    }

    private static Report timeRouting(Router router, List<RouteRequest> requests) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            RouteRequest req = requests.get(i);
            router.getFastestTrip(req.latFrom, req.lonFrom, req.latTo, req.lonTo, req.startTimeSeconds);
        }

        long totalSuccessTimeNs = 0;
        int successCount = 0;
        long totalExhaustionFailTimeNs = 0;
        int exhaustionFailCount = 0;
        long totalInitFailTimeNs = 0;
        int initFailCount = 0;

        for (int i = WARMUP_ITERATIONS; i < requests.size(); i++) {
            RouteRequest req = requests.get(i);
            
            long startQuery = System.nanoTime();
            List<RouteStep> result = router.getFastestTrip(req.latFrom, req.lonFrom, req.latTo, req.lonTo, req.startTimeSeconds);
            long duration = System.nanoTime() - startQuery;
            double durationMs = duration / 1_000_000.0;

            if (result != null && !result.isEmpty()) {
                totalSuccessTimeNs += duration;
                successCount++;
            } else {
                if (durationMs <= 4.0) {
                    totalInitFailTimeNs += duration;
                    initFailCount++;
                } else {
                    totalExhaustionFailTimeNs += duration;
                    exhaustionFailCount++;
                }
            }
        }

        return new Report(
            requests.size() - WARMUP_ITERATIONS,
            successCount,
            exhaustionFailCount,
            initFailCount,
            totalSuccessTimeNs,
            totalExhaustionFailTimeNs,
            totalInitFailTimeNs
        );
    }

    private static class Report {
        private final int totalRequests;
        private final int successCount;
        private final int exhaustionFailCount;
        private final int initFailCount;
        private final long totalSuccessTimeNs;
        private final long totalExhaustionFailTimeNs;
        private final long totalInitFailTimeNs;

        public Report(int totalRequests, int successCount, int exhaustionFailCount, int initFailCount, 
                      long totalSuccessTimeNs, long totalExhaustionFailTimeNs, long totalInitFailTimeNs) {
            this.totalRequests = totalRequests;
            this.successCount = successCount;
            this.exhaustionFailCount = exhaustionFailCount;
            this.initFailCount = initFailCount;
            this.totalSuccessTimeNs = totalSuccessTimeNs;
            this.totalExhaustionFailTimeNs = totalExhaustionFailTimeNs;
            this.totalInitFailTimeNs = totalInitFailTimeNs;
        }

        public void printReport() {
            long totalTimeNs = totalSuccessTimeNs + totalExhaustionFailTimeNs + totalInitFailTimeNs;

            System.out.printf("Report:\n");
            System.out.printf("Total Requests: %d (Success: %d, Exhaustion Fails: %d, Init Fails: %d)\n", 
                              totalRequests, successCount, exhaustionFailCount, initFailCount);
            
            System.out.printf("Overall Avg Time: %.2f ms\n", (totalTimeNs / 1_000_000.0) / totalRequests);
            System.out.printf("Success Avg Time: %.2f ms\n", (totalSuccessTimeNs / 1_000_000.0) / successCount);
            System.out.printf("Exhaustion Fail Avg Time: %.2f ms\n", (totalExhaustionFailTimeNs / 1_000_000.0) / exhaustionFailCount);
            System.out.printf("Init Fail Avg Time: %.2f ms\n", (totalInitFailTimeNs / 1_000_000.0) / initFailCount);
            System.out.println();
        }

        public void printComparison(Report aStar) {
            System.out.println("Comparison: RAPTOR vs A*");

            double raptorSuccessPct = (double) this.successCount / this.totalRequests * 100;
            double aStarSuccessPct = (double) aStar.successCount / aStar.totalRequests * 100;
            
            double raptorExhaustionPct = (double) this.exhaustionFailCount / this.totalRequests * 100;
            double aStarExhaustionPct = (double) aStar.exhaustionFailCount / aStar.totalRequests * 100;
            
            double raptorInitPct = (double) this.initFailCount / this.totalRequests * 100;
            double aStarInitPct = (double) aStar.initFailCount / aStar.totalRequests * 100;

            System.out.printf("Success Rate: RAPTOR %.2f%% | A* %.2f%% | Diff: %+.2f%%\n", raptorSuccessPct, aStarSuccessPct, (raptorSuccessPct - aStarSuccessPct));
            System.out.printf("Exhaustion Fails: RAPTOR %.2f%% | A* %.2f%% | Diff: %+.2f%%\n", raptorExhaustionPct, aStarExhaustionPct, (raptorExhaustionPct - aStarExhaustionPct));
            System.out.printf("Initial Fails: RAPTOR %.2f%% | A* %.2f%% | Diff: %+.2f%%\n\n", raptorInitPct, aStarInitPct, (raptorInitPct - aStarInitPct));

            long raptorTotalTimeNs = this.totalSuccessTimeNs + this.totalExhaustionFailTimeNs + this.totalInitFailTimeNs;
            long aStarTotalTimeNs = aStar.totalSuccessTimeNs + aStar.totalExhaustionFailTimeNs + aStar.totalInitFailTimeNs;

            double raptorOverallAvg = (raptorTotalTimeNs / 1_000_000.0) / this.totalRequests;
            double aStarOverallAvg = (aStarTotalTimeNs / 1_000_000.0) / aStar.totalRequests;
            printSpeedComparison("Overall Avg Time", raptorOverallAvg, aStarOverallAvg);

            double raptorSuccessAvg = (this.totalSuccessTimeNs / 1_000_000.0) / this.successCount;
            double aStarSuccessAvg = (aStar.totalSuccessTimeNs / 1_000_000.0) / aStar.successCount;
            printSpeedComparison("Success Avg Time", raptorSuccessAvg, aStarSuccessAvg);

            double raptorExhaustAvg = (this.totalExhaustionFailTimeNs / 1_000_000.0) / this.exhaustionFailCount;
            double aStarExhaustAvg = (aStar.totalExhaustionFailTimeNs / 1_000_000.0) / aStar.exhaustionFailCount;
            printSpeedComparison("Exhaustion Avg Time", raptorExhaustAvg, aStarExhaustAvg);

            double raptorInitAvg = (this.totalInitFailTimeNs / 1_000_000.0) / this.initFailCount;
            double aStarInitAvg = (aStar.totalInitFailTimeNs / 1_000_000.0) / aStar.initFailCount;
            printSpeedComparison("Init Avg Time", raptorInitAvg, aStarInitAvg);
            System.out.println();
        }

        private void printSpeedComparison(String metricName, double raptorTime, double aStarTime) {
            double speedup = aStarTime / raptorTime;
            
            System.out.printf("%s: RAPTOR %.2f ms | A* %.2f ms -> Speedup: %.2fx\n", 
                              metricName, raptorTime, aStarTime, speedup);
        }
    }
}