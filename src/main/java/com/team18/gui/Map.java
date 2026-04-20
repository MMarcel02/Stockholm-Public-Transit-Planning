package com.team18.gui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.Group;
import javafx.scene.shape.Circle;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;

public class Map {
	// Coordinates of Stockholm
	private double LATITUDE = 59.3293;
	private double LONGITUDE = 18.0686;
	private int ZOOM = 14;
	
	private Group mapGroup;
	private Pane drawingLayer;

	private double dragStartX = 0;
	private double dragStartY = 0;
	private double groupTranslateX = 0;
	private double groupTranslateY = 0;

	public Map() {
		mapGroup = new Group();
		drawingLayer = new Pane();

		// OpenStreetMap Web Mercator Math
		int centerX = (int) Math.floor((LONGITUDE + 180) / 360 * (1 << ZOOM));
		int centerY = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(LATITUDE)) + 1 / Math.cos(Math.toRadians(LATITUDE))) / Math.PI) / 2 * (1 << ZOOM));

		int leftX = centerX - 2;
		int leftY = centerY - 2;
		int rightX = centerX + 2;
		int rightY = centerY + 2;

		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				int tileX = centerX + dx;
				int tileY = centerY + dy;

				String urlString = "https://tile.openstreetmap.org/" + ZOOM + "/" + tileX + "/" + tileY + ".png";

				try {
					// Open a manual connection
					URL url = new URL(urlString);
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();

					// Set the user agent in a way that will prevent OSM
					// from blocking the request
					conn.setRequestProperty("User-Agent", "Team18RoutingApp/1.0 (UniversityProject)");

					// Read the image stream
					InputStream in = conn.getInputStream();
					Image img = new Image(in);
					ImageView view = new ImageView(img);

					// Position the 256x256 tile in the grid
					view.setX((dx + 2) * 256);
					view.setY((dy + 2) * 256);

					mapGroup.getChildren().add(view);

					// Clean up the stream
					in.close();
				} catch (Exception e) {
					System.out.println("Could not load tile: " + urlString);
				}
			}
		}

		mapGroup.getChildren().add(drawingLayer);

		Circle point = new Circle();
		point.setCenterX(0.5*5*256);
		point.setCenterY(0.5*5*256);
		point.setRadius(10);
		drawingLayer.getChildren().add(point);

		mapGroup.setOnMousePressed(ev -> {
			// Save initial coordinates for panning

			dragStartX = ev.getSceneX();
			dragStartY = ev.getSceneY();
			groupTranslateX = mapGroup.getTranslateX();
			groupTranslateY = mapGroup.getTranslateY();
		});

		mapGroup.setOnMouseDragged(ev -> {
			// Continuously update map position while panning

			mapGroup.setTranslateX(groupTranslateX + (ev.getSceneX() - dragStartX));
			mapGroup.setTranslateY(groupTranslateY + (ev.getSceneY() - dragStartY));
		});
	}

	public Group getMapGroup() { return mapGroup; }
	public Pane getDrawingLayer() { return drawingLayer; }
}
