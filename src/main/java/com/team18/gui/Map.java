package com.team18.gui;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;

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

    // Zoom settings
    private double scale = 1.0;
    private static final double ZOOM_FACTOR = 1.1;
    private static final double MIN_SCALE = 0.5;
    private static final double MAX_SCALE = 5.0;

    // Tile settings (each tile is 256px, we load a 5x5 grid)
    private static final int TILE_SIZE = 256;
    private static final int TILE_RADIUS = 2;

    // Exact pixel position of Stockholm inside our tile grid
    private double centerLocalX;
    private double centerLocalY;

    // Map Center Tile Reference (Class fields, NOT local variables!)
    private int zoom = 14;
    private int centerTileX;
    private int centerTileY;

    public Map() {
        mapGroup = new Group();
        drawingLayer = new Pane();

        // Set map center to Stockholm
        double lat = 59.3293;
        double lon = 18.0686;

        // NO 'int' here - saving to class field
        zoom = 14;

        // Convert lat/lon into pixel coordinates
        double worldX = (lon + 180) / 360 * (1 << zoom) * TILE_SIZE;
        double worldY = (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom) * TILE_SIZE;

        // NO 'int' here - saving to class fields so getLocalCoords can use them later!
        centerTileX = (int) Math.floor(worldX / TILE_SIZE);
        centerTileY = (int) Math.floor(worldY / TILE_SIZE);

        // How far inside the tile the exact point is
        double pixelOffsetX = worldX - centerTileX * TILE_SIZE;
        double pixelOffsetY = worldY - centerTileY * TILE_SIZE;

        // Download map tiles around Stockholm
        for (int dx = -TILE_RADIUS; dx <= TILE_RADIUS; dx++) {
            for (int dy = -TILE_RADIUS; dy <= TILE_RADIUS; dy++) {
                int tileX = centerTileX + dx;
                int tileY = centerTileY + dy;

                String urlString = "https://tile.openstreetmap.org/" + zoom + "/" + tileX + "/" + tileY + ".png";

                try {
                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                    // Required so OpenStreetMap doesn't block us
                    conn.setRequestProperty("User-Agent", "Team18RoutingApp/1.0");

                    InputStream in = conn.getInputStream();
                    Image img = new Image(in);
                    ImageView view = new ImageView(img);

                    // Place each tile in a grid
                    view.setX((dx + TILE_RADIUS) * TILE_SIZE);
                    view.setY((dy + TILE_RADIUS) * TILE_SIZE);

                    mapGroup.getChildren().add(view);
                    in.close();

                } catch (Exception e) {
                    System.out.println("Could not load tile: " + urlString);
                }
            }
        }

        // Save exact center position (not just tile center)
        centerLocalX = TILE_RADIUS * TILE_SIZE + pixelOffsetX;
        centerLocalY = TILE_RADIUS * TILE_SIZE + pixelOffsetY;

        mapGroup.getChildren().add(drawingLayer);
    }

    // Convert any Lat/Lon into local X/Y coordinates on our drawing layer
    public double[] getLocalCoords(double lat, double lon) {
        // 1. Calculate the absolute world pixel position at this zoom level
        double worldX = (lon + 180) / 360 * (1 << zoom) * TILE_SIZE;
        double worldY = (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom) * TILE_SIZE;

        // 2. Adjust using the class fields (which hold Stockholm's anchor point)
        double localX = worldX - (centerTileX - TILE_RADIUS) * TILE_SIZE;
        double localY = worldY - (centerTileY - TILE_RADIUS) * TILE_SIZE;

        return new double[]{localX, localY};
    }

    // Connect map to UI container and enable interaction
    public void enableInteraction(Pane mapContainer) {

        // Prevent drawing outside the visible map box
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(mapContainer.widthProperty());
        clip.heightProperty().bind(mapContainer.heightProperty());
        mapContainer.setClip(clip);

        // Center the map once the UI is ready
        Platform.runLater(() -> centerMap(mapContainer));

        // Keep map inside bounds when window resizes
        mapContainer.widthProperty().addListener((obs, oldVal, newVal) -> clampToBounds(mapContainer));
        mapContainer.heightProperty().addListener((obs, oldVal, newVal) -> clampToBounds(mapContainer));

        // Dragging (panning)
        mapContainer.setOnMousePressed(ev -> {
            dragStartX = ev.getX();
            dragStartY = ev.getY();
            groupTranslateX = mapGroup.getTranslateX();
            groupTranslateY = mapGroup.getTranslateY();
        });

        mapContainer.setOnMouseDragged(ev -> {
            mapGroup.setTranslateX(groupTranslateX + (ev.getX() - dragStartX));
            mapGroup.setTranslateY(groupTranslateY + (ev.getY() - dragStartY));
            clampToBounds(mapContainer);
        });

        // Zoom using mouse wheel (should zoom towards cursor)
        mapContainer.setOnScroll(ev -> {
            double zoomMultiplier = ev.getDeltaY() > 0 ? ZOOM_FACTOR : 1 / ZOOM_FACTOR;
            double newScale = scale * zoomMultiplier;

            if (newScale < MIN_SCALE || newScale > MAX_SCALE) {
                return;
            }

            double mouseX = ev.getX();
            double mouseY = ev.getY();

            // Find map point under cursor BEFORE zoom
            double localX = (mouseX - mapGroup.getTranslateX()) / scale;
            double localY = (mouseY - mapGroup.getTranslateY()) / scale;

            // Apply zoom
            scale = newScale;
            mapGroup.setScaleX(scale);
            mapGroup.setScaleY(scale);

            // Keep that point under the cursor
            mapGroup.setTranslateX(mouseX - localX * scale);
            mapGroup.setTranslateY(mouseY - localY * scale);

            clampToBounds(mapContainer);
            ev.consume();
        });
    }

    // Center map on Stockholm
    private void centerMap(Pane mapContainer) {
        double w = mapContainer.getWidth();
        double h = mapContainer.getHeight();

        mapGroup.setTranslateX(w / 2 - centerLocalX * scale);
        mapGroup.setTranslateY(h / 2 - centerLocalY * scale);

        clampToBounds(mapContainer);
    }

    // Keep map inside visible area
    private void clampToBounds(Pane mapContainer) {
        Bounds bounds = mapGroup.getBoundsInLocal();

        double w = mapContainer.getWidth();
        double h = mapContainer.getHeight();

        double scaledMinX = bounds.getMinX() * scale;
        double scaledMinY = bounds.getMinY() * scale;
        double scaledWidth = bounds.getWidth() * scale;
        double scaledHeight = bounds.getHeight() * scale;

        double minX, maxX, minY, maxY;

        if (scaledWidth <= w) {
            minX = maxX = (w - scaledWidth) / 2 - scaledMinX;
        } else {
            minX = w - scaledMinX - scaledWidth;
            maxX = -scaledMinX;
        }

        if (scaledHeight <= h) {
            minY = maxY = (h - scaledHeight) / 2 - scaledMinY;
        } else {
            minY = h - scaledMinY - scaledHeight;
            maxY = -scaledMinY;
        }

        double x = Math.max(minX, Math.min(maxX, mapGroup.getTranslateX()));
        double y = Math.max(minY, Math.min(maxY, mapGroup.getTranslateY()));

        mapGroup.setTranslateX(x);
        mapGroup.setTranslateY(y);
    }

    public Group getMapGroup() { return mapGroup; }
    public Pane getDrawingLayer() { return drawingLayer; }
}