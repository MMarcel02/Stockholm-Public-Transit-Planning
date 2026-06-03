package com.team18.gui;

import javafx.scene.Group;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;

import com.team18.gui.Layer;

public class MapLayer implements Layer {
	Group group = new Group();

	private LinkedHashMap<Tile.Coord, Tile> tiles =
		new LinkedHashMap<Tile.Coord, Tile>(1000, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(
					java.util.Map.Entry<Tile.Coord, Tile> eldest) {
				return size() > 100;
			}
		};

	private ArrayList<Tile> activeTiles = new ArrayList<>();

	public void render(double x, double y, double width, double height) {
		int xoffset = (int) Math.floor(x/ Tile.RESOLUTION);
		int yoffset = (int) Math.floor(y/ Tile.RESOLUTION);

		ArrayList<Tile> newActives = new ArrayList<>();
		for (int relX = -1; relX * Tile.RESOLUTION <= width; relX++) {
			for (int relY = -1; relY * Tile.RESOLUTION <= height; relY++) {
				int absX = -xoffset + relX;
				int absY = -yoffset + relY;

				Tile tile = fetchTile(absX, absY);
				newActives.add(tile);

				if (!activeTiles.contains(tile)) {
					activeTiles.add(tile);

					Group rendered = tile.render();
					rendered.setTranslateX(absX * Tile.RESOLUTION);
					rendered.setTranslateY(absY * Tile.RESOLUTION);
					group.getChildren().add(rendered);
				}
			}
		}

		ArrayList<Tile> toRemove = new ArrayList<>();
		for (Tile tile: activeTiles) {
			if (!newActives.contains(tile)) {
				group.getChildren().removeAll(tile.rendered);
				toRemove.add(tile);
			}
		}

		for (Tile tile: toRemove) {
			activeTiles.remove(tile);
		}
	}

	private Tile fetchTile(int x, int y) {
		Tile.Coord origin = CoordSystem.getOrigin();

		int tileX = origin.x + x;
		int tileY = origin.y + y;

		Tile.Coord coord = new Tile.Coord(origin.x + x, origin.y + y,
				CoordSystem.getZoomLevel());

		Tile tile = tiles.get(coord);

		if (tile == null) {
			tile = new Tile(coord);
			tiles.put(tile.coord, tile);
		}

		return tile;
	}

	public Group getGroup() {
		return group;
	}
}

