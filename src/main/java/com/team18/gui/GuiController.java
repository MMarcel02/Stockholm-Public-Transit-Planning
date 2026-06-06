package com.team18.gui;

import com.team18.routing.raptor.RaptorNetwork;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.Router;
import com.team18.routing.raptor.RaptorAlgorithm;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.stage.Stage;
import javafx.scene.layout.Pane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import com.team18.parser.GTFSParser;
import com.team18.model.RouteStep;

import java.util.List;

public class GuiController {
	public Stage stage;

	@FXML Pane mapContainer;
	@FXML TextField startField;
	@FXML TextField endField;
	@FXML TextField timeField;
	@FXML VBox routeStepsContainer;

	RaptorNetwork network;

	JourneyInput journeyInput;
	LayerStack stack;
	RouteDisplay routeDisplay;

	@FXML
	public void initialize() {
		GTFSParser parser = null;
		try {
			System.err.println("Loading GTFS data...");
			parser = new GTFSParser();
			parser.loadFromZip("data/stockholm/sl_center.zip");

			System.err.println("Building RAPTOR Network...");
			RaptorBuilder builder = new RaptorBuilder();
			network = builder.build(parser.agencies, parser.stops, parser.routes,
					parser.trips);

			System.err.println("Network Ready! Drawing stops on map...");
		} catch (Exception e) {
			System.err.println("Failed to load GTFS/Raptor data: " + e.getMessage());
			e.printStackTrace();

			System.exit(1);
		}

		journeyInput = new JourneyInput(parser, startField, endField, timeField);

		stack = new LayerStack(parser, network, journeyInput);
		mapContainer.getChildren().add(stack.getGroup());

		routeDisplay = new RouteDisplay(routeStepsContainer);

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
			int startTimeSeconds = journeyInput.getTimeInSeconds();

			Router raptorAlgorithm = new RaptorAlgorithm(network);

			List<RouteStep> route = raptorAlgorithm.getFastestTrip(
				startLocation[0], startLocation[1],
				endLocation[0], endLocation[1],
				startTimeSeconds
			);

			//stack.navLayer.setStartMarker(startLocation.lat, startLocation.lon);
			//stack.navLayer.setEndMarker(endLocation.lat, endLocation.lon);

			stack.navLayer.navigate(route);
			routeDisplay.display(route);
		} catch (Exception e) {
			routeDisplay.displayInvalidInput();
		}
	}

	@FXML
	public void handleGenerateHeatmap() {
		try {
			double[] origin = journeyInput.resolveStart();
			int startTimeSeconds = journeyInput.getTimeInSeconds();

			//navLayer.setStartMarker(origin.lat, origin.lon);

			stack.heatmapLayer.configure(origin[0], origin[1], startTimeSeconds);
		} catch (Exception e) {
			routeDisplay.displayInvalidInput();
		}
	}
}

