package com.team18.gui;

import com.team18.routing.raptor.RaptorNetwork;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.Router;
import com.team18.routing.raptor.RaptorAlgorithm;

import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import com.team18.parser.GTFSParser;
import com.team18.model.RouteStep;

import java.util.List;

public class GuiController {
	@FXML private Pane mapContainer;
	@FXML private TextField startField;
	@FXML private TextField endField;
	@FXML private TextField timeField;
	@FXML private VBox routeStepsContainer;

	private RaptorNetwork network;

	StopHoverCard stopHoverCard = new StopHoverCard();
	JourneyInput journeyInput;
	LayerStack stack;

	@FXML
	public void initialize() {
		GTFSParser parser = null;
		try {
			System.err.println("Loading GTFS data...");
			parser = new GTFSParser();
			parser.loadFromZip("data/stockholm/sl_center.zip");
			buildStopArrivalIndex(parser);

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
		mapContainer.getChildren.add(stack.getGroup());

		routeDisplay = new RouteDisplay(routeStepsContainer);
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

			stack.navLayer.setStartMarker(startLocation.lat, startLocation.lon);
			stack.navLayer.setEndMarker(endLocation.lat, endLocation.lon);
			stack.navLayer.navigate(currentRoute);

			routeDisplay.display(currentRoute);
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

