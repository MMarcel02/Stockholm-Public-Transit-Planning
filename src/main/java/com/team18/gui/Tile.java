package com.team18.gui;

import com.team18.gui.Landmark;

import java.util.ArrayList;
import java.util.Objects;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;


public class Tile {
	public static final int RESOLUTION = 256;

	// Coordinates that uniquely locate a tile, including its zoom level.
	// A tile is a square on a mercantor projection of the Earth.
	public static class Coord {
		public int x;
		public int y;
		public int zoom;

		public Coord(int x, int y, int zoom) {
			this.x = x;
			this.y = y;
			this.zoom = zoom;
		}

		// Given any latitude and longitude, this function determines
		// which tile they fall into.
		public static Coord fromLatLon(double lat, double lon, int zoom) {
			int x = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
			int y = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));

			return new Coord(x, y, zoom);
		}

		// Gives the four limits (one on each side) of the square tile,
		// in longitude and latitude.
		public Bounds calculateBounds() {
			double n = Math.pow(2, this.zoom);

			return new Bounds(
				(this.x) / n * 360.0 - 180.0, // leftLon
				(this.x + 1) / n * 360.0 - 180.0, // rightLon
				(Math.atan(Math.sinh(Math.PI * (1 - 2 * (this.y) / n)))) * 180.0 / Math.PI, // topLat
				(Math.atan(Math.sinh(Math.PI * (1 - 2 * (this.y + 1) / n)))) * 180.0 / Math.PI // bottomLat
			);
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) return true;
			if (other == null) return false;

			if (this.getClass() != other.getClass()) return false;

			Coord otherCoord = (Coord) other;
			if (otherCoord.x != this.x || otherCoord.y != this.y) return false;
			if (otherCoord.zoom != this.zoom) return false;

			return true;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.x, this.y, this.zoom);
		}
	}

	// Limits of a tile.
	public static class Bounds {
		public double leftLon;
		public double rightLon;
		public double topLat;
		public double bottomLat;

		public Bounds(double leftLon, double rightLon, double topLat, double bottomLat) {
			this.leftLon = leftLon;
			this.rightLon = rightLon;
			this.topLat = topLat;
			this.bottomLat = bottomLat;
		}
	}

	Coord coord;
	Image image;
	ArrayList<Landmark> landmarks = new ArrayList<>();

	Group rendered;

	// Produces the unrendered tile, including its image.
	// This tile can be cached, then rendered into the GUI whenever it is needed.
	public Tile(Coord coord) {
		this.coord = coord;

		String urlString = "https://tile.openstreetmap.org/"
			+ coord.zoom + "/" + coord.x + "/" + coord.y + ".png";

		try {
			// Open a manual connection
			URL url = new URL(urlString);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			// Set the user agent in a way that will prevent OSM
			// from blocking the request
			conn.setRequestProperty("User-Agent", "Team18RoutingApp/1.0 (UniversityProject)");

			// Read the image stream
			InputStream in = conn.getInputStream();
			this.image = new Image(in);

			// Clean up the stream
			in.close();
		} catch (Exception e) {
			System.err.println("Could not load tile: " + urlString);
		}
	}

	public void addLandmark(Landmark landmark) {
		Bounds bounds = this.coord.calculateBounds();

		// This landmark falls outside the bounds of the tile.
		if (landmark.lon < bounds.leftLon || landmark.lon > bounds.rightLon) return;
		if (landmark.lat > bounds.topLat || landmark.lat < bounds.bottomLat) return;

		this.landmarks.add(landmark);
	}

	// Renders the tile and its landmarks into a JavaFX component to be put
	// on the screen.
	public Group render() {
		Group group = new Group();

		ImageView view = new ImageView(this.image);
		group.getChildren().add(view);

		Bounds b = this.coord.calculateBounds();
		Canvas canvas = new Canvas(RESOLUTION, RESOLUTION);
		GraphicsContext gc = canvas.getGraphicsContext2D();

		for (Landmark mark: this.landmarks) {
			// Calculates 0-1 coordinates on both axes, top-left being the origin.
			double percentX = (mark.lon - b.leftLon) / (b.rightLon - b.leftLon);
			double percentY = 1 - ((mark.lat - b.bottomLat) / (b.topLat - b.bottomLat));

			gc.setFill(Color.BLUE);
			gc.fillRect(percentX * RESOLUTION, percentY * RESOLUTION, 4, 4);
		}

		group.getChildren().add(canvas);

		this.rendered = group;
		return group;
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (other == null) return false;

		if (this.getClass() != other.getClass()) return false;

		Tile otherTile = (Tile) other;
		return this.coord.equals(otherTile.coord);
	}

	@Override
	public int hashCode() {
		return this.coord.hashCode();
	}
}

