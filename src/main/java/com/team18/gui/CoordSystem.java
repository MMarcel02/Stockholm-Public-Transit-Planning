package com.team18.gui;

import java.lang.Math;

import com.team18.gui.Tile;

public final class CoordSystem {
	public static int zoomLevel = 14;

	public static double[] getLatLonFromLocal(double localX, double localY) {
		Tile.Coord origin = getOrigin();

		double exactTileX = (localX / Tile.RESOLUTION) + origin.x;
		double exactTileY = (localY / Tile.RESOLUTION) + origin.y;

		double n = Math.pow(2, zoomLevel);
		double lon = (exactTileX / n) * 360.0 - 180.0;

		double latRad = Math.atan(Math.sinh(Math.PI * (1 - 2 * exactTileY / n)));
		double lat = latRad * 180.0 / Math.PI;

		return new double[]{lat, lon};
	}

	public static double[] getLocalFromLatLon(double lat, double lon) {
		Tile.Coord origin = getOrigin();
		Tile.Coord coord = Tile.Coord.fromLatLon(lat, lon, zoomLevel);
		Tile.Bounds bounds = coord.calculateBounds();
		Tile.Bounds.RelPos pos = bounds.interpolate(lat, lon);

		double localX = (coord.x - origin.x + pos.percentX) * Tile.RESOLUTION;
		double localY = (coord.y - origin.y + pos.percentY) * Tile.RESOLUTION;

		return new double[]{localX, localY};
	}

	private static Tile.Coord getOrigin() {
		return Tile.Coord.fromLatLon(59.3293, 18.0686, zoomLevel);
	}
}

