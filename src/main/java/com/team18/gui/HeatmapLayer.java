package com.team18.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.team18.model.Stop;
import com.team18.optimizer.Config;
import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.routing.raptor.RaptorNetwork;
import com.team18.util.Colors;
import com.team18.util.GeoCalculator;
import com.team18.util.StockholmUrbanArea;

import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class HeatmapLayer implements Layer {
	RaptorNetwork network;

	List<Point> points = List.of();

	Canvas canvas = new Canvas();
	Group group = new Group();

	boolean differenceMode = false;

	double viewWidth = 0;
	double viewHeight = 0;
	double viewX = 0;
	double viewY = 0;

	double cellLatSize = 0;
	double cellLonSize = 0;

	double[] thresholds;
	String[] colors;

	Legend legend;
	Legend.Item legendItem = null;

	public static class Point {
		public final double lat;
		public final double lon;
		public final double value;

		public Point(double lat, double lon, double value) {
			this.lat = lat;
			this.lon = lon;
			this.value = value;
		}
	}

	public HeatmapLayer(RaptorNetwork network, Legend legend) {
		canvas.setMouseTransparent(true);
		group.getChildren().add(canvas);

		this.network = network;
		this.legend = legend;
	}

	public void configureDelayMode(double originLat, double originLon,
								   int startTimeSeconds, boolean differenceMode) {
		this.differenceMode = differenceMode;

		String legendTitle;

		if (differenceMode) {
			legendTitle = "Delay (min)";
			thresholds = new double[] {0, 1, 3, 5, 10, 15, 20, 30};
			colors = new String[] {
					"#FFF59D", // 1. Light Yellow
					"#FFEB3B", // 2. Normal Yellow
					"#FF9800", // 3. Orange
					"#E65100", // 4. Dark Orange
					"#EF5350", // 5. Light Red
					"#D32F2F", // 6. Red
					"#8E0000", // 7. Dark Red
					"#000000"  // 8. Black
			};
		} else {
			legendTitle = "Travel time (min)";
			thresholds = new double[] {0, 15, 30, 45, 60, 80, 100, 120};
			colors = new String[] {
				"#a50026", 
				"#d73027", 
				"#f46d43", 
				"#fdae61", 
				"#abd9e9", 
				"#74add1", 
				"#4575b4", 
				"#313695"  
			};
		}

		if (legendItem != null) legend.remove(legendItem);
		legendItem = new Legend.Item(legendTitle, thresholds, colors);
		legend.add(legendItem);

		cellLatSize =
			(StockholmUrbanArea.OUTER_MAX_LAT - StockholmUrbanArea.OUTER_MIN_LAT)
			/ (56*3);

		cellLonSize =
			(StockholmUrbanArea.OUTER_MAX_LON - StockholmUrbanArea.OUTER_MIN_LON)
			/ (90*3);

		int[] times = new RaptorAlgorithm(network)
				.getBestArrivalTimeToAllStops(originLat, originLon, startTimeSeconds);

		// Convert absolute arrival times into relative travel durations
		for (int i = 0; i < times.length; i++) {
			if (times[i] != Integer.MAX_VALUE) {
				times[i] -= startTimeSeconds;
			}
		}

		// Only used if doing a difference heatmap
		int[] baselineTimes = null;

		if (differenceMode) {
			boolean[] oldEnabled = Arrays.copyOf(
					network.stopsEnabledArr,
					network.stopsEnabledArr.length);

			try {
				Arrays.fill(network.stopsEnabledArr, true);
				baselineTimes = new RaptorAlgorithm(network)
						.getBestArrivalTimeToAllStops(
								originLat, originLon, startTimeSeconds);

				// Convert baseline absolute arrival times into relative travel durations
				for (int i = 0; i < baselineTimes.length; i++) {
					if (baselineTimes[i] != Integer.MAX_VALUE) {
						baselineTimes[i] -= startTimeSeconds;
					}
				}
			} finally {
				System.arraycopy(oldEnabled, 0, network.stopsEnabledArr, 0,
						oldEnabled.length);
			}
		}

		double latSize = cellLatSize;
		double lonSize = cellLonSize;

		points = new ArrayList<>();

		for (int row = 0; row < getRowCount(); row++) {
			double lat = StockholmUrbanArea.OUTER_MAX_LAT - ((row + 0.5) * latSize);

			for (int col = 0; col < getColCount(); col++) {
				double lon =
					StockholmUrbanArea.OUTER_MIN_LON + ((col + 0.5) * lonSize);

				if (differenceMode) {
					int newSeconds = estimateTravelTimeToPoint(
						originLat, originLon,
						lat, lon,
						times
					);

					int baselineSeconds = estimateTravelTimeToPoint(
						originLat, originLon,
						lat, lon,
						baselineTimes
					);

					double delayMinutes =
							Math.max(0.0, (newSeconds - baselineSeconds) / 60.0);

					points.add(new Point(lat, lon, delayMinutes));
				} else {
					int seconds = estimateTravelTimeToPoint(
						originLat, originLon,
						lat, lon,
						times
					);

					points.add(new Point(lat, lon, (double) seconds / 60.0));
				}
			}
		}

		update();
	}

	public void configureManual(List<Point> points,
			double cellLatSize, double cellLonSize,
			double[] thresholds, String[] colors, String title) {
		this.points = points;
		this.differenceMode = false;
		this.cellLatSize = cellLatSize;
		this.cellLonSize = cellLonSize;
		this.thresholds = thresholds;
		this.colors = colors;

		if (legendItem != null) legend.remove(legendItem);
		legendItem = new Legend.Item(title, thresholds, colors);
		legend.add(legendItem);

		update();
	}

	public void clear() {
		points = List.of();

		if (legendItem != null) legend.remove(legendItem);
		legendItem = null;

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
		GraphicsContext gc = canvas.getGraphicsContext2D();

		if (points.isEmpty()) {
			canvas.setWidth(1);
			canvas.setHeight(1);
			gc.clearRect(0, 0, 1, 1);
			return;
		}

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

		gc.clearRect(0, 0, width, height);

		double radius = 22;
		if (CoordSystem.getZoomLevel() < 15) radius = 16;
		if (CoordSystem.getZoomLevel() < 13) radius = 11;

		for (Point point: points) {
			gc.setFill(pointColor(point));

			if (cellLatSize > 0 && cellLonSize > 0) {
				double[] topLeft = CoordSystem.getLocalFromLatLon(
						point.lat + (cellLatSize / 2.0),
						point.lon - (cellLonSize / 2.0)
				);

				double[] bottomRight = CoordSystem.getLocalFromLatLon(
						point.lat - (cellLatSize / 2.0),
						point.lon + (cellLonSize / 2.0)
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
		if (differenceMode && point.value <= 0.05) {
			return Color.TRANSPARENT;
		}

		return Colors.interpolatePalette(point.value, thresholds, colors, 0.7);
	}


	private double getRowCount() {
		return
			(StockholmUrbanArea.OUTER_MAX_LAT - StockholmUrbanArea.OUTER_MIN_LAT)
			/ cellLatSize;
	}

	private double getColCount() {
		return
			(StockholmUrbanArea.OUTER_MAX_LON - StockholmUrbanArea.OUTER_MIN_LON)
			/ cellLonSize;
	}

	public Group getGroup() {
		return group;
	}


	private int estimateTravelTimeToPoint(double originLat, double originLon,
			double lat, double lon, int[] times) {
		double walkOnlyDistance = GeoCalculator.calculateEquirectangularDistance(
				originLat, originLon, lat, lon);
		int bestSeconds = (int) Math.round(walkOnlyDistance / Config.WALK_SPEED_MPS);
		for (int i = 0; i < times.length; i++) {
			if (times[i] == Integer.MAX_VALUE) continue;
			Stop stop = network.stopLookup[i];
			double walkDistanceLeft = GeoCalculator.calculateEquirectangularDistance(
					stop.lat, stop.lon, lat, lon);
			int totalSeconds =
				times[i] + (int) Math.round(walkDistanceLeft / Config.WALK_SPEED_MPS);
			if (totalSeconds < bestSeconds) {
				bestSeconds = totalSeconds;
			}
		}

		return bestSeconds;
	}
}

