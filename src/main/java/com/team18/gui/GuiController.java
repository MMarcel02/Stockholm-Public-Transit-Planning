package com.team18.gui;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

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

import com.team18.parser.GTFSParser;
import com.team18.model.RouteStep;
import com.team18.gui.NavigationLayer.DrawnRoute;

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
}

