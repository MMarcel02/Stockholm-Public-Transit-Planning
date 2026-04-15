package com.team18.gui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.Group;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;

public class Map {
    private Group mapGroup;
    private Pane drawingLayer;

    private double dragStartX = 0;
    private double dragStartY = 0;
    private double groupTranslateX = 0;
    private double groupTranslateY = 0;

    public Map() {
        mapGroup = new Group();
        drawingLayer = new Pane();

        // 1. SET TO STOCKHOLM, SWEDEN
        double lat = 59.3293; // Stockholm Latitude
        double lon = 18.0686; // Stockholm Longitude
        int zoom = 14;

        // 2. OpenStreetMap Web Mercator Math
        int centerX = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
        int centerY = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));

        // 3. Download the tiles with a custom User-Agent to bypass the 403 Block
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                int tileX = centerX + dx;
                int tileY = centerY + dy;

                String urlString = "https://tile.openstreetmap.org/" + zoom + "/" + tileX + "/" + tileY + ".png";

                try {
                    // Open a manual connection
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                    // THIS IS THE FIX: Identify your app to OSM so they don't block you
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

        // 4. Implement smooth panning
        mapGroup.setOnMousePressed(ev -> {
            dragStartX = ev.getSceneX();
            dragStartY = ev.getSceneY();
            groupTranslateX = mapGroup.getTranslateX();
            groupTranslateY = mapGroup.getTranslateY();
        });

        mapGroup.setOnMouseDragged(ev -> {
            mapGroup.setTranslateX(groupTranslateX + (ev.getSceneX() - dragStartX));
            mapGroup.setTranslateY(groupTranslateY + (ev.getSceneY() - dragStartY));
        });
    }

    public Group getMapGroup() { return mapGroup; }
    public Pane getDrawingLayer() { return drawingLayer; }
}