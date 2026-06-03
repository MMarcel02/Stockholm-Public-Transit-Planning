package com.team18.gui;

import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import com.team18.parser.GTFSParser;
import com.team18.model.Stop;

public class StopLayer implements Layer {
	Canvas canvas = new Canvas();
	Group group = new Group();

	GTFSParser parser;

	public StopLayer(GTFSParser parser) {
		group.getChildren().add(canvas);
		this.parser = parser;
	}

	public void render(double x, double y, double width, double height) {
		width  += Tile.RESOLUTION * 2;
		height += Tile.RESOLUTION * 2;

		double minX = -x - Tile.RESOLUTION;
		double minY = -y - Tile.RESOLUTION;
		double maxX = minX + width;
		double maxY = minY + height;

		canvas.setTranslateX(minX);
		canvas.setTranslateY(minY);
		canvas.setWidth(width);
		canvas.setHeight(height);

		GraphicsContext gc = canvas.getGraphicsContext2D();
		gc.clearRect(0, 0, width, height);

		double radius = 3.5;
		if (CoordSystem.getZoomLevel() >= 15) radius = 4.5;

		gc.setFill(Color.web("#E91E63", 0.8));
		gc.setStroke(Color.WHITE);
		gc.setLineWidth(1);

		for (Stop stop: parser.stops.values()) {
			double[] local = CoordSystem.getLocalFromLatLon(stop.lat, stop.lon);

			if (local[0] < minX
					|| local[0] > maxX
					|| local[1] < minY
					|| local[1] > maxY) {
				continue;
			}

			double canvasX = local[0] - minX;
			double canvasY = local[1] - minY;

			double diameter = radius * 2;

			gc.fillOval(canvasX - radius, canvasY - radius, diameter, diameter);
			gc.strokeOval(canvasX - radius, canvasY - radius, diameter, diameter);
		}
	}

	public Group getGroup() {
		return group;
	}
}

