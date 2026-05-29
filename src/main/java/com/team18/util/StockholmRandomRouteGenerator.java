package com.team18.util;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.Locale;

public class StockholmRandomRouteGenerator {

    private static final Random random = new Random();

    // A configuration class to hold the details for each specific area
    private static class AreaConfig {
        String areaName;
        String filename;
        int numRoutes;
        double minLat, maxLat, minLon, maxLon;

        public AreaConfig(String areaName, String filename, int numRoutes, 
                          double minLat, double maxLat, double minLon, double maxLon) {
            this.areaName = areaName;
            this.filename = filename;
            this.numRoutes = numRoutes;
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.minLon = minLon;
            this.maxLon = maxLon;
        }
    }

    public static void main(String[] args) {
        String loadString = "{\"load\": \"data/stockholm/sl_center.zip\"}\n";

        AreaConfig metroArea = new AreaConfig(
            "Stockholm Metro Area", 
            "stockholm_metro_routes.jsonl", 
            10500, 
            58.7000, 60.2500, // MIN_LAT, MAX_LAT
            17.2000, 19.3000  // MIN_LON, MAX_LON
        );

        // Visualise here https://bboxfinder.com/#59.192826,17.786891,59.475075,18.356737
        AreaConfig urbanArea = new AreaConfig(
            "Stockholm Urban Area", 
            "stockholm_urban_routes.jsonl", 
            10500, 
            59.192826, 59.475075, // MIN_LAT, MAX_LAT
            17.786891, 18.356737  // MIN_LON, MAX_LON
        );

        generateRoutesForArea(metroArea, loadString);
        generateRoutesForArea(urbanArea, loadString);
    }

    private static void generateRoutesForArea(AreaConfig area, String loadString) {
        try (FileWriter writer = new FileWriter(area.filename)) {
            writer.write(loadString);
            for (int i = 0; i < area.numRoutes; i++) {
                double fromLat = getRandomCoordinate(area.minLat, area.maxLat);
                double fromLon = getRandomCoordinate(area.minLon, area.maxLon);
                double toLat = getRandomCoordinate(area.minLat, area.maxLat);
                double toLon = getRandomCoordinate(area.minLon, area.maxLon);
                String time = generateRandomTime();

                String jsonLine = String.format(Locale.US,
                        "{\"routeFrom\": {\"lat\": %.4f, \"lon\": %.4f}, \"to\": {\"lat\": %.4f, \"lon\": %.4f}, \"startingAt\": \"%s\"}\n",
                        fromLat, fromLon, toLat, toLon, time);
                
                writer.write(jsonLine);
            }
            System.out.println("Successfully generated " + area.numRoutes + " routes for " + area.areaName + " in '" + area.filename);
        } catch (IOException e) {
            System.err.println("An error occurred while writing the file for " + area.areaName);
            e.printStackTrace();
        }
    }

    private static double getRandomCoordinate(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    private static String generateRandomTime() {
        int hour = random.nextInt(17) + 6; 
        int minute = random.nextInt(60);   
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }
}
