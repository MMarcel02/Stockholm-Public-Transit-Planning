package com.team18.gui;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;

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
import com.team18.optimizer.Config;

public class HeatmapLayer implements Layer {
	static final int KD_LEAF_SIZE = 16;

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
								   int startTimeSeconds, boolean differenceMode) {
		this.differenceMode = differenceMode;

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

		Estimator estimator = new Estimator(
				new double[] {originLat, originLon},
				times, network.stopLookup);

		Estimator baselineEstimator = null;
		if (differenceMode) baselineEstimator = new Estimator(
				new double[] {originLat, originLon},
				baselineTimes, network.stopLookup);


		for (int row = 0; row < getRowCount(); row++) {
			double lat = StockholmUrbanArea.OUTER_MAX_LAT - ((row + 0.5) * latSize);

			for (int col = 0; col < getColCount(); col++) {
				double lon =
					StockholmUrbanArea.OUTER_MIN_LON + ((col + 0.5) * lonSize);

				if (differenceMode) {
					int newSeconds = estimator.estimateTravelTimeToPoint(lat, lon);
					// Use the baselineEstimator instead of estimator
					int baselineSeconds = baselineEstimator.estimateTravelTimeToPoint(lat, lon);

					double delayMinutes =
							Math.max(0.0, (newSeconds - baselineSeconds) / 60.0);

					points.add(new Point(lat, lon, delayMinutes));
				} else {
					int seconds = estimator.estimateTravelTimeToPoint(lat, lon);
					points.add(new Point(lat, lon, (double) seconds / 60.0));
				}
			}
		}

		update();
	}

	public void configureManual(List<Point> points,
			double cellLatSize, double cellLonSize) {
		this.points = points;
		this.differenceMode = false;
		this.cellLatSize = cellLatSize;
		this.cellLonSize = cellLonSize;
		update();
	}

	public void clear() {
		points = List.of();
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
		if (differenceMode) {
			if (point.valueMinutes <= 0.05) {
				return Color.TRANSPARENT;
			}
			double[] thresholds = {0, 1, 3, 5, 10, 15, 20, 30};

			String[] colors = {
					"#FDD835", // Yellow (starts here immediately after 0.01)
					"#FB8C00", // 1 min delay -> Orange
					"#E53935", // 3 min delay -> Red
					"#8E0000", // 5+ min delay -> Dark Red
					"#4A0000", "#300000", "#1A0000", "#000000"
			};
			return interpolatePalette(point.valueMinutes, thresholds, colors, 0.65);
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


	private static class Estimator {
		private final double originLat;
		private final double originLon;
		private final KdNode root;

		Estimator(double[] origin, int[] travelTimes, Stop[] stopLookup) {
			this.originLat = origin[0];
			this.originLon = origin[1];

			List<Candidate> candidates = new ArrayList<>();
			for (int i = 0; i < travelTimes.length; i++) {
				if (travelTimes[i] == Integer.MAX_VALUE) continue;

				Stop stop = stopLookup[i];
				candidates.add(new Candidate(stop.lat, stop.lon, travelTimes[i]));
			}

			Candidate[] candidateArray = candidates.toArray(new Candidate[0]);
			this.root = candidateArray.length == 0
				? null
				: new KdNode(candidateArray, 0, candidateArray.length);
		}

		int estimateTravelTimeToPoint(double lat, double lon) {
			double directWalkDistance = GeoCalculator.calculateEquirectangularDistance(originLat, originLon, lat, lon);
			int bestSeconds = (int) Math.round(directWalkDistance / Config.WALK_SPEED_MPS);
			return root == null ? bestSeconds : root.estimateTravelTimeToPoint(lat, lon, bestSeconds);
		}
	}

	private static class KdNode {
		private final double minLat;
		private final double maxLat;
		private final double minLon;
		private final double maxLon;
		private final int minTravelSeconds;
		private final Candidate[] candidates;
		private final KdNode left;
		private final KdNode right;

		KdNode(Candidate[] points, int start, int end) {
			double nodeMinLat = Double.POSITIVE_INFINITY;
			double nodeMaxLat = Double.NEGATIVE_INFINITY;
			double nodeMinLon = Double.POSITIVE_INFINITY;
			double nodeMaxLon = Double.NEGATIVE_INFINITY;
			int nodeMinTravelSeconds = Integer.MAX_VALUE;

			for (int i = start; i < end; i++) {
				Candidate point = points[i];
				nodeMinLat = Math.min(nodeMinLat, point.lat);
				nodeMaxLat = Math.max(nodeMaxLat, point.lat);
				nodeMinLon = Math.min(nodeMinLon, point.lon);
				nodeMaxLon = Math.max(nodeMaxLon, point.lon);
				nodeMinTravelSeconds =
					Math.min(nodeMinTravelSeconds, point.travelSeconds);
			}

			this.minLat = nodeMinLat;
			this.maxLat = nodeMaxLat;
			this.minLon = nodeMinLon;
			this.maxLon = nodeMaxLon;
			this.minTravelSeconds = nodeMinTravelSeconds;

			if (end - start <= KD_LEAF_SIZE) {
				this.candidates = Arrays.copyOfRange(points, start, end);
				this.left = null;
				this.right = null;
				return;
			}

			boolean splitByLat = (maxLat - minLat) >= (maxLon - minLon);
			Arrays.sort(points, start, end, splitByLat
					? Comparator.comparingDouble(candidate -> candidate.lat)
					: Comparator.comparingDouble(candidate -> candidate.lon));

			int midpoint = start + ((end - start) / 2);
			this.candidates = null;
			this.left = new KdNode(points, start, midpoint);
			this.right = new KdNode(points, midpoint, end);
		}

		int estimateTravelTimeToPoint(double lat, double lon, int bestSeconds) {
			if (lowerBoundSeconds(lat, lon) >= bestSeconds) {
				return bestSeconds;
			}

			if (candidates != null) {
				for (Candidate candidate : candidates) {
					if (candidate.travelSeconds >= bestSeconds) continue;

					double walkDistance =
						GeoCalculator.calculateEquirectangularDistance(
								candidate.lat, candidate.lon, lat, lon);

					int totalSeconds = candidate.travelSeconds
						+ (int) Math.round(walkDistance / Config.WALK_SPEED_MPS);

					if (totalSeconds < bestSeconds) {
						bestSeconds = totalSeconds;
					}
				}
				return bestSeconds;
			}

			double leftLowerBound = left.lowerBoundSeconds(lat, lon);
			double rightLowerBound = right.lowerBoundSeconds(lat, lon);

			if (leftLowerBound <= rightLowerBound) {
				bestSeconds = left.estimateTravelTimeToPoint(lat, lon, bestSeconds);
				bestSeconds = right.estimateTravelTimeToPoint(lat, lon, bestSeconds);
			} else {
				bestSeconds = right.estimateTravelTimeToPoint(lat, lon, bestSeconds);
				bestSeconds = left.estimateTravelTimeToPoint(lat, lon, bestSeconds);
			}

			return bestSeconds;
		}

		private double lowerBoundSeconds(double lat, double lon) {
			return minTravelSeconds
				+ (minimumDistanceToBoundsMeters(lat, lon) / Config.WALK_SPEED_MPS);
		}

		private double minimumDistanceToBoundsMeters(double lat, double lon) {
			double latDistance = 0.0;
			if (lat < minLat) {
				latDistance = Math.toRadians(minLat - lat) * Config.EARTH_RADIUS_METERS;
			} else if (lat > maxLat) {
				latDistance = Math.toRadians(lat - maxLat) * Config.EARTH_RADIUS_METERS;
			}

			double lonDistance = 0.0;
			if (lon < minLon) {
				lonDistance = conservativeLongitudeDistanceMeters(
						lat, minLat, maxLat, minLon - lon);
			} else if (lon > maxLon) {
				lonDistance = conservativeLongitudeDistanceMeters(
						lat, minLat, maxLat, lon - maxLon);
			}

			return Math.max(latDistance, lonDistance);
		}

		private double conservativeLongitudeDistanceMeters(
				double queryLat,
				double boundsMinLat, double boundsMaxLat,
				double deltaLonDegrees) {
			double maxAbsLat = Math.max(
				Math.abs(queryLat),
				Math.max(Math.abs(boundsMinLat), Math.abs(boundsMaxLat))
			);

			return Math.toRadians(deltaLonDegrees)
				* Config.EARTH_RADIUS_METERS
				* Math.cos(Math.toRadians(maxAbsLat));
		}
	}

	private static class Candidate {
		final double lat;
		final double lon;
		final int travelSeconds;

		Candidate(double lat, double lon, int travelSeconds) {
			this.lat = lat;
			this.lon = lon;
			this.travelSeconds = travelSeconds;
		}
	}
}

