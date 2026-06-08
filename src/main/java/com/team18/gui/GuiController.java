package com.team18.gui;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Collections;
import java.util.Comparator;
import java.io.IOException;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

import com.team18.routing.raptor.RaptorNetwork;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.Router;
import com.team18.routing.raptor.RaptorAlgorithm;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.scene.layout.Pane;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import javafx.util.converter.LocalDateStringConverter;
import javafx.scene.paint.Color;

import com.team18.parser.GTFSParser;
import com.team18.parser.CSVParser;
import com.team18.parser.CSVParser.Row;
import com.team18.model.RouteStep;
import com.team18.gui.NavigationLayer.DrawnRoute;
import com.team18.gui.HeatmapLayer;
import com.team18.model.RouteStep;
import com.team18.model.Route;
import com.team18.model.Trip;
import com.team18.model.Stop;
import com.team18.model.StopTime;
import com.team18.optimizer.Optimizer;
import com.team18.parser.PopdistParser;
import com.team18.optimizer.Config;

import java.util.List;

public class GuiController {
	public Stage stage;

	@FXML Pane mapContainer;
	@FXML TextField startField;
	@FXML TextField endField;
	@FXML TextField timeField;
	@FXML DatePicker datePicker;
	@FXML VBox routeStepsContainer;
	@FXML VBox stopCardPlaceholder;

	@FXML CheckBox hideDisabled;
	@FXML CheckBox hideEnabled;
	@FXML CheckBox differenceMode;

	@FXML TextField removalCutoff;

	RaptorNetwork network;
	RaptorAlgorithm raptorAlgorithm;

	JourneyInput journeyInput;
	LayerStack stack;
	RouteDisplay routeDisplay;
	StopHoverCard stopHoverCard;

	DrawnRoute drawnRoute = null;

	List<DrawnRoute> optimizerRoutes = new ArrayList<>();

	@FXML
	public void initialize() {
		GTFSParser parser = null;
		try {
			System.err.println("Loading GTFS data...");
			parser = new GTFSParser();
			parser.loadFromZip("data/stockholm/sl_center.zip");

			System.err.println("Building RAPTOR Network...");
			RaptorBuilder builder = new RaptorBuilder();
			network = builder.build(parser.agencies, parser.stops, parser.routes, parser.trips, parser.serviceByCalendar);
			raptorAlgorithm = new RaptorAlgorithm(network);

			System.err.println("Network Ready! Drawing stops on map...");
		} catch (Exception e) {
			System.err.println("Failed to load GTFS/Raptor data: " + e.getMessage());
			e.printStackTrace();

			System.exit(1);
		}

		datePicker.setValue(LocalDate.now());

		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		datePicker.setConverter(new LocalDateStringConverter(dateFormatter, dateFormatter));

		datePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
    		network.setByCalendar(newDate);
			stack.stopLayer.update(); 
		});

        timeField.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
		journeyInput = new JourneyInput(parser, startField, endField, timeField, datePicker);

		stack = new LayerStack(parser, network, journeyInput);
		mapContainer.getChildren().add(stack.getGroup());

		routeDisplay = new RouteDisplay(routeStepsContainer);

		stopHoverCard = new StopHoverCard(journeyInput, network, parser);
		stopHoverCard.setOnToggleCallback( () -> {
			stack.render(mapContainer.getWidth(), mapContainer.getHeight());
		});
		stopCardPlaceholder.getChildren().add(stopHoverCard.getVBox());
		stack.stopLayer.setHoverCard(stopHoverCard);

		mapContainer.widthProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> obs,
					Number oldWidth, Number newWidth) {
				stack.render(mapContainer.getWidth(), mapContainer.getHeight());
			}
		});

		mapContainer.heightProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> obs,
					Number oldHeight, Number newHeight) {
				stack.render(mapContainer.getWidth(), mapContainer.getHeight());
			}
		});

		mapContainer.setOnMousePressed(ev -> stack.mousePressed(ev));
		mapContainer.setOnMouseClicked(ev -> stack.mouseClicked(ev));
		mapContainer.setOnMouseMoved(ev -> stack.mouseMoved(ev));
		mapContainer.setOnMouseExited(ev -> stack.mouseExited(ev));
		mapContainer.setOnMouseDragged(ev -> stack.mouseDragged(ev));

		hideDisabled.setOnAction(ev ->
				stack.stopLayer.setHideDisabled(hideDisabled.isSelected()));
		hideEnabled.setOnAction(ev ->
				stack.stopLayer.setHideEnabled(hideEnabled.isSelected()));

		removalCutoff.setText("3");
	}

	@FXML
	public void handlePlanJourney() {
		try {
			double[] startLocation = journeyInput.resolveStart();
			double[] endLocation = journeyInput.resolveEnd();
			int startTimeSeconds = journeyInput.getEffectiveTimeInSeconds();
			LocalDate selectedDate = journeyInput.getEffectiveDate();

			List<RouteStep> route = raptorAlgorithm.getFastestTrip(
				startLocation[0], startLocation[1],
				endLocation[0], endLocation[1],
				startTimeSeconds
			);

			if (drawnRoute != null) stack.navLayer.remove(drawnRoute);
			drawnRoute = new DrawnRoute(route);
			stack.navLayer.add(drawnRoute);

			routeDisplay.display(route);
		} catch (Exception e) {
			routeDisplay.displayInvalidInput();
		}
	}

	@FXML
	public void handleClearJourney() {
		stack.navLayer.remove(drawnRoute);
		drawnRoute = null;
		routeDisplay.clear();
	}

	@FXML
	public void handleGenerateHeatmap() {
		try {
			double[] origin = journeyInput.resolveStart();
			int startTimeSeconds = journeyInput.getEffectiveTimeInSeconds();

			stack.heatmapLayer.configureDelayMode(origin[0], origin[1],
					startTimeSeconds, differenceMode.isSelected());
		} catch (Exception e) {
			routeDisplay.displayInvalidInput();
		}
	}

	@FXML
	public void handleGeneratePopHeatmap() {
		try {
			FileReader reader = new FileReader("data/stockholm/population.csv");
			CSVParser csvp = new CSVParser(new BufferedReader(reader));

			List<HeatmapLayer.Point> points = new ArrayList<>();

			Row row;
			while ((row = csvp.nextRow()) != null) {
				double lat = Double.parseDouble(row.getCol("lat"));
				double lon = Double.parseDouble(row.getCol("lon"));
				double pop = Double.parseDouble(row.getCol("population"));

				points.add(new HeatmapLayer.Point(
					lat + PopdistParser.CELL_SIZE_LAT/2,
					lon - PopdistParser.CELL_SIZE_LON/2,
					pop / 10
				));
			}

			stack.heatmapLayer.configureManual(points,
					PopdistParser.CELL_SIZE_LAT,
					PopdistParser.CELL_SIZE_LON);
		} catch (Exception e) {
			routeDisplay.displayInvalidInput();
		}
	}

	@FXML
	public void handleHideHeatmap() {
		stack.heatmapLayer.clear();
		stack.render(mapContainer.getWidth(), mapContainer.getHeight());
	}


	@FXML
	public void handleRemovalCosts() {
		for (DrawnRoute dr: optimizerRoutes) {
			stack.navLayer.remove(dr);
		}

		optimizerRoutes.clear();

		try {
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Choose optimizer results file");
			chooser.getExtensionFilters().add(
					new ExtensionFilter("CSV Files", "*.csv"));
			File file = chooser.showOpenDialog(stage);
			if (file == null) return;

			FileReader reader = new FileReader(file);
			CSVParser costp = new CSVParser(new BufferedReader(reader));

			Map<String, Double> costs = new HashMap<>();

			Row row;
			while ((row = costp.nextRow()) != null) {
				String routeId = row.getCol("routeId");
				double cost = Double.parseDouble(row.getCol("cost"));
				costs.put(routeId, cost);
			}

			Set<Map.Entry<String, Double>> entrySet = costs.entrySet();

			List<Map.Entry<String, Double>> entries = new ArrayList<>();
			entries.addAll(entrySet);
			entries.sort(Comparator.comparingDouble(e -> e.getValue()));

			int maxEntries = entries.size();

			if (!removalCutoff.getText().isBlank()) {
				int cutoff = Integer.parseInt(removalCutoff.getText());
				if (cutoff != 0) maxEntries = Math.min(cutoff, maxEntries);
			}

			for (int entryIndex = 0;
					entryIndex <= maxEntries;
					entryIndex++) {
				Map.Entry<String, Double> entry = entries.get(entryIndex);
				String routeId = entry.getKey();
				Route route = network.parentRouteLookup.get(routeId);

				Trip trip = route.trips.get(0);

				List<NavigationLayer.Step> steps = new ArrayList<>();
				if (trip.stopTimes.size() == 0) continue;

				double latFrom = trip.stopTimes.get(0).stop.lat;
				double lonFrom = trip.stopTimes.get(0).stop.lon;

				for (int i = 1; i < trip.stopTimes.size(); i++) {
					StopTime time = trip.stopTimes.get(i);
					Stop stop = time.stop;

					steps.add(new NavigationLayer.Step(
						latFrom, lonFrom,
						stop.lat, stop.lon,
						trip.shapeId
					));

					latFrom = stop.lat;
					lonFrom = stop.lon;
				}


				double[] thresholds = {-1000, 0, 500, 1000, 5000, 10000, 20000, 50000};

				String[] colors = {
					"#00FF2A", "#24DB24", "#49B61E", "#6D9218",
					"#926D12", "#B6490C", "#DB2406", "#FF0000"
				};

				Color color =
					interpolatePalette(entry.getValue(), thresholds, colors, 1);

				DrawnRoute dr = new DrawnRoute(steps, color);
				optimizerRoutes.add(dr);
				stack.navLayer.add(dr);
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	@FXML
	public void handleHideRemovalCosts() {
		for (DrawnRoute dr: optimizerRoutes) {
			stack.navLayer.remove(dr);
		}

		optimizerRoutes.clear();
	}

	private Color interpolatePalette(double value, double[] thresholds,
			String[] colors, double opacity) {
		if (value <= thresholds[0]) {
			return Color.web(colors[0], opacity);
		}

		for (int i = 1; i < thresholds.length; i++) {
			if (value <= thresholds[i]) {
				double ratio =
					(value - thresholds[i - 1]) / (thresholds[i] - thresholds[i - 1]);

				Color start = Color.web(colors[i - 1]);
				Color end = Color.web(colors[i]);

				return start.interpolate(end, ratio).deriveColor(0, 1, 1, opacity);
			}
		}

		return Color.web(colors[colors.length - 1], opacity);
	}


	@FXML
	public void zoomIn() {
		stack.zoom(1);
	}

	@FXML
	public void zoomOut() {
		stack.zoom(-1);
	}
}

