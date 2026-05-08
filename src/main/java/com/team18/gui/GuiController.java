package com.team18.gui;

import com.team18.routing.raptor.RaptorNetwork;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.Router;
import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.util.ParsingUtil;
import com.team18.gui.FullRoute;

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
		GTFSParser parser = null;
		try {
			System.out.println("Loading GTFS data...");
			parser = new GTFSParser();
			parser.loadFromZip("data/stockholm/sl.zip");

			System.out.println("Building RAPTOR Network (This might take a second)...");
			RaptorBuilder builder = new RaptorBuilder();
			raptorNetwork = builder.build(parser.agencies, parser.stops, parser.routes, parser.trips);

			System.out.println("Network Ready! Drawing stops on map...");
		} catch (Exception e) {
			System.err.println("Failed to load GTFS/Raptor data: " + e.getMessage());
			e.printStackTrace();
		}

		map = new Map(parser);

		if (map.getMapGroup() != null) {
			mapContainer.getChildren().add(map.getMapGroup());
		}

		final boolean[] settingStart = {true};


		map.getMapGroup().setOnMouseClicked(ev -> {
			if (ev.isStillSincePress()) {


				double localX = ev.getX();
				double localY = ev.getY();

				double[] latLon = map.getLatLonFromLocal(localX, localY);
				double lat = latLon[0];
				double lon = latLon[1];

				String coordString = String.format("%.6f, %.6f", lat, lon);

				if (settingStart[0]) {
					startField.setText(coordString);
					map.setStartMarker(lat, lon); // green pin
					settingStart[0] = false;
				} else {
					endField.setText(coordString);
					map.setEndMarker(lat, lon);   // red pin
					settingStart[0] = true;       //
				}
			}
		});
		map.setOnStopClicked(landmark -> {
			String coordString = String.format("%.6f, %.6f", landmark.lat, landmark.lon);

			if (settingStart[0]) {
				startField.setText(coordString);
				map.setStartMarker(landmark.lat, landmark.lon);
				settingStart[0] = false;
			} else {
				endField.setText(coordString);
				map.setEndMarker(landmark.lat, landmark.lon);
				settingStart[0] = true;
			}
		});


		new Thread(() -> {
		}).start();
	}

	public void displayRouteInstructions(List<RouteStep> steps) {
		routeStepsContainer.getChildren().clear();

		if (steps == null || steps.isEmpty()) {
			routeStepsContainer.getChildren().add(new Label("No route found."));
			return;
		}

		for (RouteStep step : steps) {
			if (step.walking && step.durationMinutes <= 0 && "destination".equals(step.toStopName)) {
				continue;
			}
			VBox stepCard = new VBox(5);
			stepCard.setStyle("-fx-background-color: #f4f4f4; -fx-padding: 10; -fx-background-radius: 5; -fx-border-color: #ddd; -fx-border-radius: 5;");

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

			int startTimeSeconds = ParsingUtil.parseStopTime(time);

			System.out.println("Routing from: (" + startLat + ", " + startLon + ") to (" + endLat + ", " + endLon + ")");

			Router raptorAlgorithm = new RaptorAlgorithm(raptorNetwork);

			currentRoute = raptorAlgorithm.getFastestTrip(startLat, startLon, endLat, endLon, startTimeSeconds);
			map.setRoute(new FullRoute(startLat, startLon, currentRoute));

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
