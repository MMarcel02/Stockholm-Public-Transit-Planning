package com.team18.gui;

import com.team18.model.Stop;
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

	public Map(GTFSParser parser) {
		this.parser = parser;

		mapGroup = new Group();

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

			//refresh();
		});

		mapGroup.setOnMouseDragged(ev -> {
			mapGroup.setTranslateX(groupTranslateX + (ev.getSceneX() - dragStartX));
			mapGroup.setTranslateY(groupTranslateY + (ev.getSceneY() - dragStartY));

			//refresh();
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

			//refresh();
		});
	}

	public Group getMapGroup() { return mapGroup; }

	public Stop findStopNearLocal(double localX, double localY, double radiusPixels) {
	        double bestDistanceSquared = radiusPixels * radiusPixels;
	        Stop best = null;
	
	        for (Stop stop : parser.stops.values()) {
	                double[] local = CoordSystem.getLocalFromLatLon(stop.lat, stop.lon);
	                double dx = local[0] - localX;
	                double dy = local[1] - localY;
	                double distanceSquared = (dx * dx) + (dy * dy);
	
	                if (distanceSquared <= bestDistanceSquared) {
	                        bestDistanceSquared = distanceSquared;
	                        best = stop;
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
