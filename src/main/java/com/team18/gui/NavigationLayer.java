package com.team18.gui;

import java.util.List;
import java.util.ArrayList;

import javafx.scene.Group;
import javafx.scene.shape.Shape;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.Polyline;

import com.team18.gui.Layer;
import com.team18.gui.CoordSystem;
import com.team18.model.RouteStep;
import com.team18.model.RouteStepType;
import com.team18.model.ShapePoint;
import com.team18.parser.GTFSParser;
import com.team18.util.GeoCalculator;

public class NavigationLayer implements Layer {
	GTFSParser parser;
	Group group = new Group();

	Circle startMarker = null;
	Circle endMarker = null;

	List<DrawnRoute> drawnRoutes = new ArrayList<>();

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

		public DrawnRoute(List<RouteStep> steps) {
			this.steps = new ArrayList<>();

			for (RouteStep rs: steps) {
				Step step = new Step(
						rs.latFrom,
						rs.lonFrom,
						rs.latTo,
						rs.lonTo,
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

	public void shift(double x, double y) {}

	public void render(double width, double height) {
		viewWidth = width;
		viewHeight = height;

		group.getChildren().clear();

		for (DrawnRoute dr: drawnRoutes) {
			for (Step step: dr.steps) {
				Shape segment = null;

				boolean usingTransit = (step.routeStepType == RouteStepType.TRANSIT);
				boolean shapeAvailable =
					(step.shapeId != null && parser.shapes != null);

				// In this case we can probably use the shapes from gtfs data
				if (shapeAvailable) {
					segment = buildPolylineSegment(step);
				}

				// If it's still null, then either we are walking,
				// or something went wrong building the polyline.
				//
				// Either way, we need a straight line segment.
				if (segment == null) {
					Line line = new Line();

					double[] startLocal = CoordSystem.getLocalFromLatLon(
							step.latFrom, step.lonFrom);
					line.setStartX(startLocal[0]);
					line.setStartY(startLocal[1]);

					double[] endLocal = CoordSystem.getLocalFromLatLon(
							step.latTo, step.lonTo);
					line.setEndX(endLocal[0]);
					line.setEndY(endLocal[1]);

					segment = line;
				}

				segment.setStrokeLineCap(StrokeLineCap.ROUND);
				segment.setStrokeWidth(4);

				segment.getStyleClass().add("route-line");

				if (dr.color != null) {
					segment.setStroke(dr.color);
				} else if (usingTransit) {
					segment.getStyleClass().add("route-line-transit");
				} else {
					segment.getStyleClass().add("route-line-walk");
				}

				group.getChildren().add(segment);
			}
		}
	}

	public Group getGroup() {
		return group;
	}

	private Polyline buildPolylineSegment(Step step) {
		List<ShapePoint> points = parser.shapes.get(step.shapeId);
		if (points == null || points.size() < 2) return null;

		int startIdx = findNearestShapePointIndex(points, step.latFrom, step.lonFrom);
		if (startIdx < 0) return null;

		int endIdx = findNearestShapePointIndex(points, step.latTo, step.lonTo);
		if (endIdx < 0) return null;

		Polyline poly = new Polyline();

		double[] startLocal = CoordSystem.getLocalFromLatLon(step.latFrom, step.lonFrom);
		poly.getPoints().addAll(startLocal[0], startLocal[1]);

		if (startIdx <= endIdx) {
			for (int i = startIdx; i <= endIdx; i++) {
				ShapePoint point = points.get(i);

				double[] local = CoordSystem.getLocalFromLatLon(point.lat, point.lon);
				poly.getPoints().addAll(local[0], local[1]);
			}
		} else {
			for (int i = startIdx; i >= endIdx; i--) {
				ShapePoint point = points.get(i);

				double[] local = CoordSystem.getLocalFromLatLon(point.lat, point.lon);
				poly.getPoints().addAll(local[0], local[1]);
			}
		}


		double[] endLocal = CoordSystem.getLocalFromLatLon(step.latTo, step.lonTo);
		poly.getPoints().addAll(endLocal[0], endLocal[1]);

		// Need at least 2 points (4 pairs) for a polyline.
		if (poly.getPoints().size() < 4) return null;

		return poly;
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

