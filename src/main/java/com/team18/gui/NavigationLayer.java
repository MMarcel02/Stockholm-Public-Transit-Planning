package com.team18.gui;

import java.util.List;
import java.util.ArrayList;

import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.shape.Shape;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.Polyline;
import javafx.scene.canvas.GraphicsContext;

import com.team18.gui.Layer;
import com.team18.gui.CoordSystem;
import com.team18.model.RouteStep;
import com.team18.model.Stop;
import com.team18.model.RouteStepType;
import com.team18.model.ShapePoint;
import com.team18.parser.GTFSParser;
import com.team18.util.GeoCalculator;

public class NavigationLayer implements Layer {
	GTFSParser parser;
	Group group = new Group();
	Canvas canvas = new Canvas();

	Circle startMarker = null;
	Circle endMarker = null;

	List<DrawnRoute> drawnRoutes = new ArrayList<>();

	double viewX = 0;
	double viewY = 0;
	double viewWidth = 0;
	double viewHeight = 0;

	public static class Step {
		public String shapeId = null;

		public double latFrom;
		public double lonFrom;
		public double latTo;
		public double lonTo;

		public RouteStepType routeStepType = null;

		public Step(double latFrom, double lonFrom, double latTo, double lonTo,
				String shapeId) {
			this.latFrom = latFrom;
			this.lonFrom = lonFrom;
			this.latTo = latTo;
			this.lonTo = lonTo;
			this.shapeId = shapeId;
		}

		public Step(double latFrom, double lonFrom, double latTo, double lonTo) {
			this.latFrom = latFrom;
			this.lonFrom = lonFrom;
			this.latTo = latTo;
			this.lonTo = lonTo;
		}
	}

	public static class DrawnRoute {
		List<Step> steps;
		Color color = null;

		public DrawnRoute(List<RouteStep> steps, GTFSParser parser) {
			this.steps = new ArrayList<>();

			for (RouteStep rs: steps) {
				double latFrom = rs.latFrom;
				double lonFrom = rs.lonFrom;
				if (rs.fromStop != null) {
					for (Stop stop: parser.stops.values()) {
						if (stop.name.equals(rs.fromStop.name)) {
							latFrom = stop.lat;
							lonFrom = stop.lon;
							break;
						}
					}
				}

				double latTo = rs.latTo;
				double lonTo = rs.lonTo;
				if (rs.toStop != null) {
					for (Stop stop: parser.stops.values()) {
						if (stop.name.equals(rs.toStop.name)) {
							latTo = stop.lat;
							lonTo = stop.lon;
							break;
						}
					}
				}

				Step step = new Step(
					latFrom,
					lonFrom,
					latTo,
					lonTo,
					rs.shapeId
				);

				step.routeStepType = rs.routeStepType;

				this.steps.add(step);
			}
		}

		public DrawnRoute(List<Step> steps, Color color) {
			this.steps = steps;
			this.color = color;
		}
	};

	public NavigationLayer(GTFSParser parser) {
		group.getChildren().add(canvas);
		this.parser = parser;
	}

	public void add(DrawnRoute route) {
		drawnRoutes.add(route);
		render(viewWidth, viewHeight);
	}

	public void remove(DrawnRoute route) {
		drawnRoutes.remove(route);
		render(viewWidth, viewHeight);
	}

	public void setStartMarker(double lat, double lon) {
		if (startMarker == null) {
			startMarker = new Circle(8, Color.web("#4CAF50"));
			group.getChildren().add(startMarker);
		}

		double[] local = CoordSystem.getLocalFromLatLon(lat, lon);
		startMarker.setCenterX(local[0]);
		startMarker.setCenterY(local[1]);
	}

	public void setEndMarker(double lat, double lon) {
		if (endMarker == null) {
			endMarker = new Circle(8, Color.web("#F44336"));
			group.getChildren().add(endMarker);
		}

		double[] local = CoordSystem.getLocalFromLatLon(lat, lon);
		endMarker.setCenterX(local[0]);
		endMarker.setCenterY(local[1]);
	}

	public void shift(double x, double y) {
		viewX = x;
		viewY = y;
		render(viewWidth, viewHeight);
	}

	public void render(double width, double height) {
		viewWidth = width;
		viewHeight = height;

		double canvasWidth = viewWidth + Tile.RESOLUTION * 2;
		double canvasHeight = viewHeight + Tile.RESOLUTION * 2;

		double minX = -viewX - Tile.RESOLUTION;
		double minY = -viewY - Tile.RESOLUTION;
		double maxX = minX + canvasWidth;
		double maxY = minY + canvasHeight;

		canvas.setTranslateX(minX);
		canvas.setTranslateY(minY);
		canvas.setWidth(canvasWidth);
		canvas.setHeight(canvasHeight);

		GraphicsContext gc = canvas.getGraphicsContext2D();
		gc.clearRect(0, 0, canvasWidth, canvasHeight);

		for (DrawnRoute dr: drawnRoutes) {
			for (Step step: dr.steps) {
				boolean usingTransit = (step.routeStepType == RouteStepType.TRANSIT);
				boolean shapeAvailable =
					(step.shapeId != null && parser.shapes != null);

				if (dr.color != null) {
					gc.setStroke(dr.color);
				} else if (usingTransit) {
					gc.setStroke(Color.web("#004a59"));
				} else {
					gc.setStroke(Color.web("#988d10"));
				}

				gc.setLineWidth(3);

				boolean okay = false;

				// In this case we can probably use the shapes from gtfs data
				if (shapeAvailable) {
					okay = buildPolylineSegment(step, gc, minX, minY);
				}

				// If okay is false, then either we are walking,
				// or something went wrong with the shapes.
				//
				// Either way, we need a straight line segment.
				if (!okay) {
					double[] startLocal = CoordSystem.getLocalFromLatLon(
							step.latFrom, step.lonFrom);

					double[] endLocal = CoordSystem.getLocalFromLatLon(
							step.latTo, step.lonTo);

					gc.strokeLine(startLocal[0]-minX, startLocal[1]-minY,
							endLocal[0]-minX, endLocal[1]-minY);
				}
			}
		}
	}

	public Group getGroup() {
		return group;
	}

	boolean buildPolylineSegment(Step step, GraphicsContext gc,
			double minX, double minY) {
		List<ShapePoint> points = parser.shapes.get(step.shapeId);
		if (points == null || points.size() < 2) return false;

		int startIdx = findNearestShapePointIndex(points, step.latFrom, step.lonFrom);
		if (startIdx < 0) return false;

		int endIdx = findNearestShapePointIndex(points, step.latTo, step.lonTo);
		if (endIdx < 0) return false;

		double[] startLocal = CoordSystem.getLocalFromLatLon(step.latFrom, step.lonFrom);

		if (startIdx <= endIdx) {
			for (int i = startIdx; i <= endIdx; i++) {
				ShapePoint point = points.get(i);
				double[] local = CoordSystem.getLocalFromLatLon(point.lat, point.lon);

				gc.strokeLine(startLocal[0]-minX, startLocal[1]-minY,
						local[0]-minX, local[1]-minY);
				startLocal = local;
			}
		} else {
			for (int i = startIdx; i >= endIdx; i--) {
				ShapePoint point = points.get(i);
				double[] local = CoordSystem.getLocalFromLatLon(point.lat, point.lon);

				gc.strokeLine(startLocal[0]-minX, startLocal[1]-minY,
						local[0]-minX, local[1]-minY);
				startLocal = local;
			}
		}


		double[] endLocal = CoordSystem.getLocalFromLatLon(step.latTo, step.lonTo);
		gc.strokeLine(startLocal[0], startLocal[1], endLocal[0], endLocal[1]);

		return true;
	}

	private int findNearestShapePointIndex(List<ShapePoint> points,
			double lat, double lon) {
		int bestIdx = -1;
		double bestDist = Double.POSITIVE_INFINITY;

		for (int i = 0; i < points.size(); i++) {
			ShapePoint point = points.get(i);

			double d = GeoCalculator.calculateEquirectangularDistance(
					lat, lon, point.lat, point.lon);

			if (d < bestDist) {
				bestDist = d;
				bestIdx = i;
			}
		}

		return bestIdx;
	}
}

