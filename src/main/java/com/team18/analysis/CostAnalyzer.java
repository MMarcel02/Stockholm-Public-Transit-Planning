package com.team18.analysis;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.team18.model.Route;
import com.team18.optimizer.Config;
import com.team18.parser.GTFSParser;

// just extra util to help guess the ratios
public class CostAnalyzer {

    private static class RouteData {
        String outputLine;
        double difference;

        RouteData(String outputLine, double difference) {
            this.outputLine = outputLine;
            this.difference = difference;
        }
    }

    public static void main(String[] args) {
        String file = "data/costs/run_final_all_routes_testing.csv";
        GTFSParser gparser = new GTFSParser();
        try {
            gparser.loadFromZip("data/stockholm/sl_center.zip");            
        } catch (Exception e) { 
            e.printStackTrace();
        }
        
        List<RouteData> allRoutes = new ArrayList<>();
        
        // adjust this to guess what feels right
        double passengerCostWeight = 5; 
        String line;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            
            br.readLine();

            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");

                String routeId = data[0].trim();
                double routeCost = Double.parseDouble(data[1].trim());
                double passengerCost = passengerCostWeight * Double.parseDouble(data[2].trim());

                double difference = passengerCost - routeCost;

                // the 15 can also be adjusted, its just a ratio to try to exclude busses that are inbetween bounding boxes
                if (Math.abs(difference) < 0.001 || (routeCost / passengerCost) > 15) {
                    continue;
                }

                Route route = gparser.routes.get(routeId);
                
                String routeType = String.valueOf(route.routeType);
                String sName = route.shortName != null ? route.shortName : "";
                String lName = route.longName != null ? route.longName : "";
                String routeName = (sName + " " + lName).trim();

                if (!routeType.equals("BUS") || Config.CRITICAL_BUS_ROUTES.contains(sName)) {
                    continue;
                }
                

                String formattedLine = String.format("%-10s | %-12s | %-25s | %-10.2f | %-10.2f | %-10.2f ", 
                        routeId, routeType, routeName, routeCost, passengerCost, difference);
                
                allRoutes.add(new RouteData(formattedLine, difference));
            }

            allRoutes.sort(Comparator.comparingDouble(r -> r.difference));

            String tableHeader = String.format("%-10s | %-12s | %-25s | %-10s | %-10s | %-10s", 
                    "RouteID", "Type", "Name", "RouteCost", "PassCost", "Difference");
            String divider = "----------------------------------------------------------------------------------------------";

            System.out.println("### 5 BEST TO REMOVE ###");
            System.out.println(tableHeader);
            System.out.println(divider);
            for (int i = 0; i < Math.min(5, allRoutes.size()); i++) {
                System.out.println(allRoutes.get(i).outputLine);
            }

            System.out.println("\n### 5 BEST TO KEEP ###");
            System.out.println(tableHeader);
            System.out.println(divider);
            int startIndex = Math.max(0, allRoutes.size() - 5);
            for (int i = startIndex; i < allRoutes.size(); i++) {
                System.out.println(allRoutes.get(i).outputLine);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}