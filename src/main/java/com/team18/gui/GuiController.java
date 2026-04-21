package com.team18.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import javafx.scene.control.TextField;
import javafx.scene.shape.Circle;
import javafx.scene.control.Tooltip;

// Import your parser and model
import com.team18.parser.GTFSParser;
import com.team18.model.Stop;
import java.util.Collection;

public class GuiController {

    @FXML private Pane mapContainer;
    @FXML private TextField startField;
    @FXML private TextField endField;

    private Map map;

    @FXML
    public void initialize() {
        // 1. Initialize the pure Java OpenStreetMap tile engine
        map = new Map();

        if (map.getMapGroup() != null) {
            mapContainer.getChildren().add(map.getMapGroup());
            map.enableInteraction(mapContainer);
        }

        // 2. Load GTFS data in a background thread to prevent UI freezing
        new Thread(() -> {
            try {
                System.out.println("Loading GTFS data...");
                GTFSParser parser = new GTFSParser();

                // Make sure this file is in your project's root directory!
                parser.loadFromZip("data/stockholm/sl.zip");

                System.out.println("Data loaded! Drawing stops on map...");

                // 3. Update the UI on the main JavaFX thread
                Platform.runLater(() -> {
                    displayAllStops(parser.stops.values());
                });

            } catch (Exception e) {
                System.err.println("Failed to load GTFS data: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // Your new method for rendering the stops
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

    @FXML
    public void handlePlanJourney() {
        System.out.println("Routing from: " + startField.getText() + " to " + endField.getText());
    }
}