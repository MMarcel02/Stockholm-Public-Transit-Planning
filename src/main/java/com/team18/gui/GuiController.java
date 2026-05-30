package com.team18.gui;

import com.team18.routing.raptor.RaptorNetwork;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.Router;
import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.util.GeoCalculator;
import com.team18.util.ParsingUtil;

import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.geometry.Point2D;

import com.team18.parser.GTFSParser;
import com.team18.model.Stop;
import com.team18.model.RouteStep;
import com.team18.model.RouteStepType;
import com.team18.model.StopTime;
import com.team18.model.Trip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class GuiController {
	private static final int MAX_SUGGESTIONS = 10;
	private static final double STOP_HOVER_CARD_WIDTH = 340;
	private static final double STOP_HOVER_CARD_HEIGHT = 390;

	@FXML private Pane mapContainer;
	@FXML private TextField startField;
	@FXML private TextField endField;
	@FXML private TextField timeField;
	@FXML private VBox routeStepsContainer;

	private Map map;
	private RaptorNetwork raptorNetwork;
	private List<Stop> stops = new ArrayList<>();
	private java.util.Map<String, List<ArrivalInfo>> stopArrivalsByStopId = new HashMap<>();
	private Stop selectedStartStop;
	private Stop selectedEndStop;
	private VBox stopHoverCard;
	private Landmark hoveredLandmark;
	private boolean settingFieldProgrammatically = false;
	public List<RouteStep> currentRoute;

	@FXML
	public void initialize() {
		GTFSParser parser = null;
		try {
			System.out.println("Loading GTFS data...");
			parser = new GTFSParser();
			parser.loadFromZip("data/stockholm/sl_center.zip");
			stops = new ArrayList<>(parser.stops.values());
			stops.sort(Comparator.comparing(stop -> stop.name.toLowerCase()));
			buildStopArrivalIndex(parser);

			System.out.println("Building RAPTOR Network...");
			RaptorBuilder builder = new RaptorBuilder();
			raptorNetwork = builder.build(parser.agencies, parser.stops, parser.routes, parser.trips);

			System.out.println("Network Ready! Drawing stops on map...");
		} catch (Exception e) {
			System.err.println("Failed to load GTFS/Raptor data: " + e.getMessage());
			e.printStackTrace();
		}

		map = new Map(parser);
		setupStopAutocomplete(startField, true);
		setupStopAutocomplete(endField, false);

		if (map.getMapGroup() != null) {
			mapContainer.getChildren().add(map.getMapGroup());
		}

		final boolean[] settingStart = {true};
		setupStopHoverCard();


		map.getMapGroup().setOnMouseClicked(ev -> {
			if (ev.isStillSincePress()) {


				double localX = ev.getX();
				double localY = ev.getY();

				Landmark stop = map.findStopNearLocal(localX, localY, 10);
				if (stop != null) {
					selectLandmark(stop, settingStart);
					hideStopHoverCard();
					return;
				}

				double[] latLon = map.getLatLonFromLocal(localX, localY);
				double lat = latLon[0];
				double lon = latLon[1];

				String coordString = String.format("%.6f, %.6f", lat, lon);

				if (settingStart[0]) {
					setFieldText(startField, coordString);
					selectedStartStop = null;
					map.setStartMarker(lat, lon); // green pin
					settingStart[0] = false;
				} else {
					setFieldText(endField, coordString);
					selectedEndStop = null;
					map.setEndMarker(lat, lon);   // red pin
					settingStart[0] = true;       //
				}
			}
		});

		mapContainer.setOnMouseMoved(ev -> {
			if (stopHoverCard != null && stopHoverCard.isHover()) {
				return;
			}

			Point2D localPoint = map.getMapGroup().sceneToLocal(ev.getSceneX(), ev.getSceneY());
			Landmark stop = map.findStopNearLocal(localPoint.getX(), localPoint.getY(), 10);
			if (stop == null) {
				if (isMouseNearStopHoverCard(ev.getX(), ev.getY())) {
					return;
				}
				hideStopHoverCard();
				return;
			}

			showStopHoverCard(stop, ev.getX(), ev.getY(), settingStart);
		});

		mapContainer.setOnMouseExited(ev -> {
			if (stopHoverCard == null || !stopHoverCard.isHover()) {
				hideStopHoverCard();
			}
		});

		new Thread(() -> {
		}).start();
	}

	private void selectLandmark(Landmark landmark, boolean[] settingStart) {
		if (settingStart[0]) {
			selectLandmarkAsStart(landmark, settingStart);
		} else {
			selectLandmarkAsEnd(landmark, settingStart);
		}
	}

	private void selectLandmarkAsStart(Landmark landmark, boolean[] settingStart) {
		String fieldText = getLandmarkFieldText(landmark);
		setFieldText(startField, fieldText);
		selectedStartStop = findExactStopByName(fieldText);
		map.setStartMarker(landmark.lat, landmark.lon);
		settingStart[0] = false;
	}

	private void selectLandmarkAsEnd(Landmark landmark, boolean[] settingStart) {
		String fieldText = getLandmarkFieldText(landmark);
		setFieldText(endField, fieldText);
		selectedEndStop = findExactStopByName(fieldText);
		map.setEndMarker(landmark.lat, landmark.lon);
		settingStart[0] = true;
	}

	private String getLandmarkFieldText(Landmark landmark) {
		return landmark.name != null && !landmark.name.isBlank()
				? landmark.name
				: String.format("%.6f, %.6f", landmark.lat, landmark.lon);
	}

	private void setupStopHoverCard() {
		stopHoverCard = new VBox(8);
		stopHoverCard.getStyleClass().add("stop-hover-card");
		stopHoverCard.setVisible(false);
		stopHoverCard.setMouseTransparent(false);
		stopHoverCard.setPrefWidth(STOP_HOVER_CARD_WIDTH);
		stopHoverCard.setMinWidth(STOP_HOVER_CARD_WIDTH);
		stopHoverCard.setMaxWidth(STOP_HOVER_CARD_WIDTH);
		stopHoverCard.setOnMouseExited(ev -> hideStopHoverCard());
		mapContainer.getChildren().add(stopHoverCard);
	}

	private void showStopHoverCard(Landmark landmark, double mouseX, double mouseY, boolean[] settingStart) {
		if (landmark == hoveredLandmark && stopHoverCard.isVisible()) {
			return;
		}

		hoveredLandmark = landmark;
		stopHoverCard.getChildren().clear();

		Label nameLabel = new Label(landmark.name == null || landmark.name.isBlank() ? "Unnamed stop" : landmark.name);
		nameLabel.getStyleClass().add("stop-hover-title");
		nameLabel.setWrapText(true);
		nameLabel.setPrefWidth(STOP_HOVER_CARD_WIDTH - 24);
		nameLabel.setMinHeight(38);

		Label coordinatesLabel = new Label(String.format(Locale.US, "%.6f, %.6f", landmark.lat, landmark.lon));
		coordinatesLabel.getStyleClass().add("stop-hover-coordinates");
		coordinatesLabel.setPrefWidth(STOP_HOVER_CARD_WIDTH - 24);
		coordinatesLabel.setTextOverrun(OverrunStyle.CLIP);

		Label arrivalsLabel = new Label("Rides through this stop");
		arrivalsLabel.getStyleClass().add("stop-hover-section-title");

		ListView<String> arrivalsList = new ListView<>();
		arrivalsList.getStyleClass().add("stop-hover-arrivals");
		arrivalsList.setPrefWidth(STOP_HOVER_CARD_WIDTH - 24);
		arrivalsList.setMinWidth(STOP_HOVER_CARD_WIDTH - 24);
		arrivalsList.setPrefHeight(145);
		arrivalsList.setMinHeight(145);
		arrivalsList.getItems().addAll(getArrivalRows(landmark));

		Button startButton = new Button("Start");
		startButton.getStyleClass().add("stop-hover-button");
		startButton.setPrefWidth(122);
		startButton.setMinWidth(122);
		startButton.setMinHeight(34);
		startButton.setOnAction(ev -> {
			selectLandmarkAsStart(landmark, settingStart);
			hideStopHoverCard();
		});

		Button endButton = new Button("Destination");
		endButton.getStyleClass().add("stop-hover-button");
		endButton.setPrefWidth(122);
		endButton.setMinWidth(122);
		endButton.setMinHeight(34);
		endButton.setOnAction(ev -> {
			selectLandmarkAsEnd(landmark, settingStart);
			hideStopHoverCard();
		});

		Button disableButton = new Button("Disable stop");
		disableButton.getStyleClass().add("stop-hover-button-muted");
		disableButton.setPrefWidth(STOP_HOVER_CARD_WIDTH - 28);
		disableButton.setMinWidth(STOP_HOVER_CARD_WIDTH - 28);
		disableButton.setMinHeight(34);
		disableButton.setOnAction(ev -> {
			raptorNetwork.toggleStop(landmark.id);
			
			// TODO: Make a diff button for this obvs, just in here for testing
			// Would be cool to have it as a button on the actual GUI, then you can click to disable / enable all stops
			// And then pick and choose which to add / remove
			// raptorNetwork.disableAllStops();
			// raptorNetwork.enableAllStops();
		});


		HBox actionRow = new HBox(8);
		actionRow.setPrefWidth(STOP_HOVER_CARD_WIDTH - 28);
		actionRow.setMinWidth(STOP_HOVER_CARD_WIDTH - 28);
		actionRow.getChildren().addAll(startButton, endButton);

		stopHoverCard.getChildren().addAll(nameLabel, coordinatesLabel, arrivalsLabel, arrivalsList, actionRow, disableButton);
		stopHoverCard.setVisible(true);
		stopHoverCard.toFront();
		stopHoverCard.applyCss();
		stopHoverCard.autosize();
		stopHoverCard.layout();
		positionStopHoverCard(mouseX, mouseY);
	}

	private void positionStopHoverCard(double mouseX, double mouseY) {
		double x = mouseX + 14;
		double y = mouseY + 14;

		if (x + STOP_HOVER_CARD_WIDTH > mapContainer.getWidth()) {
			x = mouseX - STOP_HOVER_CARD_WIDTH - 14;
		}
		if (y + STOP_HOVER_CARD_HEIGHT > mapContainer.getHeight()) {
			y = mouseY - STOP_HOVER_CARD_HEIGHT - 14;
		}

		stopHoverCard.relocate(Math.max(8, x), Math.max(8, y));
	}

	private boolean isMouseNearStopHoverCard(double mouseX, double mouseY) {
		if (stopHoverCard == null || !stopHoverCard.isVisible()) {
			return false;
		}

		double margin = 18;
		double minX = stopHoverCard.getLayoutX() - margin;
		double minY = stopHoverCard.getLayoutY() - margin;
		double maxX = stopHoverCard.getLayoutX() + stopHoverCard.getWidth() + margin;
		double maxY = stopHoverCard.getLayoutY() + stopHoverCard.getHeight() + margin;

		return mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY;
	}

	private void hideStopHoverCard() {
		if (stopHoverCard != null) {
			stopHoverCard.setVisible(false);
		}
		hoveredLandmark = null;
	}

	private void buildStopArrivalIndex(GTFSParser parser) {
		stopArrivalsByStopId.clear();

		for (Trip trip : parser.trips.values()) {
			for (StopTime stopTime : trip.stopTimes) {
				ArrivalInfo arrival = new ArrivalInfo(
						stopTime.arrivalTime,
						trip.route.shortName,
						trip.route.longName,
						trip.headSign
				);
				stopArrivalsByStopId
						.computeIfAbsent(stopTime.stop.id, ignored -> new ArrayList<>())
						.add(arrival);
			}
		}

		for (List<ArrivalInfo> arrivals : stopArrivalsByStopId.values()) {
			arrivals.sort(Comparator.comparingInt(arrival -> arrival.arrivalTimeSeconds));
		}
	}

	private List<String> getArrivalRows(Landmark landmark) {
		List<ArrivalInfo> arrivals = stopArrivalsByStopId.get(landmark.id);
		if (arrivals == null || arrivals.isEmpty()) {
			return List.of("No scheduled rides found");
		}

		int earliestTime = getPopupStartTimeSeconds();
		List<String> rows = new ArrayList<>();

		for (ArrivalInfo arrival : arrivals) {
			if (arrival.arrivalTimeSeconds < earliestTime) {
				continue;
			}
			rows.add(formatArrivalRow(arrival));
		}

		if (rows.isEmpty()) {
			rows.add("No later rides today");
		}

		return rows;
	}

	private int getPopupStartTimeSeconds() {
		try {
			String timeText = timeField.getText();
			if (timeText == null || timeText.isBlank()) {
				return 0;
			}
			return ParsingUtil.timeStringToSecondsAfterMidnight(timeText);
		} catch (Exception e) {
			return 0;
		}
	}

	private String formatArrivalRow(ArrivalInfo arrival) {
		String routeName = !arrival.shortName.isBlank() ? arrival.shortName : arrival.longName;
		if (routeName == null || routeName.isBlank()) {
			routeName = "Route";
		}

		String headSign = arrival.headSign == null || arrival.headSign.isBlank()
				? ""
				: " to " + arrival.headSign;

		return formatAbsoluteTime(arrival.arrivalTimeSeconds) + "   " + routeName + headSign;
	}

	private String formatAbsoluteTime(int secondsAfterMidnight) {
		int hours = secondsAfterMidnight / 3600;
		int minutes = (secondsAfterMidnight % 3600) / 60;
		return String.format(Locale.US, "%02d:%02d", hours, minutes);
	}

	public void displayRouteInstructions(List<RouteStep> steps, double startLat, double startLon) {
		routeStepsContainer.getChildren().clear();

		if (steps == null || steps.isEmpty()) {
			routeStepsContainer.getChildren().add(new Label("No route found."));
			return;
		}

		routeStepsContainer.getChildren().add(buildRouteSummary(steps, startLat, startLon));

		for (RouteStep step : steps) {
			
			VBox stepCard = new VBox(5);
			stepCard.setStyle("-fx-background-color: #f4f4f4; -fx-padding: 10; -fx-background-radius: 5; -fx-border-color: #ddd; -fx-border-radius: 5;");

			String modeText = step.routeStepType == RouteStepType.TRANSIT ? (step.route.longName + " " + step.route.shortName + " " + step.headSign).trim().toUpperCase() : "WALK";
			Label modeLabel = new Label(modeText);
			modeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2196F3;");

			String destText;
			if (step.routeStepType == RouteStepType.DIRECT_WALK || step.routeStepType == RouteStepType.WALK_TO_DEST) {
				destText = "To destination";
			} else {
				destText = "To " + step.toStop.name;
			}

			int duration = (int) Math.round(step.durationMinutes);
			Label detailsLabel;

			if (duration == 0) {
				detailsLabel = new Label(destText + " ( <1 min)");
			} else {
				detailsLabel = new Label(destText + " (" + duration + " mins)");
			}

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

			ResolvedLocation startLocation = resolveLocation(start, selectedStartStop);
			ResolvedLocation endLocation = resolveLocation(end, selectedEndStop);

			int startTimeSeconds = ParsingUtil.timeStringToSecondsAfterMidnight(time);

			System.out.println("Routing from: (" + startLocation.lat + ", " + startLocation.lon + ") to (" + endLocation.lat + ", " + endLocation.lon + ")");

			Router raptorAlgorithm = new RaptorAlgorithm(raptorNetwork);

			currentRoute = raptorAlgorithm.getFastestTrip(startLocation.lat, startLocation.lon, endLocation.lat, endLocation.lon, startTimeSeconds);
			map.setStartMarker(startLocation.lat, startLocation.lon);
			map.setEndMarker(endLocation.lat, endLocation.lon);
			map.setRoute(new FullRoute(startLocation.lat, startLocation.lon, currentRoute));

			displayRouteInstructions(currentRoute, startLocation.lat, startLocation.lon);

		} catch (Exception e) {
			routeStepsContainer.getChildren().clear();
			Label errorLabel = new Label("Invalid input. Choose a stop suggestion or enter coordinates.");
			errorLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
			routeStepsContainer.getChildren().add(errorLabel);
			e.printStackTrace();
		}
	}

	private VBox buildRouteSummary(List<RouteStep> steps, double startLat, double startLon) {
		double totalMinutes = 0.0;
		double totalMeters = 0.0;
		double previousLat = startLat;
		double previousLon = startLon;

		for (RouteStep step : steps) {
			totalMinutes += step.durationMinutes;
			totalMeters += GeoCalculator.calculateEquirectangularDistance(
					previousLat, previousLon,
					step.latTo, step.lonTo
			);
			previousLat = step.latTo;
			previousLon = step.lonTo;
		}

		VBox summaryCard = new VBox(4);
		summaryCard.getStyleClass().add("route-summary-card");

		Label title = new Label("Trip summary");
		title.getStyleClass().add("route-summary-title");

		Label totals = new Label(formatDuration(totalMinutes) + " | " + formatKilometers(totalMeters));
		totals.getStyleClass().add("route-summary-value");

		summaryCard.getChildren().addAll(title, totals);
		return summaryCard;
	}

	private String formatDuration(double totalMinutes) {
		int roundedMinutes = (int) Math.round(totalMinutes);
		if (roundedMinutes <= 0) {
			return "<1 min";
		}
		if (roundedMinutes < 60) {
			return roundedMinutes + " min";
		}

		int hours = roundedMinutes / 60;
		int minutes = roundedMinutes % 60;
		if (minutes == 0) {
			return hours + " hr";
		}
		return hours + " hr " + minutes + " min";
	}

	private String formatKilometers(double totalMeters) {
		return String.format(Locale.US, "%.1f km", totalMeters / 1000.0);
	}

	private void setupStopAutocomplete(TextField field, boolean isStartField) {
		ContextMenu suggestionsMenu = new ContextMenu();
		suggestionsMenu.getStyleClass().add("suggestions-menu");

		field.textProperty().addListener((observable, oldValue, newValue) -> {
			if (settingFieldProgrammatically) return;

			if (isStartField) {
				selectedStartStop = null;
			} else {
				selectedEndStop = null;
			}

			showStopSuggestions(field, suggestionsMenu, isStartField);
		});

		field.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
			if (!isFocused) {
				suggestionsMenu.hide();
			} else if (!field.getText().isBlank()) {
				showStopSuggestions(field, suggestionsMenu, isStartField);
			}
		});
	}

	private void showStopSuggestions(TextField field, ContextMenu suggestionsMenu, boolean isStartField) {
		List<Stop> suggestions = findStopSuggestions(field.getText());
		if (suggestions.isEmpty() || !field.isFocused()) {
			suggestionsMenu.hide();
			return;
		}

		suggestionsMenu.getItems().clear();
		for (Stop stop : suggestions) {
			Label label = new Label(stop.name);
			label.getStyleClass().add("suggestion-item");

			CustomMenuItem item = new CustomMenuItem(label, true);
			item.setOnAction(event -> {
				setFieldText(field, stop.name);
				if (isStartField) {
					selectedStartStop = stop;
					map.setStartMarker(stop.lat, stop.lon);
				} else {
					selectedEndStop = stop;
					map.setEndMarker(stop.lat, stop.lon);
				}
			});
			suggestionsMenu.getItems().add(item);
		}

		if (!suggestionsMenu.isShowing()) {
			suggestionsMenu.show(field, javafx.geometry.Side.BOTTOM, 0, 0);
		}
	}

	private void setFieldText(TextField field, String text) {
		settingFieldProgrammatically = true;
		field.setText(text);
		field.positionCaret(text.length());
		settingFieldProgrammatically = false;
	}

	private List<Stop> findStopSuggestions(String query) {
		String normalizedQuery = normalize(query);
		if (normalizedQuery.length() < 2) return List.of();

		List<Stop> prefixMatches = new ArrayList<>();
		List<Stop> containsMatches = new ArrayList<>();

		for (Stop stop : stops) {
			String normalizedName = normalize(stop.name);
			if (normalizedName.startsWith(normalizedQuery)) {
				prefixMatches.add(stop);
			} else if (normalizedName.contains(normalizedQuery)) {
				containsMatches.add(stop);
			}
		}

		List<Stop> suggestions = new ArrayList<>();
		addUniqueStops(suggestions, prefixMatches);
		addUniqueStops(suggestions, containsMatches);

		if (suggestions.size() > MAX_SUGGESTIONS) {
			return suggestions.subList(0, MAX_SUGGESTIONS);
		}
		return suggestions;
	}

	private void addUniqueStops(List<Stop> target, List<Stop> candidates) {
		for (Stop candidate : candidates) {
			boolean alreadyAdded = false;
			for (Stop existing : target) {
				if (existing.name.equalsIgnoreCase(candidate.name)) {
					alreadyAdded = true;
					break;
				}
			}
			if (!alreadyAdded) {
				target.add(candidate);
			}
			if (target.size() >= MAX_SUGGESTIONS) return;
		}
	}

	private Stop findExactStopByName(String name) {
		String normalizedName = normalize(name);
		for (Stop stop : stops) {
			if (normalize(stop.name).equals(normalizedName)) {
				return stop;
			}
		}
		return null;
	}

	private ResolvedLocation resolveLocation(String input, Stop selectedStop) {
		if (selectedStop != null && normalize(selectedStop.name).equals(normalize(input))) {
			return new ResolvedLocation(selectedStop.lat, selectedStop.lon);
		}

		Stop exactStop = findExactStopByName(input);
		if (exactStop != null) {
			return new ResolvedLocation(exactStop.lat, exactStop.lon);
		}

		List<Stop> suggestions = findStopSuggestions(input);
		if (!suggestions.isEmpty()) {
			Stop firstSuggestion = suggestions.get(0);
			return new ResolvedLocation(firstSuggestion.lat, firstSuggestion.lon);
		}

		String[] parts = input.split(",");
		if (parts.length != 2) {
			throw new IllegalArgumentException("Location must be a stop name or lat, lon pair.");
		}

		double lat = Double.parseDouble(parts[0].trim());
		double lon = Double.parseDouble(parts[1].trim());
		return new ResolvedLocation(lat, lon);
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}

	private static class ResolvedLocation {
		final double lat;
		final double lon;

		ResolvedLocation(double lat, double lon) {
			this.lat = lat;
			this.lon = lon;
		}
	}

	private static class ArrivalInfo {
		final int arrivalTimeSeconds;
		final String shortName;
		final String longName;
		final String headSign;

		ArrivalInfo(int arrivalTimeSeconds, String shortName, String longName, String headSign) {
			this.arrivalTimeSeconds = arrivalTimeSeconds;
			this.shortName = shortName == null ? "" : shortName;
			this.longName = longName == null ? "" : longName;
			this.headSign = headSign == null ? "" : headSign;
		}
	}
}
