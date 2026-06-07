package com.team18.gui;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.input.MouseEvent;

import com.team18.parser.GTFSParser;
import com.team18.model.Stop;
import com.team18.model.Trip;
import com.team18.model.StopTime;
import com.team18.routing.raptor.RaptorNetwork;
import com.team18.util.GeoCalculator;
import com.team18.util.ParsingUtil;

public class StopLayer implements Layer {
	static final double CLICK_RADIUS = 10;

	static final double HOVER_CARD_WIDTH = 340;
	static final double HOVER_CARD_HEIGHT = 390;

	boolean settingStart = true;

	Canvas canvas = new Canvas();
	Group group = new Group();

	GTFSParser parser;
	JourneyInput journeyInput;
	RaptorNetwork network;

	StopHoverCard hoverCard;

	double viewX = 0;
	double viewY = 0;
	double viewWidth = 0;
	double viewHeight = 0;

	Map<String, List<Arrival>> arrivalMap = new HashMap<>();

	boolean hideDisabled = false;
	boolean hideEnabled = false;

	static class Arrival {
		final int timeSeconds;
		final String shortName;
		final String longName;
		final String headSign;

		Arrival(int timeSeconds, String shortName, String longName, String headSign) {
			this.timeSeconds = timeSeconds;
			this.shortName = shortName;
			this.longName = longName;
			this.headSign = headSign;
		}

		public String toString() {
			String route = shortName;
			if (route.isBlank()) route = longName;
			if (route.isBlank()) route = "Route";

			String direction = "";
			if (!direction.isBlank()) direction = " to " + headSign;

			int hours = timeSeconds / 3600;
			int mins = (timeSeconds % 3600) / 60;

			return String.format(Locale.US, "%02d:%02d", hours, mins);
		}
	}

	public StopLayer(GTFSParser parser, RaptorNetwork network,
			JourneyInput journeyInput) {
		group.getChildren().add(canvas);
		this.network = network;
		this.parser = parser;
		this.journeyInput = journeyInput;

		buildArrivalMap();
	}

	public void setHoverCard(StopHoverCard card) {
		hoverCard = card;
	}

	public void setHideDisabled(boolean disabled) {
		hideDisabled = disabled;
		update();
	}

	public void setHideEnabled(boolean enabled) {
		hideEnabled = enabled;
		update();
	}

	public void shift(double x, double y) {
		viewX = x;
		viewY = y;

		update();
	}

	public void render(double width, double height) {
		viewWidth = width;
		viewHeight = height;

		update();
	}

	void update() {
		double width = viewWidth + Tile.RESOLUTION * 2;
		double height = viewHeight + Tile.RESOLUTION * 2;

		double minX = -viewX - Tile.RESOLUTION;
		double minY = -viewY - Tile.RESOLUTION;
		double maxX = minX + width;
		double maxY = minY + height;

		canvas.setTranslateX(minX);
		canvas.setTranslateY(minY);
		canvas.setWidth(width);
		canvas.setHeight(height);

		GraphicsContext gc = canvas.getGraphicsContext2D();
		gc.clearRect(0, 0, width, height);

		double radius = 4.5;
		if (CoordSystem.getZoomLevel() >= 15) radius = 6;


		gc.setStroke(Color.WHITE);
		gc.setLineWidth(1);

		Set<String> alreadyDrawn = new HashSet<>();

		for (Stop stop: parser.stops.values()) {
			if (alreadyDrawn.contains(stop.name)) continue;
			alreadyDrawn.add(stop.name);

			int internalID = network.stopStringToIntMap.get(stop.id);
			if (network.stopsEnabledArr[internalID]) {
				if (hideEnabled) continue;
				gc.setFill(Color.web("#09dbd1", 0.8)); // cyan if active
			} else {
				if (hideDisabled) continue;
				gc.setFill(Color.web("#e31c0e", 0.8)); // else red
			}
			double[] local = CoordSystem.getLocalFromLatLon(stop.lat, stop.lon);

			if (local[0] < minX
					|| local[0] > maxX
					|| local[1] < minY
					|| local[1] > maxY) {
				continue;
			}

			double canvasX = local[0] - minX;
			double canvasY = local[1] - minY;

			double diameter = radius * 2;

			gc.fillOval(canvasX - radius, canvasY - radius, diameter, diameter);
			gc.strokeOval(canvasX - radius, canvasY - radius, diameter, diameter);
		}
	}

	public Group getGroup() {
		return group;
	}

	public boolean mouseClicked(MouseEvent ev) {
		double x = -viewX + ev.getX();
		double y = -viewY + ev.getY();

		if (ev.isStillSincePress()) {
			Stop stop = findNearStop(x, y);

			String text;

			if (stop != null) {
				if (stop.name != null && !stop.name.isBlank()) {
					text = stop.name;
				} else {
					text = String.format("%.6f, %.6f", stop.lat, stop.lon);
				}
			} else {
				double[] latlon = CoordSystem.getLatLonFromLocal(x, y);
				text = String.format("%.6f, %.6f", latlon[0], latlon[1]);
			}

			if (settingStart) {
				journeyInput.setStart(text);
			} else {
				journeyInput.setEnd(text);
			}

			settingStart = !settingStart;

			return true;
		}

		return false;
	}

	public boolean mouseMoved(MouseEvent ev) {
		if (hoverCard.hovered()) return true;

		double x = -viewX + ev.getX();
		double y = -viewY + ev.getY();

		Stop stop = findNearStop(x, y);
		if (stop == null) {
			if (hoverCard.overlaps(x, y)) {
				return true;
			}

			hoverCard.hide();
			return false;
		}

		List<String> arrivals = getArrivalStrings(stop);
		hoverCard.show(stop, arrivals, viewWidth, viewHeight, ev.getX(), ev.getY());

		return true;
	}

	public boolean mouseExited(MouseEvent ev) {
		if (!hoverCard.hovered()) {
			hoverCard.hide();
		}

		return false;
	}

	Stop findNearStop(double localX, double localY) {
		double bestSqDistance = CLICK_RADIUS * CLICK_RADIUS;
		Stop best = null;

		for (Stop stop: parser.stops.values()) {
			double[] local = CoordSystem.getLocalFromLatLon(stop.lat, stop.lon);

			double dx = local[0] - localX;
			double dy = local[1] - localY;

			double sqDistance = (dx * dx) + (dy * dy);

			if (sqDistance <= bestSqDistance) {
				bestSqDistance = sqDistance;
				best = stop;
			}
		}

		return best;
	}

	void buildArrivalMap() {
		arrivalMap.clear();

		for (Trip trip: parser.trips.values()) {
			for (StopTime time: trip.stopTimes) {
				Arrival arrival = new Arrival(
					time.arrivalTime,
					trip.route.shortName,
					trip.route.longName,
					trip.headSign
				);

				arrivalMap
					.computeIfAbsent(time.stop.id, x -> new ArrayList<>())
					.add(arrival);
			}
		}

		for (List<Arrival> arrivals: arrivalMap.values()) {
			arrivals.sort(Comparator.comparingInt(arrival -> arrival.timeSeconds));
		}
	}

	List<String> getArrivalStrings(Stop stop) {
		List<Arrival> arrivals = arrivalMap.get(stop.id);
		if (arrivals == null || arrivals.isEmpty()) {
			return List.of("No scheduled rides");
		}

		int earliest = 0;

		List<String> rows = new ArrayList<>();

		try {
			if (!journeyInput.getTime().isBlank()) {
				earliest = ParsingUtil.timeStringToSecondsAfterMidnight(
						journeyInput.getTime());
			}
		} catch (Exception ex) {}

		for (Arrival arrival: arrivals) {
			if (arrival.timeSeconds < earliest) {
				continue;
			}

			rows.add(arrival.toString());
		}

		if (rows.isEmpty()) {
			rows.add("No later rides today");
		}

		return rows;
	}
}

