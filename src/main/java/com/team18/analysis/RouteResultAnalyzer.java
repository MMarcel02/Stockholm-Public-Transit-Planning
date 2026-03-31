package com.team18.analysis;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RouteResultAnalyzer {
    // Calcs average of speedup and error rate for the random stockholm routes, so we can compare methods of crow flies distance
    public static void main(String[] args) {
        String filename = "stockholm_routes_results.jsonl"; 
        
        double totalErrorPercent = 0.0;
        double totalSpeedup = 0.0;
        int count = 0;
        int lineCount = 0;
        
        Pattern errorPattern = Pattern.compile("\"DEBUG_error_percent\"\\s*:\\s*([-+]?[\\d.]+(?:[eE][-+]?\\d+)?)");
        Pattern speedupPattern = Pattern.compile("\"DEBUG_speedup\"\\s*:\\s*([\\d.]+)");
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) { 
                // Ignore first 100 routes while JVM warms up (to get rid of misleading speedups)
                if (lineCount >= 100) {
                    Matcher errorMatcher = errorPattern.matcher(line);
                    Matcher speedupMatcher = speedupPattern.matcher(line);
                    
                    if (errorMatcher.find() && speedupMatcher.find()) {
                        totalErrorPercent += Double.parseDouble(errorMatcher.group(1));
                        totalSpeedup += Double.parseDouble(speedupMatcher.group(1));
                        count++;
                    }
                }
                lineCount++;
            }
            
            // Calculate and print the averages rounded to 2 decimal places
            if (count > 0) {
                double avgError = totalErrorPercent / count;
                double avgSpeedup = totalSpeedup / count;
                
                System.out.println("Successfully processed " + count + " routes.");
                System.out.printf("Average Error Percent: %.8f%%%n", avgError);
                System.out.printf("Average Speedup: %.2fx%n", avgSpeedup);
            } else {
                System.out.println("No valid 'DEBUG_error_percent' or 'DEBUG_speedup' data found in the file.");
            }
            
        } catch (IOException e) {
            System.err.println("Error reading the file. Make sure " + filename + " exists in this directory.");
        }
    }
}