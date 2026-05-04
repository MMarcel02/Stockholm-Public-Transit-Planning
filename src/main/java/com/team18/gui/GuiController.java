package com.team18.gui;

import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;

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
    }

    @FXML
    public void handlePlanJourney() {
        try {
            // get values from input fields
            String start = startField.getText();
            String end = endField.getText();
            String time = timeField.getText();

            // split "lat,lon"
            String[] startParts = start.split(",");
            String[] endParts = end.split(",");

            // convert to numbers
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