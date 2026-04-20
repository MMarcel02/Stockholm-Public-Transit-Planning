package com.team18.gui;

import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import javafx.scene.control.TextField;

public class GuiController {

    @FXML private Pane mapContainer;
    @FXML private TextField startField;
    @FXML private TextField endField;

    private Map map;

    @FXML
    public void initialize() {
        // Initialize the pure Java OpenStreetMap tile engine
        map = new Map();

        if (map.getMapGroup() != null) {
            mapContainer.getChildren().add(map.getMapGroup());
            map.enableInteraction(mapContainer);
        }
    }

    @FXML
    public void handlePlanJourney() {
        System.out.println("Routing from: " + startField.getText() + " to " + endField.getText());
    }
}