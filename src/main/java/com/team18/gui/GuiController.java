package com.team18.gui;
import com.team18.routing.raptor.RaptorNetwork;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.util.ParsingUtil;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.control.Tooltip;

import com.team18.parser.GTFSParser;
import com.team18.model.Stop;
import com.team18.model.RouteStep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javafx.scene.shape.Polyline;
import javafx.scene.paint.Color;

public class GuiController {

    @FXML private Pane mapContainer;
    @FXML private TextField startField;
    @FXML private TextField endField;
    @FXML private TextField timeField;
    @FXML private VBox routeStepsContainer;

    private Map map;
    private RaptorNetwork raptorNetwork;
    public List<RouteStep> currentRoute;

    @FXML
    public void initialize() {
        map = new Map();

        if (map.getMapGroup() != null) {
            mapContainer.getChildren().add(map.getMapGroup());
            map.enableInteraction(mapContainer);
        }
        final boolean[] settingStart = {true}; // Toggle to switch between start and end inputs

// Inside initialize(), after map.enableInteraction(mapContainer);
        mapContainer.setOnMouseClicked(ev -> {
            // Ignore drags by checking if the mouse shifted significantly, or just rely on simple clicks
            if (ev.isStillSincePress()) {
                double mouseX = ev.getX();
                double mouseY = ev.getY();

                // Account for the map's current pan (translation) and zoom (scale)
                double localX = (mouseX - map.getMapGroup().getTranslateX()) / map.getMapGroup().getScaleX();
                double localY = (mouseY - map.getMapGroup().getTranslateY()) / map.getMapGroup().getScaleY();

                double[] latLon = map.getLatLonFromLocal(localX, localY);
                String coordString = String.format("%.6f, %.6f", latLon[0], latLon[1]);

                if (settingStart[0]) {
                    startField.setText(coordString);
                    settingStart[0] = false; // Next click sets destination
                } else {
                    endField.setText(coordString);
                    settingStart[0] = true;  // Next click resets to start
                }
            }
        });

        new Thread(() -> {
            try {
                System.out.println("Loading GTFS data...");
                GTFSParser parser = new GTFSParser();
                parser.loadFromZip("data/stockholm/sl.zip");

                System.out.println("Building RAPTOR Network (This might take a second)...");
                RaptorBuilder builder = new RaptorBuilder();
                raptorNetwork = builder.build(parser.agencies, parser.stops, parser.routes, parser.trips);

                System.out.println("Network Ready! Drawing stops on map...");

                Platform.runLater(() -> {
                    displayAllStops(parser.stops.values());
                });

            } catch (Exception e) {
                System.err.println("Failed to load GTFS/Raptor data: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    public void displayAllStops(Collection<Stop> stops) {
        map.getDrawingLayer().getChildren().clear();

        for (Stop stop : stops) {
            double[] coords = map.getLocalCoords(stop.lat, stop.lon);

            Circle dot = new Circle(3);
            dot.setCenterX(coords[0]);
            dot.setCenterY(coords[1]);
            dot.getStyleClass().add("stop-marker");

            Tooltip.install(dot, new Tooltip(stop.name));

            map.getDrawingLayer().getChildren().add(dot);
        }
    }

    public void displayRouteInstructions(List<RouteStep> steps) {
        routeStepsContainer.getChildren().clear(); // Clear old results

        if (steps == null || steps.isEmpty()) {
            routeStepsContainer.getChildren().add(new Label("No route found."));
            return;
        }

        for (RouteStep step : steps) {
            VBox stepCard = new VBox(5);
            stepCard.setStyle("-fx-background-color: #f4f4f4; -fx-padding: 10; -fx-background-radius: 5; -fx-border-color: #ddd; -fx-border-radius: 5;");

            // Check the public boolean 'walking' that the backend team created
            String modeText = step.walking ? "WALK" : (step.longName + " " + step.shortName + " " + step.headSign).trim().toUpperCase();
            Label modeLabel = new Label(modeText);
            modeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2196F3;");

            String destText = "To " + step.toStopName;
            Label detailsLabel = new Label(destText + " (" + step.durationMinutes + " mins)");
            detailsLabel.setWrapText(true);

            stepCard.getChildren().addAll(modeLabel, detailsLabel);
            routeStepsContainer.getChildren().add(stepCard);
        }
    }

    @FXML
    public void handlePlanJourney() {
        // Prevent crashing if they click the button before the background thread finishes
        if (raptorNetwork == null) {
            routeStepsContainer.getChildren().clear();
            routeStepsContainer.getChildren().add(new Label("Network still loading... Please wait."));
            return;
        }

        try {
            String start = startField.getText();
            String end = endField.getText();
            String time = timeField.getText();

            String[] startParts = start.split(",");
            String[] endParts = end.split(",");

            double startLat = Double.parseDouble(startParts[0].trim());
            double startLon = Double.parseDouble(startParts[1].trim());

            double endLat = Double.parseDouble(endParts[0].trim());
            double endLon = Double.parseDouble(endParts[1].trim());

            // Convert "08:30" into seconds after midnight
            int startTimeSeconds = ParsingUtil.parseStopTime(time);

            System.out.println("Routing from: (" + startLat + ", " + startLon + ") to (" + endLat + ", " + endLon + ")");

            // --- RUN REAL RAPTOR ALGORITHM ---
            RaptorAlgorithm raptorAlgorithm = new RaptorAlgorithm(raptorNetwork);

            // Save it to the class variable so your teammate can draw it later
            currentRoute = raptorAlgorithm.compute(startLat, startLon, endLat, endLon, startTimeSeconds);

            // Display it in your sidebar!
            displayRouteInstructions(currentRoute);

        } catch (Exception e) {
            routeStepsContainer.getChildren().clear();
            Label errorLabel = new Label("Invalid input. Click the map to set coordinates.");
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            routeStepsContainer.getChildren().add(errorLabel);
            e.printStackTrace();
        }
    }
}
