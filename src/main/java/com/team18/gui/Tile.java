package com.team18.gui;

import java.util.Objects;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Group;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;

public class Tile {
	public static final int RESOLUTION = 256;

	// Coordinates that uniquely locate a tile, including its zoom level.
	// A tile is a square on a mercantor projection of the Earth.
	public static class Coord {
		public static final String CACHE_DIR = "data/tilecache/";

		public int x;
		public int y;
		public int zoom;

		public Coord(int x, int y, int zoom) {
			this.x = x;
			this.y = y;
			this.zoom = zoom;
		}

		public static Coord fromLatLon(double lat, double lon, int zoom) {
			int x = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
			int y = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));

			return new Coord(x, y, zoom);
		}

		public Bounds calculateBounds() {
			double n = Math.pow(2, this.zoom);

			return new Bounds(
				(this.x) / n * 360.0 - 180.0, // leftLon
				(this.x + 1) / n * 360.0 - 180.0, // rightLon
				(Math.atan(Math.sinh(Math.PI * (1 - 2 * (this.y) / n)))) * 180.0 / Math.PI, // topLat
				(Math.atan(Math.sinh(Math.PI * (1 - 2 * (this.y + 1) / n)))) * 180.0 / Math.PI // bottomLat
			);
		}

		public String toFilePath() {
			return CACHE_DIR + zoom + "-" + x + "-" + y + ".png";
		}

		public static Coord fromFileName(String name) {
			String bare = name.substring(0, name.lastIndexOf('.'));
			String[] parts = bare.split("-");

			return new Coord(
				Integer.parseInt(parts[1]),
				Integer.parseInt(parts[2]),
				Integer.parseInt(parts[0])
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

		public static class RelPos {
			public double percentX;
			public double percentY;

			public RelPos(double percentX, double percentY) {
				this.percentX = percentX;
				this.percentY = percentY;
			}
		}

		// Calculates 0-1 coordinates on both axes, top-left being the origin.
		public RelPos interpolate(double lat, double lon) {
			double percentX = (lon - leftLon) / (rightLon - leftLon);
			double percentY = 1 - ((lat - bottomLat) / (topLat - bottomLat));

			return new RelPos(percentX, percentY);
		}
	}

	Coord coord;
	Image image;

	Group rendered;

	// This tile can be cached, then rendered into the GUI whenever it is needed.
	public Tile(Coord coord) {
		this.coord = coord;

		String urlString = "https://tile.openstreetmap.org/"
			+ coord.zoom + "/" + coord.x + "/" + coord.y + ".png";
		String filePath = coord.toFilePath();

		try {
			File cachedFile = new File(filePath);
			if (!cachedFile.exists()) {
				System.err.printf("fetching %d/%d/%d…\n", coord.zoom, coord.x, coord.y);
				URL url = new URL(urlString);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();

				conn.setRequestProperty("User-Agent", "Team18RoutingApp/1.0 (UniversityProject)");

				InputStream in = conn.getInputStream();

				FileOutputStream out = new FileOutputStream(filePath);

				byte[] copyBuffer = new byte[1024];
				int len = in.read(copyBuffer);
				while (len != -1) {
					out.write(copyBuffer, 0, len);
					len = in.read(copyBuffer);
				}

				out.close();
				in.close();
			}

			InputStream in = new FileInputStream(filePath);
			this.image = new Image(in);
			in.close();
		} catch (Exception e) {
			System.err.println("Could not load tile: " + urlString);
		}
	}

	public Group render() {
		Group group = new Group();

		ImageView view = new ImageView(this.image);
		group.getChildren().add(view);

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

