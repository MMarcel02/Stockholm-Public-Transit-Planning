package com.team18.gui;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.io.IOException;

import com.team18.routing.raptor.RaptorNetwork;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.Router;
import com.team18.routing.raptor.RaptorAlgorithm;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.stage.Stage;
import javafx.scene.layout.Pane;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.converter.LocalDateStringConverter;
import javafx.scene.paint.Color;

import com.team18.parser.GTFSParser;
import com.team18.model.RouteStep;
import com.team18.gui.NavigationLayer.DrawnRoute;
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

	RaptorNetwork network;
	RaptorAlgorithm raptorAlgorithm;

	JourneyInput journeyInput;
	LayerStack stack;
	RouteDisplay routeDisplay;
	StopHoverCard stopHoverCard;

	DrawnRoute drawnRoute = null;

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

        timeField.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
		journeyInput = new JourneyInput(parser, startField, endField, timeField, datePicker);

		stack = new LayerStack(parser, network, journeyInput);
		mapContainer.getChildren().add(stack.getGroup());

		routeDisplay = new RouteDisplay(routeStepsContainer);

		stopHoverCard = new StopHoverCard(journeyInput, network);
		stopHoverCard.setOnToggleCallback( () -> {
			stack.render(mapContainer.getWidth(), mapContainer.getHeight());
		});
		mapContainer.getChildren().add(stopHoverCard.getVBox());
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
	}

	@FXML
	public void handlePlanJourney() {
		try {
			double[] startLocation = journeyInput.resolveStart();
			double[] endLocation = journeyInput.resolveEnd();
			int startTimeSeconds = journeyInput.getEffectiveTimeInSeconds();
			LocalDate selectedDate = journeyInput.getEffectiveDate();

			network.setByCalendar(selectedDate);

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
	public void handleGenerateHeatmap() {
		try {
			double[] origin = journeyInput.resolveStart();
			int startTimeSeconds = journeyInput.getEffectiveTimeInSeconds();
			LocalDate selectedDate = journeyInput.getEffectiveDate();

			network.setByCalendar(selectedDate);

			stack.heatmapLayer.configureDelayMode(origin[0], origin[1],
					startTimeSeconds);
		} catch (Exception e) {
			routeDisplay.displayInvalidInput();
		}
	}

	@FXML
	public void handleRemovalCosts() {
		try {
			PopdistParser popd = new PopdistParser();
			popd.loadFromCsv("data/stockholm/population.csv");

			Optimizer optimizer = new Optimizer(network, popd.demandPointCoordinates,
					popd.demand);

			Map<String, Double> costs = optimizer.getRouteRemovedToCostImpact(
					Config.REPRESENTATIVE_WEEKDAY);

			for (String routeId: costs.keySet()) {
				Route route = network.parentRouteLookup.get(routeId);

				for (Trip trip: route.trips) {
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


					double[] thresholds = {0, 0.2, 0.5, 1, 2, 3, 6, 10};

					String[] colors = {
						"#0B5D1E", "#2E7D32", "#8BC34A", "#FDD835",
						"#FB8C00", "#EF9A9A", "#E53935", "#8E0000"
					};

					Color color =
						interpolatePalette(costs.get(routeId), thresholds, colors, 0.48);

					stack.navLayer.add(new DrawnRoute(steps, color));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
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

