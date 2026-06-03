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

	public Map(GTFSParser parser) {
		this.parser = parser;

		mapGroup = new Group();

		boundingBoxCanvas = new Canvas();
		boundingBoxCanvas.setMouseTransparent(true);
		mapGroup.getChildren().add(boundingBoxCanvas);

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
		refreshStops();
	}

	public Group getMapGroup() { return mapGroup; }

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
}
