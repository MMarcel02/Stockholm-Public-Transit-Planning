package com.team18.parser;

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

import com.team18.parser.CSVParser;
import com.team18.parser.CSVParser.Row;

public class PopdistParser {
	public final double CELL_SIZE_LAT = 0.009070;
	public final double CELL_SIZE_LON = 0.017501;

	public static class Point {
		public double lat;
		public double lon;
		public int population;

		public Point(double lat, double lon, int population) {
			this.lat = lat;
			this.lon = lon;
			this.population = population;
		}
	}

	int[][] grid;

	double minLat = Double.POSITIVE_INFINITY;
	double minLon = Double.POSITIVE_INFINITY;
	double maxLat = 0;
	double maxLon = 0;

	int width = 0;
	int height = 0;

	public void loadFromCsv(String path) throws IOException {
		FileReader reader = new FileReader(path);
		CSVParser csvp = new CSVParser(new BufferedReader(reader));

		ArrayList<Point> points = new ArrayList<>();

		Row row;
		while ((row = csvp.nextRow()) != null) {
			double lat = Double.parseDouble(row.getCol("lat"));
			double lon = Double.parseDouble(row.getCol("lon"));
			int population = Integer.parseInt(row.getCol("population"));

			points.add(new Point(lat, lon, population));
		}

		for (Point pt : points) {
			minLat = Math.min(minLat, pt.lat);
			minLon = Math.min(minLon, pt.lon);
			maxLat = Math.max(maxLat, pt.lat);
			maxLon = Math.max(maxLon, pt.lon);
		}

		width = (int) Math.floor((maxLon - minLon) / CELL_SIZE_LON);
		height = (int) Math.floor((maxLat - minLat) / CELL_SIZE_LAT);

		grid = new int[width+1][height+1];

		for (Point pt : points) {
			int x = (int) Math.floor(width * ((pt.lon - minLon) / (maxLon - minLon)));
			int y = (int) Math.floor(height * ((pt.lat - minLat) / (maxLat - minLat)));
			grid[x][y] = pt.population;
		}
	}

	public int getDensity(double lat, double lon) {
		int x = (int) Math.floor(width * ((lon - minLon) / (maxLon - minLon)));
		int y = (int) Math.floor(height * ((lat - minLat) / (maxLat - minLat)));

		return grid[x][y];
	}
}

