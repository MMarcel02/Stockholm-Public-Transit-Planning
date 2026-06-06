package com.team18.gui;

import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

import com.team18.gui.Layer;
import com.team18.util.StockholmUrbanArea;

public class BoundingBoxLayer implements Layer {
	Canvas canvas = new Canvas();
	Group group = new Group();

	double viewX = 0;
	double viewY = 0;

	public BoundingBoxLayer() {
		group.getChildren().add(canvas);
	}

	public void shift(double x, double y) {
		viewX = x;
		viewY = y;
	}

	public void render(double width, double height) {
		width += Tile.RESOLUTION * 2;
		height += Tile.RESOLUTION * 2;

		double minX = -viewX - Tile.RESOLUTION;
		double minY = -viewY - Tile.RESOLUTION;

		canvas.setTranslateY(minY);
		canvas.setTranslateX(minX);
		canvas.setHeight(height);
		canvas.setWidth(width);

		GraphicsContext gc = canvas.getGraphicsContext2D();
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

	public Group getGroup() {
		return group;
	}
}

