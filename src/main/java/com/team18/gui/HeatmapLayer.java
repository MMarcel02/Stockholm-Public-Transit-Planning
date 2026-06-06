package com.team18.gui;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import com.team18.gui.Layer;
import com.team18.util.GeoCalculator;
import com.team18.util.StockholmUrbanArea;
import com.team18.model.Stop;
import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.routing.raptor.RaptorNetwork;

public class HeatmapLayer implements Layer {
	static final int COL_COUNT = 90;
	static final int ROW_COUNT = 56;

	static final double WALK_SPEED_MPS = 50.0 / 36.0;

	RaptorNetwork network;

	List<Point> points = List.of();

	Canvas canvas = new Canvas();
	Group group = new Group();

	boolean differenceMode = false;

	double viewWidth = 0;
	double viewHeight = 0;
	double viewX = 0;
	double viewY = 0;

	public static class Point {
		public final double lat;
		public final double lon;
		public final double valueMinutes;

		public Point(double lat, double lon, double valueMinutes) {
			this.lat = lat;
			this.lon = lon;
			this.valueMinutes = valueMinutes;
		}
	}

	public HeatmapLayer(RaptorNetwork network) {
		canvas.setMouseTransparent(true);
		group.getChildren().add(canvas);

		this.network = network;
	}

	public void configureDelayMode(double originLat, double originLon,
			int startTimeSeconds) {
		differenceMode = false;
		for (boolean enabled: network.stopsEnabledArr) {
			if (!enabled) differenceMode = true;
		}

		int[] times = new RaptorAlgorithm(network)
				.getTravelTimesToStops(originLat, originLon, startTimeSeconds);

		// Only used if doing a difference heatmap
		int[] baselineTimes = null;

		if (differenceMode) {
			boolean[] oldEnabled = Arrays.copyOf(
					network.stopsEnabledArr,
					network.stopsEnabledArr.length);

			try {
				Arrays.fill(network.stopsEnabledArr, true);
				baselineTimes = new RaptorAlgorithm(network).getTravelTimesToStops(
						originLat, originLon, startTimeSeconds);
			} finally {
				System.arraycopy(oldEnabled, 0, network.stopsEnabledArr, 0,
						oldEnabled.length);
			}
		}

		double latSize = getCellLatSize();
		double lonSize = getCellLonSize();

		points = new ArrayList<>();

		for (int row = 0; row < ROW_COUNT; row++) {
			double lat = StockholmUrbanArea.OUTER_MAX_LAT - ((row + 0.5) * latSize);

			for (int col = 0; col < COL_COUNT; col++) {
				double lon =
					StockholmUrbanArea.OUTER_MIN_LON + ((col + 0.5) * lonSize);

				if (differenceMode) {
					int newSeconds =
						estimateTravelTimeToPoint(
							originLat, originLon,
							lat, lon,
							times
						);

					int baselineSeconds =
						estimateTravelTimeToPoint(
							originLat, originLon,
							lat, lon,
							baselineTimes
						);

					double delayMinutes = Math.max(0,
							(newSeconds - baselineSeconds) / 60);

					points.add(new Point(lat, lon, delayMinutes));
				} else {
					int seconds =
						estimateTravelTimeToPoint(
							originLat, originLon,
							lat, lon,
							times
						);

					points.add(new Point(lat, lon, seconds / 60.0));
				}
			}
		}

		update();
	}

	public void configureManual(List<Point> points) {
		this.points = points;
		this.differenceMode = false;
		update();
	}

	private int estimateTravelTimeToPoint(double originLat, double originLon,
			double lat, double lon, int[] times) {
		double walkOnlyDistance = GeoCalculator.calculateEquirectangularDistance(
				originLat, originLon, lat, lon);

		int bestSeconds = (int) Math.round(walkOnlyDistance / WALK_SPEED_MPS);

		for (int i = 0; i < times.length; i++) {
			if (times[i] == Integer.MAX_VALUE) continue;

			Stop stop = network.stopLookup[i];
			double walkDistanceLeft = GeoCalculator.calculateEquirectangularDistance(
					stop.lat, stop.lon, lat, lon);

			int totalSeconds =
				times[i] + (int) Math.round(walkDistanceLeft / WALK_SPEED_MPS);

			if (totalSeconds < bestSeconds) {
				bestSeconds = totalSeconds;
			}
		}

		return bestSeconds;
	}

	public void clear() {
		this.points = List.of();
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
		if (points.isEmpty()) return;

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

		double radius = 22;
		if (CoordSystem.getZoomLevel() < 15) radius = 16;
		if (CoordSystem.getZoomLevel() < 13) radius = 11;

		for (Point point: points) {
			gc.setFill(pointColor(point));

			if (getCellLatSize() > 0 && getCellLonSize() > 0) {
				double[] topLeft = CoordSystem.getLocalFromLatLon(
						point.lat + (getCellLatSize() / 2.0),
						point.lon - (getCellLonSize() / 2.0)
				);

				double[] bottomRight = CoordSystem.getLocalFromLatLon(
						point.lat - (getCellLatSize() / 2.0),
						point.lon + (getCellLonSize() / 2.0)
				);

				if (bottomRight[0] < minX
						|| topLeft[0] > maxX
						|| bottomRight[1] < minY
						|| topLeft[1] > maxY) {
					continue;
				}

				double cellX = topLeft[0] - minX;
				double cellY = topLeft[1] - minY;

				gc.fillRect(
					cellX,
					cellY,
					bottomRight[0] - topLeft[0] + 1,
					bottomRight[1] - topLeft[1] + 1
				);
			} else {
				double[] local = CoordSystem.getLocalFromLatLon(point.lat, point.lon);

				if (local[0] < minX - radius
						|| local[0] > maxX + radius
						|| local[1] < minY - radius
						|| local[1] > maxY + radius) {
					continue;
				}

				double canvasX = local[0] - minX;
				double canvasY = local[1] - minY;

				double diameter = radius * 2;
				gc.fillOval(canvasX - radius, canvasY - radius, diameter, diameter);
			}
		}
	}

	private Color pointColor(Point point) {
		if (differenceMode) {
			double[] thresholds = {0, 2, 5, 8, 12, 16, 22, 30};

			String[] colors = {
				"#0B5D1E", "#2E7D32", "#8BC34A", "#FDD835",
				"#FB8C00", "#EF9A9A", "#E53935", "#8E0000"
			};

			return interpolatePalette(point.valueMinutes, thresholds, colors, 0.50);
		}

		double[] thresholds = {0, 10, 20, 30, 45, 60, 75, 90};

		String[] colors = {
			"#0B5D1E", "#2E7D32", "#8BC34A", "#FDD835",
			"#FB8C00", "#EF9A9A", "#E53935", "#8E0000"
		};

		return interpolatePalette(point.valueMinutes, thresholds, colors, 0.48);
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

	private double getCellLatSize() {
		return (StockholmUrbanArea.OUTER_MAX_LAT - StockholmUrbanArea.OUTER_MIN_LAT)
			/ ROW_COUNT;
	}

	private double getCellLonSize() {
		return (StockholmUrbanArea.OUTER_MAX_LON - StockholmUrbanArea.OUTER_MIN_LON)
			/ COL_COUNT;
	}

	public Group getGroup() {
		return group;
	}
}

