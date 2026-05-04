package com.team18.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;
import javafx.scene.control.Tooltip;

import com.team18.parser.GTFSParser;
import com.team18.model.Stop;

import java.util.Collection;

public class GuiController {

    @FXML private Pane mapContainer;
    @FXML private TextField startField;
    @FXML private TextField endField;
    @FXML private TextField timeField;
    @FXML private Label resultLabel;

    private Map map;

    @FXML
    public void initialize() {
        map = new Map();

        if (map.getMapGroup() != null) {
            mapContainer.getChildren().add(map.getMapGroup());
            map.enableInteraction(mapContainer);
        }

        new Thread(() -> {
            try {
                System.out.println("Loading GTFS data...");
                GTFSParser parser = new GTFSParser();

                parser.loadFromZip("data/stockholm/sl.zip");

                System.out.println("Data loaded! Drawing stops on map...");

                Platform.runLater(() -> {
                    displayAllStops(parser.stops.values());
                });

            } catch (Exception e) {
                System.err.println("Failed to load GTFS data: " + e.getMessage());
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

    @FXML
    public void handlePlanJourney() {
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

            String result =
                    "Start: (" + startLat + ", " + startLon + ")\n" +
                    "End: (" + endLat + ", " + endLon + ")\n" +
                    "Time: " + time;

            resultLabel.setText(result);
            System.out.println(result);

        } catch (Exception e) {
            resultLabel.setText("Invalid input. Use: lat,lon");
        }
    }
}