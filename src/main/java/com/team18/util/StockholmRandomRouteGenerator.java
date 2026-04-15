package com.team18.util;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.Locale;

public class StockholmRandomRouteGenerator {

    // Generates random routes around stocholm area so we can measure the accuracy and speedup of equirectangular vs haversine methods
    // Rough box for stockholm area
    private static final double MIN_LAT = 59.1800;
    private static final double MAX_LAT = 59.5500;
    private static final double MIN_LON = 17.6000;
    private static final double MAX_LON = 19.0000;
    
    private static final Random random = new Random();

    public static void main(String[] args) {
        int numRoutes = 10000;
        String filename = "stockholm_routes.jsonl";

        try (FileWriter writer = new FileWriter(filename)) {
            for (int i = 0; i < numRoutes; i++) {
                double fromLat = getRandomCoordinate(MIN_LAT, MAX_LAT);
                double fromLon = getRandomCoordinate(MIN_LON, MAX_LON);
                double toLat = getRandomCoordinate(MIN_LAT, MAX_LAT);
                double toLon = getRandomCoordinate(MIN_LON, MAX_LON);
                String time = generateRandomTime();

                String jsonLine = String.format(Locale.US,
                        "{\"routeFrom\": {\"lat\": %.4f, \"lon\": %.4f}, \"to\": {\"lat\": %.4f, \"lon\": %.4f}, \"startingAt\": \"%s\", \"debug\": \"%s\"}\n",
                        fromLat, fromLon, toLat, toLon, time, true);
                
                writer.write(jsonLine);
            }
            System.out.println("Successfully generated " + numRoutes + " routes in '" + filename + "'.");
        } catch (IOException e) {
            System.err.println("An error occurred while writing the file:");
            e.printStackTrace();
        }
    }

    private static double getRandomCoordinate(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    private static String generateRandomTime() {
        int hour = random.nextInt(17) + 6; // Random hour from 6 to 22
        int minute = random.nextInt(60);   // Random minute from 0 to 59
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }
}