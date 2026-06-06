package com.team18.gui;

import com.team18.routing.raptor.RaptorNetwork;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.Router;
import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.util.GeoCalculator;
import com.team18.util.ParsingUtil;
import com.team18.util.StockholmUrbanArea;

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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class GuiController {
	private static final int MAX_SUGGESTIONS = 10;
	private static final double STOP_HOVER_CARD_WIDTH = 340;
	private static final double STOP_HOVER_CARD_HEIGHT = 390;
	private static final double HEATMAP_CELL_SIZE_METERS = 100.0;
	private static final int HEATMAP_KD_LEAF_SIZE = 16;
	private static final int HEATMAP_GRID_COLUMNS = calculateHeatmapGridColumns();
	private static final int HEATMAP_GRID_ROWS = calculateHeatmapGridRows();
	private static final double WALK_SPEED_MPS = 50.0 / 36.0;
	private static final double EARTH_RADIUS_METERS = 6371000.0;

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
	private ResolvedLocation lastHeatmapOrigin;
	private int lastHeatmapStartTimeSeconds = -1;
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
			positionStopHoverCard(mouseX, mouseY);
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
			if (lastHeatmapOrigin != null) {
				generateHeatmap(lastHeatmapOrigin, lastHeatmapStartTimeSeconds);
			}
			
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
		double cardWidth = Math.max(stopHoverCard.getWidth(), STOP_HOVER_CARD_WIDTH);
		double cardHeight = Math.max(stopHoverCard.getHeight(), STOP_HOVER_CARD_HEIGHT);
		double margin = 8;

		double maxX = Math.max(margin, mapContainer.getWidth() - cardWidth - margin);
		double maxY = Math.max(margin, mapContainer.getHeight() - cardHeight - margin);
		double x = Math.min(Math.max(mouseX, margin), maxX);
		double y = Math.min(Math.max(mouseY, margin), maxY);

		stopHoverCard.relocate(x, y);
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
		int hours = (secondsAfterMidnight / 3600) % 24;
		int minutes = (secondsAfterMidnight % 3600) / 60;
		String dayMarker = (secondsAfterMidnight >= 86400) ? " (+1)" : "";

		return String.format(Locale.US, "%02d:%02d%s", hours, minutes, dayMarker);
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
			if (time == null || !time.matches("^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$")) {
				routeStepsContainer.getChildren().clear();
				Label errorLabel = new Label("Invalid time. Please use HH:MM (00:00 - 23:59).");
				errorLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
				routeStepsContainer.getChildren().add(errorLabel);
				return;
			}

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

	@FXML
	public void handleGenerateHeatmap() {
		if (raptorNetwork == null) {
			showStatus("Network still loading... Please wait.", true);
			return;
		}

		try {
			String timeText = timeField.getText();
			if (timeText == null || !timeText.matches("^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$")) {
				showStatus("Invalid time. Please use HH:MM (00:00 - 23:59).", true);
				return;
			}
			ResolvedLocation origin = resolveLocation(startField.getText(), selectedStartStop);
			int startTimeSeconds = ParsingUtil.timeStringToSecondsAfterMidnight(timeText);

			lastHeatmapOrigin = origin;
			lastHeatmapStartTimeSeconds = startTimeSeconds;
			map.setStartMarker(origin.lat, origin.lon);
			generateHeatmap(origin, startTimeSeconds);
		} catch (Exception e) {
			showStatus("Invalid heatmap input. Choose a start stop or enter coordinates, then enter HH:MM time.", true);
			e.printStackTrace();
		}
	}

	private void generateHeatmap(ResolvedLocation origin, int startTimeSeconds) {
		long startedAt = System.nanoTime();
		boolean differenceMode = hasDisabledStops();

		try {
			int[] currentTimes = new RaptorAlgorithm(raptorNetwork)
					.getTravelTimesToStops(origin.lat, origin.lon, startTimeSeconds);
			List<Map.HeatmapPoint> points;

			if (differenceMode) {
				boolean[] disabledState = Arrays.copyOf(raptorNetwork.stopsEnabledArr, raptorNetwork.stopsEnabledArr.length);
				int[] baselineTimes;

				try {
					Arrays.fill(raptorNetwork.stopsEnabledArr, true);
					baselineTimes = new RaptorAlgorithm(raptorNetwork)
							.getTravelTimesToStops(origin.lat, origin.lon, startTimeSeconds);
				} finally {
					System.arraycopy(disabledState, 0, raptorNetwork.stopsEnabledArr, 0, disabledState.length);
				}

				points = buildDifferenceHeatmapPoints(currentTimes, baselineTimes, origin);
			} else {
				points = buildTravelTimeHeatmapPoints(currentTimes, origin);
			}

			map.setHeatmap(points, differenceMode, getHeatmapCellLatSpan(), getHeatmapCellLonSpan());
			double elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
			String mode = differenceMode ? "Stop removal impact heatmap" : "Journey time heatmap";
			showStatus(String.format(Locale.US, "%s generated: %d cells at %.0fm resolution in %.1fs.",
					mode, points.size(), HEATMAP_CELL_SIZE_METERS, elapsedSeconds), false);
		} catch (Exception e) {
			showStatus("Failed to generate heatmap: " + e.getMessage(), true);
			e.printStackTrace();
		}
	}

	private List<Map.HeatmapPoint> buildTravelTimeHeatmapPoints(int[] travelTimes, ResolvedLocation origin) {
		List<Map.HeatmapPoint> points = new ArrayList<>(HEATMAP_GRID_ROWS * HEATMAP_GRID_COLUMNS);
		HeatmapEstimator estimator = new HeatmapEstimator(origin, travelTimes, raptorNetwork.stopLookup);
		double cellLatSpan = getHeatmapCellLatSpan();
		double cellLonSpan = getHeatmapCellLonSpan();

		for (int row = 0; row < HEATMAP_GRID_ROWS; row++) {
			double lat = StockholmUrbanArea.OUTER_MAX_LAT - ((row + 0.5) * cellLatSpan);
			for (int col = 0; col < HEATMAP_GRID_COLUMNS; col++) {
				double lon = StockholmUrbanArea.OUTER_MIN_LON + ((col + 0.5) * cellLonSpan);
				int estimatedSeconds = estimator.estimateTravelTimeToPoint(lat, lon);
				points.add(new Map.HeatmapPoint(lat, lon, estimatedSeconds / 60.0));
			}
		}

		return points;
	}

	private List<Map.HeatmapPoint> buildDifferenceHeatmapPoints(int[] currentTimes, int[] baselineTimes, ResolvedLocation origin) {
		List<Map.HeatmapPoint> points = new ArrayList<>(HEATMAP_GRID_ROWS * HEATMAP_GRID_COLUMNS);
		HeatmapEstimator currentEstimator = new HeatmapEstimator(origin, currentTimes, raptorNetwork.stopLookup);
		HeatmapEstimator baselineEstimator = new HeatmapEstimator(origin, baselineTimes, raptorNetwork.stopLookup);
		double cellLatSpan = getHeatmapCellLatSpan();
		double cellLonSpan = getHeatmapCellLonSpan();

		for (int row = 0; row < HEATMAP_GRID_ROWS; row++) {
			double lat = StockholmUrbanArea.OUTER_MAX_LAT - ((row + 0.5) * cellLatSpan);
			for (int col = 0; col < HEATMAP_GRID_COLUMNS; col++) {
				double lon = StockholmUrbanArea.OUTER_MIN_LON + ((col + 0.5) * cellLonSpan);
				int currentSeconds = currentEstimator.estimateTravelTimeToPoint(lat, lon);
				int baselineSeconds = baselineEstimator.estimateTravelTimeToPoint(lat, lon);
				double delayMinutes = Math.max(0.0, (currentSeconds - baselineSeconds) / 60.0);
				points.add(new Map.HeatmapPoint(lat, lon, delayMinutes));
			}
		}

		return points;
	}

	private double getHeatmapCellLatSpan() {
		return (StockholmUrbanArea.OUTER_MAX_LAT - StockholmUrbanArea.OUTER_MIN_LAT) / HEATMAP_GRID_ROWS;
	}

	private double getHeatmapCellLonSpan() {
		return (StockholmUrbanArea.OUTER_MAX_LON - StockholmUrbanArea.OUTER_MIN_LON) / HEATMAP_GRID_COLUMNS;
	}

	private static int calculateHeatmapGridRows() {
		double centerLon = (StockholmUrbanArea.OUTER_MIN_LON + StockholmUrbanArea.OUTER_MAX_LON) / 2.0;
		double heightMeters = GeoCalculator.calculateEquirectangularDistance(
				StockholmUrbanArea.OUTER_MIN_LAT,
				centerLon,
				StockholmUrbanArea.OUTER_MAX_LAT,
				centerLon
		);
		return (int) Math.ceil(heightMeters / HEATMAP_CELL_SIZE_METERS);
	}

	private static int calculateHeatmapGridColumns() {
		double centerLat = (StockholmUrbanArea.OUTER_MIN_LAT + StockholmUrbanArea.OUTER_MAX_LAT) / 2.0;
		double widthMeters = GeoCalculator.calculateEquirectangularDistance(
				centerLat,
				StockholmUrbanArea.OUTER_MIN_LON,
				centerLat,
				StockholmUrbanArea.OUTER_MAX_LON
		);
		return (int) Math.ceil(widthMeters / HEATMAP_CELL_SIZE_METERS);
	}

	private boolean hasDisabledStops() {
		for (boolean enabled : raptorNetwork.stopsEnabledArr) {
			if (!enabled) return true;
		}
		return false;
	}

	private void showStatus(String message, boolean error) {
		routeStepsContainer.getChildren().clear();
		Label statusLabel = new Label(message);
		statusLabel.setWrapText(true);
		statusLabel.setStyle(error ? "-fx-text-fill: red; -fx-font-weight: bold;" : "-fx-text-fill: #1f2d3d; -fx-font-weight: bold;");
		routeStepsContainer.getChildren().add(statusLabel);
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

	private static class HeatmapEstimator {
		private final double originLat;
		private final double originLon;
		private final HeatmapKdNode root;

		HeatmapEstimator(ResolvedLocation origin, int[] travelTimes, Stop[] stopLookup) {
			this.originLat = origin.lat;
			this.originLon = origin.lon;

			List<HeatmapCandidate> candidates = new ArrayList<>();
			for (int i = 0; i < travelTimes.length; i++) {
				if (travelTimes[i] == Integer.MAX_VALUE) continue;

				Stop stop = stopLookup[i];
				candidates.add(new HeatmapCandidate(stop.lat, stop.lon, travelTimes[i]));
			}

			HeatmapCandidate[] candidateArray = candidates.toArray(new HeatmapCandidate[0]);
			this.root = candidateArray.length == 0
					? null
					: new HeatmapKdNode(candidateArray, 0, candidateArray.length);
		}

		int estimateTravelTimeToPoint(double lat, double lon) {
			double directWalkDistance = GeoCalculator.calculateEquirectangularDistance(originLat, originLon, lat, lon);
			int bestSeconds = (int) Math.round(directWalkDistance / WALK_SPEED_MPS);
			return root == null ? bestSeconds : root.estimateTravelTimeToPoint(lat, lon, bestSeconds);
		}
	}

	private static class HeatmapKdNode {
		private final double minLat;
		private final double maxLat;
		private final double minLon;
		private final double maxLon;
		private final int minTravelSeconds;
		private final HeatmapCandidate[] candidates;
		private final HeatmapKdNode left;
		private final HeatmapKdNode right;

		HeatmapKdNode(HeatmapCandidate[] points, int start, int end) {
			double nodeMinLat = Double.POSITIVE_INFINITY;
			double nodeMaxLat = Double.NEGATIVE_INFINITY;
			double nodeMinLon = Double.POSITIVE_INFINITY;
			double nodeMaxLon = Double.NEGATIVE_INFINITY;
			int nodeMinTravelSeconds = Integer.MAX_VALUE;

			for (int i = start; i < end; i++) {
				HeatmapCandidate point = points[i];
				nodeMinLat = Math.min(nodeMinLat, point.lat);
				nodeMaxLat = Math.max(nodeMaxLat, point.lat);
				nodeMinLon = Math.min(nodeMinLon, point.lon);
				nodeMaxLon = Math.max(nodeMaxLon, point.lon);
				nodeMinTravelSeconds = Math.min(nodeMinTravelSeconds, point.travelSeconds);
			}

			this.minLat = nodeMinLat;
			this.maxLat = nodeMaxLat;
			this.minLon = nodeMinLon;
			this.maxLon = nodeMaxLon;
			this.minTravelSeconds = nodeMinTravelSeconds;

			if (end - start <= HEATMAP_KD_LEAF_SIZE) {
				this.candidates = Arrays.copyOfRange(points, start, end);
				this.left = null;
				this.right = null;
				return;
			}

			boolean splitByLat = (maxLat - minLat) >= (maxLon - minLon);
			Arrays.sort(points, start, end, splitByLat
					? Comparator.comparingDouble(candidate -> candidate.lat)
					: Comparator.comparingDouble(candidate -> candidate.lon));

			int midpoint = start + ((end - start) / 2);
			this.candidates = null;
			this.left = new HeatmapKdNode(points, start, midpoint);
			this.right = new HeatmapKdNode(points, midpoint, end);
		}

		int estimateTravelTimeToPoint(double lat, double lon, int bestSeconds) {
			if (lowerBoundSeconds(lat, lon) >= bestSeconds) {
				return bestSeconds;
			}

			if (candidates != null) {
				for (HeatmapCandidate candidate : candidates) {
					if (candidate.travelSeconds >= bestSeconds) continue;

					double walkDistance = GeoCalculator.calculateEquirectangularDistance(candidate.lat, candidate.lon, lat, lon);
					int totalSeconds = candidate.travelSeconds + (int) Math.round(walkDistance / WALK_SPEED_MPS);
					if (totalSeconds < bestSeconds) {
						bestSeconds = totalSeconds;
					}
				}
				return bestSeconds;
			}

			double leftLowerBound = left.lowerBoundSeconds(lat, lon);
			double rightLowerBound = right.lowerBoundSeconds(lat, lon);

			if (leftLowerBound <= rightLowerBound) {
				bestSeconds = left.estimateTravelTimeToPoint(lat, lon, bestSeconds);
				bestSeconds = right.estimateTravelTimeToPoint(lat, lon, bestSeconds);
			} else {
				bestSeconds = right.estimateTravelTimeToPoint(lat, lon, bestSeconds);
				bestSeconds = left.estimateTravelTimeToPoint(lat, lon, bestSeconds);
			}

			return bestSeconds;
		}

		private double lowerBoundSeconds(double lat, double lon) {
			return minTravelSeconds + (minimumDistanceToBoundsMeters(lat, lon) / WALK_SPEED_MPS);
		}

		private double minimumDistanceToBoundsMeters(double lat, double lon) {
			double latDistance = 0.0;
			if (lat < minLat) {
				latDistance = Math.toRadians(minLat - lat) * EARTH_RADIUS_METERS;
			} else if (lat > maxLat) {
				latDistance = Math.toRadians(lat - maxLat) * EARTH_RADIUS_METERS;
			}

			double lonDistance = 0.0;
			if (lon < minLon) {
				lonDistance = conservativeLongitudeDistanceMeters(lat, minLat, maxLat, minLon - lon);
			} else if (lon > maxLon) {
				lonDistance = conservativeLongitudeDistanceMeters(lat, minLat, maxLat, lon - maxLon);
			}

			return Math.max(latDistance, lonDistance);
		}

		private double conservativeLongitudeDistanceMeters(double queryLat, double boundsMinLat, double boundsMaxLat, double deltaLonDegrees) {
			double maxAbsLat = Math.max(Math.abs(queryLat), Math.max(Math.abs(boundsMinLat), Math.abs(boundsMaxLat)));
			return Math.toRadians(deltaLonDegrees) * EARTH_RADIUS_METERS * Math.cos(Math.toRadians(maxAbsLat));
		}
	}

	private static class HeatmapCandidate {
		final double lat;
		final double lon;
		final int travelSeconds;

		HeatmapCandidate(double lat, double lon, int travelSeconds) {
			this.lat = lat;
			this.lon = lon;
			this.travelSeconds = travelSeconds;
		}
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
