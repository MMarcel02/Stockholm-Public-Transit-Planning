package com.team18.gui;

import java.util.List;
import java.time.LocalDate;
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
import javafx.scene.Cursor;
import com.team18.util.ParsingUtil;

public class StopLayer implements Layer {
	static final double CLICK_RADIUS = 5;

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

	Set<String> alreadyDrawn = new HashSet<>();

	public static class Arrival {
        public final int timeSeconds;
        public final String shortName;
        public final String longName;
        public final String headSign;
        public final Trip trip;

        public Arrival(int timeSeconds, String shortName, String longName, String headSign, Trip trip) {
            this.timeSeconds = timeSeconds;
            this.shortName = shortName;
            this.longName = longName;
            this.headSign = headSign;
            this.trip = trip;
        }

		@Override
		public String toString() {
			String route = shortName;
			if (route.isBlank()) route = longName;
			if (route.isBlank()) route = "Route";

			String direction = "";
			if (!direction.isBlank()) direction = " to " + headSign;

			int hours = (timeSeconds / 3600) % 24;
			int mins = (timeSeconds % 3600) / 60;

			return String.format(Locale.US, "%02d:%02d | %s%s", hours, mins, route, direction);
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

		if (CoordSystem.getZoomLevel() <= 12) return;


		gc.setStroke(Color.WHITE);
		gc.setLineWidth(1);

		alreadyDrawn.clear();
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

			double radius = stopRadius();
			double diameter = radius * 2;

			gc.fillOval(canvasX - radius, canvasY - radius, diameter, diameter);
			gc.strokeOval(canvasX - radius, canvasY - radius, diameter, diameter);
		}
	}

	double stopRadius() {
		if (CoordSystem.getZoomLevel() >= 15) return 6;
		else return 4.5;
	}

	public Group getGroup() {
		return group;
	}

	@Override
	public boolean mouseClicked(MouseEvent ev) {
        double x = -viewX + ev.getX();
        double y = -viewY + ev.getY();

        if (ev.isStillSincePress()) {
            Stop stop = findNearStop(x, y);

            if (stop != null) {
                // show the card on click 
                List<Arrival> arrivals = getArrivals(stop);
                hoverCard.show(stop, arrivals);
                return true;
            } else {
                // hide if click out of hover card
                if (hoverCard.isVisible()) {
                    hoverCard.hide();
                    return true;
                }

                // keep looking if no card
                double[] latlon = CoordSystem.getLatLonFromLocal(x, y);
                String text = String.format(Locale.US, "%.6f, %.6f", latlon[0], latlon[1]);

                if (settingStart) {
                    journeyInput.setStart(text);
                } else {
                    journeyInput.setEnd(text);
                }
                settingStart = !settingStart;

                return true;
            }
        }

        return false;
    }

	Stop findNearStop(double localX, double localY) {
		double bestSqDistance = Math.pow(stopRadius() + 3, 2);
		Stop best = null;

		Set<String> alreadySeen = new HashSet<>();

		for (Stop stop: parser.stops.values()) {
			if (alreadySeen.contains(stop.name)) continue;
			alreadySeen.add(stop.name);

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
                    trip.headSign,
                    trip
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

    List<Arrival> getArrivals(Stop stop) {
	    LocalDate activeDate = journeyInput.getEffectiveDate();
	    Set<String> activeServices = network.serviceByCalendar.get(activeDate);

	    List<Arrival> arrivals = new ArrayList<>();
	    for (Stop otherStop: parser.stops.values()) {
		    if (stop.name.equals(otherStop.name)) {
			    arrivals.addAll(arrivalMap.get(otherStop.id));
		    }
	    }

	    if (arrivals == null || arrivals.isEmpty()) {
		    return List.of(new Arrival(-1, "", "No scheduled rides", "", null));
	    }

	    int earliest = 0;
	    List<Arrival> rows = new ArrayList<>();

	    try {
		    if (!journeyInput.getTime().isBlank()) {
			    earliest = ParsingUtil.timeStringToSecondsAfterMidnight(
					    journeyInput.getTime());
		    }
	    } catch (Exception ex) {}

	    for (Arrival arrival: arrivals) {
		    if (activeServices.contains(arrival.trip.serviceId)) {
			    if (arrival.timeSeconds >= earliest) {
				    rows.add(arrival);
			    }
		    }
	    }

	    if (rows.isEmpty()) {
		    rows.add(new Arrival(-1, "", "No later rides today", "", null));
	    }

	    return rows;
    }
}

