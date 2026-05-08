package com.team18.analysis;

import com.team18.util.GeoCalculator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WalkingMethodAnalyzer {
    
    private static final int WARMUP_ITERATIONS = 500;
    private static final String ROUTES_FILE = "stockholm_routes.jsonl";

    public static void main(String[] args) {
        
        double totalErrorPercent = 0.0;
        long totalTimeHaversineNs = 0;
        long totalTimeEquiNs = 0;
        int count = 0;
        int lineCount = 0;
        
        Pattern coordPattern = Pattern.compile("\"routeFrom\": \\{\"lat\": ([\\d.]+), \"lon\": ([\\d.]+)\\}, \"to\": \\{\"lat\": ([\\d.]+), \"lon\": ([\\d.]+)\\}");
        
        try (BufferedReader reader = new BufferedReader(new FileReader(ROUTES_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("load")) continue; 

                Matcher matcher = coordPattern.matcher(line);
                if (matcher.find()) {
                    double latFrom = Double.parseDouble(matcher.group(1));
                    double lonFrom = Double.parseDouble(matcher.group(2));
                    double latTo = Double.parseDouble(matcher.group(3));
                    double lonTo = Double.parseDouble(matcher.group(4));

                    long startHaversine = System.nanoTime();
                    double distHaversine = GeoCalculator.calculateHaversineDistance(latFrom, lonFrom, latTo, lonTo);
                    long timeHaversine = System.nanoTime() - startHaversine;

                    long startEqui = System.nanoTime();
                    double distEqui = GeoCalculator.calculateEquirectangularDistance(latFrom, lonFrom, latTo, lonTo);
                    long timeEqui = System.nanoTime() - startEqui;
                    
                    if (lineCount >= WARMUP_ITERATIONS) {
                        double errorPercentage = Math.abs(distHaversine - distEqui) / distHaversine * 100.0;
                        totalErrorPercent += errorPercentage;
                        totalTimeHaversineNs += timeHaversine;
                        totalTimeEquiNs += timeEqui;
                        count++;
                    }
                    lineCount++;
                }
            }
            
            if (count > 0) {
                double avgError = totalErrorPercent / count;
                double speedup = (double) totalTimeHaversineNs / totalTimeEquiNs;
                
                System.out.println("Successfully processed " + count + " routes");
                System.out.printf("Average Error Percent: %.8f%%%n", avgError);
                System.out.printf("Average Speedup: %.2fx%n", speedup);
            } else {
                System.out.println("No valid coordinate data found in the file.");
            }
            
        } catch (IOException e) {
            System.err.println("Error reading the file. Make sure " + ROUTES_FILE + " exists in this directory.");
        }
    }
}