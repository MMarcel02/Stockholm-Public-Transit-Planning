package com.team18.gui;

import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.input.MouseEvent;

import com.team18.parser.GTFSParser;
import com.team18.model.Stop;
import com.team18.routing.raptor.RaptorNetwork;

public class StopLayer implements Layer {
	static final double CLICK_RADIUS = 10;

	static final double HOVER_CARD_WIDTH = 340;
	static final double HOVER_CARD_HEIGHT = 390;

	boolean settingStart = true;

	Canvas canvas = new Canvas();
	Group group = new Group();

	GTFSParser parser;
	JourneyInput journeyInput;

	StopHoverCard hoverCard;

	double viewWidth = 0;
	double viewHeight = 0;

	Map<String, List<Arrival>> arrivalMap = new HashMap<>();

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

		String toString() {
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

		this.parser = parser;
		this.journeyInput = journeyInput;

		hoverCard = new StopHoverCard(journeyInput, network);
		group.getChildren().add(hoverCard.getVBox());

		buildArrivalMap();
	}

	public void render(double x, double y, double width, double height) {
		viewWidth = width;
		viewHeight = height;

		width  += Tile.RESOLUTION * 2;
		height += Tile.RESOLUTION * 2;

		double minX = -x - Tile.RESOLUTION;
		double minY = -y - Tile.RESOLUTION;
		double maxX = minX + width;
		double maxY = minY + height;

		canvas.setTranslateX(minX);
		canvas.setTranslateY(minY);
		canvas.setWidth(width);
		canvas.setHeight(height);

		GraphicsContext gc = canvas.getGraphicsContext2D();
		gc.clearRect(0, 0, width, height);

		double radius = 3.5;
		if (CoordSystem.getZoomLevel() >= 15) radius = 4.5;

		gc.setFill(Color.web("#E91E63", 0.8));
		gc.setStroke(Color.WHITE);
		gc.setLineWidth(1);

		for (Stop stop: parser.stops.values()) {
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
		if (ev.isStillSincePress()) {
			Stop stop = findNearStop(ev.getX(), ev.getY());

			String text;

			if (stop != null) {
				if (stop.name != null && !stop.name.isBlank()) {
					text = stop.name;
				} else {
					text = String.format("%.6f, %.6f", stop.lat, stop.lon);
				}
			} else {
				double[] latlon = CoordSystem.getLatLonFromLocal(ev.getX(), ev.getY());
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

		Stop stop = findNearStop(ev.getX(), ev.getY());
		if (stop == null) {
			if (hoverCard.overlaps(ev.getX(), ev.getY())) {
				return true;
			}

			hoverCard.hide();
			return false;
		}

		List<String> arrivals = getArrivalStrings(stop);
		hoverCard.show(stop, arrivals, viewWidth, viewHeight, ev.getX(), ev.getY());
	}

	public boolean mouseExited(MouseEvent ev) {
		if (!hoverCard.hovered()) {
			hoverCard.hide();
		}
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
					stopTime.arrivalTime,
					trip.route.shortName,
					trip.route.longName,
					trip.headSign
				);

				arrivalMap
					.computeIfAbsent(time.stop.id, x -> new ArrayList<>())
					.add(arrival);
			}
		}

		for (List<Arriva> arrivals: arrivalMap.values()) {
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

