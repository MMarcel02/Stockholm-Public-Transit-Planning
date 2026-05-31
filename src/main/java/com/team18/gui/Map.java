package com.team18.gui;

import com.team18.parser.GTFSParser;
import com.team18.model.RouteStep;
import com.team18.model.RouteStepType;
import com.team18.model.ShapePoint;
import com.team18.util.GeoCalculator;
import com.team18.util.StockholmUrbanArea;

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
	private Group tileGroup;
	private Canvas stopCanvas;
	private Canvas boundingBoxCanvas;
	private Group routeGroup;
	private double dragStartX = 0;
	private double dragStartY = 0;
	private double groupTranslateX = 0;
	private double groupTranslateY = 0;

	private int zoomLevel = 14;
	private final int maxZoom = 16;

	private LinkedHashMap<Tile.Coord, Tile> tiles = new LinkedHashMap<Tile.Coord, Tile>(1000, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<Tile.Coord, Tile> eldest) {
			return size() > 100;
		}
	};
	private ArrayList<Tile> activeTiles = new ArrayList<>();

	private GTFSParser parser;
	private ArrayList<Landmark> stopLandmarks = new ArrayList<>();

	private FullRoute route = null;
	private double startMarkerLat = Double.NaN;
	private double startMarkerLon = Double.NaN;
	private double endMarkerLat = Double.NaN;
	private double endMarkerLon = Double.NaN;

	private Circle startMarker = new Circle(8, Color.web("#4CAF50")); // Green for Start
	private Circle endMarker = new Circle(8, Color.web("#F44336"));   // Red for End

	public Map(GTFSParser parser) {
		this.parser = parser;

		mapGroup = new Group();

		tileGroup = new Group();
		mapGroup.getChildren().add(tileGroup);

		boundingBoxCanvas = new Canvas();
		boundingBoxCanvas.setMouseTransparent(true);
		mapGroup.getChildren().add(boundingBoxCanvas);

		stopCanvas = new Canvas();
		stopCanvas.setMouseTransparent(true);
		mapGroup.getChildren().add(stopCanvas);

		routeGroup = new Group();
		mapGroup.getChildren().add(routeGroup);
		startMarker.setVisible(false);
		startMarker.setStroke(Color.WHITE);
		startMarker.setStrokeWidth(2);

		endMarker.setVisible(false);
		endMarker.setStroke(Color.WHITE);
		endMarker.setStrokeWidth(2);

		routeGroup.getChildren().addAll(startMarker, endMarker);

		for (var stop: parser.stops.values()) {
			stopLandmarks.add(new Landmark(stop.lat, stop.lon, stop.id, stop.name));
		}

		File cacheDir = new File(Tile.Coord.CACHE_DIR);
		File[] files = cacheDir.listFiles();
		if (files != null) {
			for (File file: files) {
				constructNewTile(Tile.Coord.fromFileName(file.getName()));
			}
		}

		refresh();

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

			this.zoomLevel += delta;
			if (this.zoomLevel > maxZoom) this.zoomLevel = maxZoom;

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

			this.zoomLevel += delta;
			if (this.zoomLevel > maxZoom) this.zoomLevel = maxZoom;

			refresh();
		});
	}
	public Landmark findStopNearLocal(double localX, double localY, double radiusPixels) {
		if (zoomLevel < 13) return null;

		double bestDistanceSquared = radiusPixels * radiusPixels;
		Landmark best = null;

		for (Landmark landmark : stopLandmarks) {
			double[] local = getLocalFromLatLon(landmark.lat, landmark.lon);
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
	public double[] getLatLonFromLocal(double localX, double localY) {
		Tile.Coord origin = getOrigin();
		double exactTileX = (localX / Tile.RESOLUTION) + origin.x;
		double exactTileY = (localY / Tile.RESOLUTION) + origin.y;
		double n = Math.pow(2, zoomLevel);
		double lon = (exactTileX / n) * 360.0 - 180.0;
		double latRad = Math.atan(Math.sinh(Math.PI * (1 - 2 * exactTileY / n)));
		double lat = latRad * 180.0 / Math.PI;
		return new double[]{lat, lon};
	}

	public double[] getLocalFromLatLon(double lat, double lon) {
		Tile.Coord origin = getOrigin();
		Tile.Coord coord = Tile.Coord.fromLatLon(lat, lon, zoomLevel);
		Tile.Bounds bounds = coord.calculateBounds();
		Tile.Bounds.RelPos pos = bounds.interpolate(lat, lon);

		double localX = (coord.x - origin.x + pos.percentX) * Tile.RESOLUTION;
		double localY = (coord.y - origin.y + pos.percentY) * Tile.RESOLUTION;

		return new double[]{localX, localY};
	}

	public void setStartMarker(double lat, double lon) {
		this.startMarkerLat = lat;
		this.startMarkerLon = lon;
		refresh();
	}

	public void setEndMarker(double lat, double lon) {
		this.endMarkerLat = lat;
		this.endMarkerLon = lon;
		refresh();
	}


	public void setRoute(FullRoute route) {
		this.route = route;
		refresh();
	}

	private void refreshTiles() {
		int xoffset = (int) Math.floor(mapGroup.getTranslateX() / Tile.RESOLUTION);
		int yoffset = (int) Math.floor(mapGroup.getTranslateY() / Tile.RESOLUTION);

		// TODO: We need the actual size of the visible window so that these
		// are more precise/less wasteful/don't break down on windows bigger than this :)
		double width = getViewportWidth();
		double height = getViewportHeight();

		ArrayList<Tile> newActives = new ArrayList<>();
		for (int relX = -1; relX * Tile.RESOLUTION <= width; relX++) {
			for (int relY = -1; relY * Tile.RESOLUTION <= height; relY++) {
				int absX = -xoffset + relX;
				int absY = -yoffset + relY;

				Tile tile = fetchTile(absX, absY);
				newActives.add(tile);

				if (!this.activeTiles.contains(tile)) {
					this.activeTiles.add(tile);

					Group rendered = tile.render();
					rendered.setTranslateX(absX * Tile.RESOLUTION);
					rendered.setTranslateY(absY * Tile.RESOLUTION);
					tileGroup.getChildren().add(rendered);
				}
			}
		}

		ArrayList<Tile> toRemove = new ArrayList<>();
		for (Tile tile: this.activeTiles) {
			if (!newActives.contains(tile)) {
				tileGroup.getChildren().removeAll(tile.rendered);
				toRemove.add(tile);
			}
		}

		for (Tile tile: toRemove) {
			this.activeTiles.remove(tile);
		}
	}

	private void refreshStops() {
		double width = getViewportWidth() + (Tile.RESOLUTION * 2);
		double height = getViewportHeight() + (Tile.RESOLUTION * 2);
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

		if (zoomLevel < 13) return;

		double radius = zoomLevel >= 15 ? 4.5 : 3.5;
		gc.setFill(Color.web("#E91E63", 0.80));
		gc.setStroke(Color.WHITE);
		gc.setLineWidth(1.0);

		for (Landmark landmark : stopLandmarks) {
			double[] local = getLocalFromLatLon(landmark.lat, landmark.lon);
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

	private void refreshRoute() {
		routeGroup.getChildren().clear();
		routeGroup.getChildren().addAll(startMarker, endMarker);
		if (!Double.isNaN(startMarkerLat)) {
			double[] local = getLocalFromLatLon(startMarkerLat, startMarkerLon);
			startMarker.setCenterX(local[0]);
			startMarker.setCenterY(local[1]);
			startMarker.setVisible(true);
		}

		if (!Double.isNaN(endMarkerLat)) {
			double[] local = getLocalFromLatLon(endMarkerLat, endMarkerLon);
			endMarker.setCenterX(local[0]);
			endMarker.setCenterY(local[1]);
			endMarker.setVisible(true);
		}
		if (route == null) return;

		double lat = route.startLat;
		double lon = route.startLon;
		for (RouteStep step: route.steps) {
			int size = (int) Math.pow((double)zoomLevel / 10, 4);
			if (step.routeStepType == RouteStepType.TRANSIT && step.shapeId != null && parser.shapes != null) {
				Polyline poly = buildShapeSegmentPolyline(step.shapeId, lat, lon, step.latTo, step.lonTo);
				if (poly != null) {
					poly.getStyleClass().add("route-line");
					poly.getStyleClass().add("route-line-transit");
					poly.setStrokeLineCap(StrokeLineCap.ROUND);
					poly.setStrokeWidth(size);
					routeGroup.getChildren().add(poly);
				} else {
					routeGroup.getChildren().add(buildStraightLine(lat, lon, step.latTo, step.lonTo, size, false));
				}
			} else {
				routeGroup.getChildren().add(buildStraightLine(lat, lon, step.latTo, step.lonTo, size, true));
			}

			lat = step.latTo;
			lon = step.lonTo;
		}
	}

	private void refreshBoundingBox() {
		double width = getViewportWidth() + (Tile.RESOLUTION * 2);
		double height = getViewportHeight() + (Tile.RESOLUTION * 2);
		double minX = -mapGroup.getTranslateX() - Tile.RESOLUTION;
		double minY = -mapGroup.getTranslateY() - Tile.RESOLUTION;

		boundingBoxCanvas.setTranslateY(minY);
		boundingBoxCanvas.setTranslateX(minX);
		boundingBoxCanvas.setHeight(height);
		boundingBoxCanvas.setWidth(width);

		GraphicsContext gc = boundingBoxCanvas.getGraphicsContext2D();
		gc.clearRect(0, 0, width, height);


		double[] outerTopLeft = getLocalFromLatLon(
			StockholmUrbanArea.OUTER_MAX_LAT, 
			StockholmUrbanArea.OUTER_MIN_LON
		);
		
		double[] outerBottomRight = getLocalFromLatLon(
			StockholmUrbanArea.OUTER_MIN_LAT, 
			StockholmUrbanArea.OUTER_MAX_LON
		);

		double[] innerTopLeft = getLocalFromLatLon(
			StockholmUrbanArea.INNER_MAX_LAT, 
			StockholmUrbanArea.INNER_MIN_LON
		);
		
		double[] innerBottomRight = getLocalFromLatLon(
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

	

	private Line buildStraightLine(double startLat, double startLon, double endLat, double endLon, int strokeWidth, boolean walking) {
		Line line = new Line();
		line.getStyleClass().add("route-line");
		line.getStyleClass().add(walking ? "route-line-walk" : "route-line-transit");
		line.setStrokeLineCap(StrokeLineCap.ROUND);
		line.setStrokeWidth(strokeWidth);

		double[] startLocal = getLocalFromLatLon(startLat, startLon);
		double[] endLocal = getLocalFromLatLon(endLat, endLon);
		line.setStartX(startLocal[0]);
		line.setStartY(startLocal[1]);
		line.setEndX(endLocal[0]);
		line.setEndY(endLocal[1]);

		return line;
	}

	// Builds a Polyline segment for a transit leg using GTFS shapes.txt.
	// We approximate the segment by taking the points between the nearest shape points to start and end.
	private Polyline buildShapeSegmentPolyline(String shapeId, double startLat, double startLon, double endLat, double endLon) {
		List<ShapePoint> pts = parser.shapes.get(shapeId);
		if (pts == null || pts.size() < 2) return null;

		int startIdx = findNearestShapePointIndex(pts, startLat, startLon);
		int endIdx = findNearestShapePointIndex(pts, endLat, endLon);
		if (startIdx < 0 || endIdx < 0) return null;

		Polyline poly = new Polyline();

		// Anchor the polyline to the exact stop coordinates so we don't end up slightly "off-stop".
		double[] startLocal = getLocalFromLatLon(startLat, startLon);
		poly.getPoints().addAll(startLocal[0], startLocal[1]);

		if (startIdx <= endIdx) {
			for (int i = startIdx; i <= endIdx; i++) {
				ShapePoint p = pts.get(i);
				double[] local = getLocalFromLatLon(p.lat, p.lon);
				poly.getPoints().addAll(local[0], local[1]);
			}
		} else {
			for (int i = startIdx; i >= endIdx; i--) {
				ShapePoint p = pts.get(i);
				double[] local = getLocalFromLatLon(p.lat, p.lon);
				poly.getPoints().addAll(local[0], local[1]);
			}
		}

		double[] endLocal = getLocalFromLatLon(endLat, endLon);
		poly.getPoints().addAll(endLocal[0], endLocal[1]);

		// Need at least 2 points (4 doubles) for a polyline.
		if (poly.getPoints().size() < 4) return null;

		return poly;
	}

	private int findNearestShapePointIndex(List<ShapePoint> pts, double lat, double lon) {
		int bestIdx = -1;
		double bestDist = Double.POSITIVE_INFINITY;

		for (int i = 0; i < pts.size(); i++) {
			ShapePoint p = pts.get(i);
			double d = GeoCalculator.calculateEquirectangularDistance(lat, lon, p.lat, p.lon);
			if (d < bestDist) {
				bestDist = d;
				bestIdx = i;
			}
		}

		return bestIdx;
	}

	public void refresh() {
		refreshTiles();
		refreshBoundingBox();
		refreshStops();
		refreshRoute();
	}

	private double getViewportWidth() {
		if (mapGroup.getScene() != null) {
			return mapGroup.getScene().getWidth();
		}
		return 1920;
	}

	private double getViewportHeight() {
		if (mapGroup.getScene() != null) {
			return mapGroup.getScene().getHeight();
		}
		return 1080;
	}

	private Tile.Coord getOrigin() {
		return Tile.Coord.fromLatLon(59.3293, 18.0686, zoomLevel);
	}

	private Tile fetchTile(int x, int y) {
		Tile.Coord origin = getOrigin();

		int tileX = origin.x + x;
		int tileY = origin.y + y;

		Tile.Coord coord = new Tile.Coord(origin.x + x, origin.y + y, zoomLevel);

		Tile tile = this.tiles.get(coord);
		if (tile == null) {
			tile = constructNewTile(coord);
		}

		return tile;
	}

	private Tile constructNewTile(Tile.Coord coord) {
		Tile tile = new Tile(coord);

		this.tiles.put(tile.coord, tile);

		return tile;
	}

	public Group getMapGroup() { return mapGroup; }
}
