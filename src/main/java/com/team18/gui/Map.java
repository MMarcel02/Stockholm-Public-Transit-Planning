package com.team18.gui;

import com.team18.parser.GTFSParser;
import com.team18.model.RouteStep;
import com.team18.model.RouteStepType;
import com.team18.model.ShapePoint;
import com.team18.util.GeoCalculator;
import com.team18.util.StockholmUrbanArea;

import com.team18.gui.Layer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.Polyline;

import java.util.LinkedHashMap;

public class Map {
	private Group mapGroup;
	private Canvas heatmapCanvas;
	private Canvas stopCanvas;
	private Canvas boundingBoxCanvas;
	private double dragStartX = 0;
	private double dragStartY = 0;
	private double groupTranslateX = 0;
	private double groupTranslateY = 0;

	private LinkedHashMap<Tile.Coord, Tile> tiles = new LinkedHashMap<Tile.Coord, Tile>(1000, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<Tile.Coord, Tile> eldest) {
			return size() > 100;
		}
	};
	private ArrayList<Tile> activeTiles = new ArrayList<>();

	private GTFSParser parser;
	private ArrayList<Landmark> stopLandmarks = new ArrayList<>();
	private List<HeatmapPoint> heatmapPoints = List.of();
	private boolean heatmapDifferenceMode = false;
	private double heatmapCellLatSpan = 0.0;
	private double heatmapCellLonSpan = 0.0;

	public Map(GTFSParser parser) {
		this.parser = parser;

		mapGroup = new Group();

		boundingBoxCanvas = new Canvas();
		boundingBoxCanvas.setMouseTransparent(true);
		mapGroup.getChildren().add(boundingBoxCanvas);

		heatmapCanvas = new Canvas();
		heatmapCanvas.setMouseTransparent(true);
		mapGroup.getChildren().add(heatmapCanvas);

		stopCanvas = new Canvas();
		stopCanvas.setMouseTransparent(true);
		mapGroup.getChildren().add(stopCanvas);

		for (var stop: parser.stops.values()) {
			stopLandmarks.add(new Landmark(stop.lat, stop.lon, stop.id, stop.name));
		}

		// File cacheDir = new File(Tile.Coord.CACHE_DIR);
		// File[] files = cacheDir.listFiles();
		// if (files != null) {
		// 	for (File file: files) {
		// 		constructNewTile(Tile.Coord.fromFileName(file.getName()));
		// 	}
		// }

		//refresh();

		mapGroup.setOnMousePressed(ev -> {
			dragStartX = ev.getSceneX();
			dragStartY = ev.getSceneY();
			groupTranslateX = mapGroup.getTranslateX();
			groupTranslateY = mapGroup.getTranslateY();

			int delta = 0;
			if (ev.isMiddleButtonDown()) {
				delta = 1;
			} else if (ev.isSecondaryButtonDown()) {
				delta = -1;
			}
			if (delta == 0) return;

			double factor = Math.pow(2, delta);
			if (factor < 0) factor = 1 / (-factor);

			// TODO: We need to figure out the size of the visible map and offset by that,
			// so that zoom is centered in the middle.
			mapGroup.setTranslateX(mapGroup.getTranslateX() * factor);
			mapGroup.setTranslateY(mapGroup.getTranslateY() * factor);

			CoordSystem.setZoomLevel(CoordSystem.getZoomLevel() + delta);

			refresh();
		});

		mapGroup.setOnMouseDragged(ev -> {
			mapGroup.setTranslateX(groupTranslateX + (ev.getSceneX() - dragStartX));
			mapGroup.setTranslateY(groupTranslateY + (ev.getSceneY() - dragStartY));

			refresh();
		});

		mapGroup.setOnScroll(ev -> {
			int delta = (int) Math.floor(ev.getDeltaY() / ev.getMultiplierY());
			if (delta == 0) return;

			double factor = Math.pow(2, delta);
			if (factor < 0) factor = 1 / (-factor);

			// TODO: We need to figure out the size of the visible map and offset by that,
			// so that zoom is centered in the middle.
			mapGroup.setTranslateX(mapGroup.getTranslateX() * factor);
			mapGroup.setTranslateY(mapGroup.getTranslateY() * factor);

			CoordSystem.setZoomLevel(CoordSystem.getZoomLevel() + delta);

			refresh();
		});
	}

	public void setHeatmap(List<HeatmapPoint> heatmapPoints, boolean differenceMode, double cellLatSpan, double cellLonSpan) {
		this.heatmapPoints = heatmapPoints == null ? List.of() : List.copyOf(heatmapPoints);
		this.heatmapDifferenceMode = differenceMode;
		this.heatmapCellLatSpan = cellLatSpan;
		this.heatmapCellLonSpan = cellLonSpan;
		refresh();
	}

	public void clearHeatmap() {
		this.heatmapPoints = List.of();
		this.heatmapCellLatSpan = 0.0;
		this.heatmapCellLonSpan = 0.0;
		refresh();
	}

	private void refreshStops() {
		double width = mapGroup.getScene().getWidth() + (Tile.RESOLUTION * 2);
		double height = mapGroup.getScene().getWidth() + (Tile.RESOLUTION * 2);
		double minX = -mapGroup.getTranslateX() - Tile.RESOLUTION;
		double minY = -mapGroup.getTranslateY() - Tile.RESOLUTION;
		double maxX = minX + width;
		double maxY = minY + height;

		stopCanvas.setTranslateX(minX);
		stopCanvas.setTranslateY(minY);
		stopCanvas.setWidth(width);
		stopCanvas.setHeight(height);

		GraphicsContext gc = stopCanvas.getGraphicsContext2D();
		gc.clearRect(0, 0, width, height);

		// TODO: This.
		//if (zoomLevel < 13) return;

		double radius = CoordSystem.getZoomLevel() >= 15 ? 4.5 : 3.5;
		gc.setFill(Color.web("#E91E63", 0.80));
		gc.setStroke(Color.WHITE);
		gc.setLineWidth(1.0);

		for (Landmark landmark : stopLandmarks) {
			double[] local = CoordSystem.getLocalFromLatLon(landmark.lat, landmark.lon);
			if (local[0] < minX || local[0] > maxX || local[1] < minY || local[1] > maxY) {
				continue;
			}

			double canvasX = local[0] - minX;
			double canvasY = local[1] - minY;
			double diameter = radius * 2;
			gc.fillOval(canvasX - radius, canvasY - radius, diameter, diameter);
			gc.strokeOval(canvasX - radius, canvasY - radius, diameter, diameter);
		}
	}

	private void refreshHeatmap() {
		double width = mapGroup.getScene().getWidth() + (Tile.RESOLUTION * 2);
		double height = mapGroup.getScene().getHeight() + (Tile.RESOLUTION * 2);
		double minX = -mapGroup.getTranslateX() - Tile.RESOLUTION;
		double minY = -mapGroup.getTranslateY() - Tile.RESOLUTION;
		double maxX = minX + width;
		double maxY = minY + height;

		heatmapCanvas.setTranslateX(minX);
		heatmapCanvas.setTranslateY(minY);
		heatmapCanvas.setWidth(width);
		heatmapCanvas.setHeight(height);

		GraphicsContext gc = heatmapCanvas.getGraphicsContext2D();
		gc.clearRect(0, 0, width, height);

		if (heatmapPoints.isEmpty()) return;

		double radius = 22;
		if (CoordSystem.getZoomLevel() < 15) radius = 16;
		if (CoordSystem.getZoomLevel() < 13) radius = 11;

		for (HeatmapPoint point : heatmapPoints) {
			gc.setFill(colorForHeatmapValue(point.valueMinutes, heatmapDifferenceMode));
			if (heatmapCellLatSpan > 0 && heatmapCellLonSpan > 0) {
				double[] topLeft = CoordSystem.getLocalFromLatLon(
						point.lat + (heatmapCellLatSpan / 2.0),
						point.lon - (heatmapCellLonSpan / 2.0)
				);
				double[] bottomRight = CoordSystem.getLocalFromLatLon(
						point.lat - (heatmapCellLatSpan / 2.0),
						point.lon + (heatmapCellLonSpan / 2.0)
				);
				if (bottomRight[0] < minX || topLeft[0] > maxX || bottomRight[1] < minY || topLeft[1] > maxY) {
					continue;
				}
				double cellX = topLeft[0] - minX;
				double cellY = topLeft[1] - minY;
				gc.fillRect(cellX, cellY, bottomRight[0] - topLeft[0] + 1, bottomRight[1] - topLeft[1] + 1);
			} else {
				double[] local = CoordSystem.getLocalFromLatLon(point.lat, point.lon);
				if (local[0] < minX - radius || local[0] > maxX + radius || local[1] < minY - radius || local[1] > maxY + radius) {
					continue;
				}

				double canvasX = local[0] - minX;
				double canvasY = local[1] - minY;
				double diameter = radius * 2;
				gc.fillOval(canvasX - radius, canvasY - radius, diameter, diameter);
			}
		}
	}

	private Color colorForHeatmapValue(double valueMinutes, boolean differenceMode) {
		if (differenceMode) {
			double[] thresholds = {0, 2, 5, 8, 12, 16, 22, 30};
			String[] colors = {"#0B5D1E", "#2E7D32", "#8BC34A", "#FDD835", "#FB8C00", "#EF9A9A", "#E53935", "#8E0000"};
			return interpolatePalette(valueMinutes, thresholds, colors, 0.50);
		}

		double[] thresholds = {0, 10, 20, 30, 45, 60, 75, 90};
		String[] colors = {"#0B5D1E", "#2E7D32", "#8BC34A", "#FDD835", "#FB8C00", "#EF9A9A", "#E53935", "#8E0000"};
		return interpolatePalette(valueMinutes, thresholds, colors, 0.48);
	}

	private Color interpolatePalette(double value, double[] thresholds, String[] colors, double opacity) {
		if (value <= thresholds[0]) {
			return Color.web(colors[0], opacity);
		}

		for (int i = 1; i < thresholds.length; i++) {
			if (value <= thresholds[i]) {
				double ratio = (value - thresholds[i - 1]) / (thresholds[i] - thresholds[i - 1]);
				Color start = Color.web(colors[i - 1]);
				Color end = Color.web(colors[i]);
				return start.interpolate(end, ratio).deriveColor(0, 1, 1, opacity);
			}
		}

		return Color.web(colors[colors.length - 1], opacity);
	}

	private void refreshBoundingBox() {
		double width = mapGroup.getScene().getWidth() + (Tile.RESOLUTION * 2);
		double height = mapGroup.getScene().getHeight() + (Tile.RESOLUTION * 2);
		double minX = -mapGroup.getTranslateX() - Tile.RESOLUTION;
		double minY = -mapGroup.getTranslateY() - Tile.RESOLUTION;

		boundingBoxCanvas.setTranslateY(minY);
		boundingBoxCanvas.setTranslateX(minX);
		boundingBoxCanvas.setHeight(height);
		boundingBoxCanvas.setWidth(width);

		GraphicsContext gc = boundingBoxCanvas.getGraphicsContext2D();
		gc.clearRect(0, 0, width, height);


		double[] outerTopLeft = CoordSystem.getLocalFromLatLon(
			StockholmUrbanArea.OUTER_MAX_LAT, 
			StockholmUrbanArea.OUTER_MIN_LON
		);
		
		double[] outerBottomRight = CoordSystem.getLocalFromLatLon(
			StockholmUrbanArea.OUTER_MIN_LAT, 
			StockholmUrbanArea.OUTER_MAX_LON
		);

		double[] innerTopLeft = CoordSystem.getLocalFromLatLon(
			StockholmUrbanArea.INNER_MAX_LAT, 
			StockholmUrbanArea.INNER_MIN_LON
		);
		
		double[] innerBottomRight = CoordSystem.getLocalFromLatLon(
			StockholmUrbanArea.INNER_MIN_LAT, 
			StockholmUrbanArea.INNER_MAX_LON
		);

		double outRectY = outerTopLeft[1] - minY;
		double outRectX = outerTopLeft[0] - minX;
		double outRectWidth = outerBottomRight[0] - outerTopLeft[0];
		double outRectHeight = outerBottomRight[1] - outerTopLeft[1];
		gc.strokeRect(outRectX, outRectY, outRectWidth, outRectHeight);

		double inRectX = innerTopLeft[0] - minX;
		double inRectY = innerTopLeft[1] - minY;
		double inRectWidth = innerBottomRight[0] - innerTopLeft[0];
		double inRectHeight = innerBottomRight[1] - innerTopLeft[1];
		gc.strokeRect(inRectX, inRectY, inRectWidth, inRectHeight);

	}

	public void refresh() {
		refreshBoundingBox();
		refreshHeatmap();
		refreshStops();
	}

	public Group getMapGroup() { return mapGroup; }

	public static class HeatmapPoint {
		public final double lat;
		public final double lon;
		public final double valueMinutes;

		public HeatmapPoint(double lat, double lon, double valueMinutes) {
			this.lat = lat;
			this.lon = lon;
			this.valueMinutes = valueMinutes;
		}
	}

	public Landmark findStopNearLocal(double localX, double localY, double radiusPixels) {
	        double bestDistanceSquared = radiusPixels * radiusPixels;
	        Landmark best = null;
	
	        for (Landmark landmark : stopLandmarks) {
	                double[] local = CoordSystem.getLocalFromLatLon(landmark.lat, landmark.lon);
	                double dx = local[0] - localX;
	                double dy = local[1] - localY;
	                double distanceSquared = (dx * dx) + (dy * dy);
	
	                if (distanceSquared <= bestDistanceSquared) {
	                        bestDistanceSquared = distanceSquared;
	                        best = landmark;
	                }
	        }
	
	        return best;
	}
}
