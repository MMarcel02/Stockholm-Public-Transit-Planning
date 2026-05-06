package com.team18.gui;

import com.team18.gui.Tile;
import com.team18.gui.Landmark;
import com.team18.gui.FullRoute;
import com.team18.parser.GTFSParser;
import com.team18.model.RouteStep;

import java.io.File;
import java.util.HashMap;
import java.util.ArrayList;

import java.lang.Math;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.Group;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.paint.Color;

import java.io.FileNotFoundException;
import java.nio.file.NoSuchFileException;
import java.util.zip.ZipException;
import java.io.IOException;

public class Map {
	// This group contains the tiles that are currently visible.
	private Group mapGroup;
	private Group tileGroup;
	private Group routeGroup;

	private double dragStartX = 0;
	private double dragStartY = 0;
	private double groupTranslateX = 0;
	private double groupTranslateY = 0;

	private int zoomLevel = 14;

	private HashMap<Tile.Coord, Tile> tiles = new HashMap<>();
	private ArrayList<Tile> activeTiles = new ArrayList<>();

	private GTFSParser parser;

	private FullRoute route = null;

	public Map(GTFSParser parser) {
		this.parser = parser;

		mapGroup = new Group();

		tileGroup = new Group();
		mapGroup.getChildren().add(tileGroup);

		routeGroup = new Group();
		mapGroup.getChildren().add(routeGroup);

		File cacheDir = new File(Tile.Coord.CACHE_DIR);
		File[] files = cacheDir.listFiles();
		if (files != null) {
			for (File file: files) {
				constructNewTile(Tile.Coord.fromFileName(file.getName()));
			}
		}

		refresh();

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

			refreshTiles();
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

			refresh();
		});
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
		double width = 1920;
		double height = 1080;

		// A list that _exclusively_ contains the new active tiles.
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

		// Cull tiles that are off-screen.
		ArrayList<Tile> toRemove = new ArrayList<>();
		for (Tile tile: this.activeTiles) {
			if (!newActives.contains(tile)) {
				// This tile is off-screen now.
				tileGroup.getChildren().removeAll(tile.rendered);
				toRemove.add(tile);
			}
		}

		for (Tile tile: toRemove) {
			this.activeTiles.remove(tile);
		}
	}

	private void refreshRoute() {
		routeGroup.getChildren().clear();
		if (route == null) return;

		Tile.Coord origin = getOrigin();

		double lat = route.startLat;
		double lon = route.startLon;
		for (RouteStep step: route.steps) {
			Line line = new Line();
			line.setFill(Color.ORANGE);

			int size = (int) Math.pow((double)zoomLevel / 10, 4);
			line.setStrokeWidth(size);

			Tile.Coord startCoord = Tile.Coord.fromLatLon(lat, lon, zoomLevel);
			Tile.Bounds startBounds = startCoord.calculateBounds();
			Tile.Bounds.RelPos startPos = startBounds.interpolate(lat, lon);
			line.setStartX((startCoord.x - origin.x + startPos.percentX) * Tile.RESOLUTION);
			line.setStartY((startCoord.y - origin.y + startPos.percentY) * Tile.RESOLUTION);

			Tile.Coord endCoord = Tile.Coord.fromLatLon(step.latTo, step.lonTo, zoomLevel);
			Tile.Bounds endBounds = endCoord.calculateBounds();
			Tile.Bounds.RelPos endPos = endBounds.interpolate(step.latTo, step.lonTo);
			line.setEndX((endCoord.x - origin.x + endPos.percentX) * Tile.RESOLUTION);
			line.setEndY((endCoord.y - origin.y + endPos.percentY) * Tile.RESOLUTION);

			routeGroup.getChildren().add(line);

			lat = step.latTo;
			lon = step.lonTo;
		}
	}

	public void refresh() {
		refreshTiles();
		refreshRoute();
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

		for (var stop: parser.stops.values()) {
			tile.addLandmark(new Landmark(stop.lat, stop.lon));
		}

		return tile;
	}

	public Group getMapGroup() { return mapGroup; }
}
