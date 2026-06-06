package com.team18.parser;

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

import com.team18.optimizer.Config;
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

	public int[] grid;
	public double[] demandPointCoordinates;
	public int[][] demand;

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
		
		double totalPopulation = 0.0;

		for (Point pt : points) {
			minLat = Math.min(minLat, pt.lat);
			minLon = Math.min(minLon, pt.lon);
			maxLat = Math.max(maxLat, pt.lat);
			maxLon = Math.max(maxLon, pt.lon);
			totalPopulation += pt.population;
		}

		System.err.println("Total population in our inner area: " + totalPopulation);

		width = (int) Math.floor((maxLon - minLon) / CELL_SIZE_LON);
		height = (int) Math.floor((maxLat - minLat) / CELL_SIZE_LAT);
		int aw = width+1;
		int ah = height+1;

		grid = new int[aw*ah];
		demandPointCoordinates = new double[aw*ah*2];

		for (Point pt : points) {
			int x = (int) Math.floor(width * ((pt.lon - minLon) / (maxLon - minLon)));
			int y = (int) Math.floor(height * ((pt.lat - minLat) / (maxLat - minLat)));
			grid[x+aw*y] = pt.population;
			demandPointCoordinates[x+aw*y*2] = pt.lat;
			demandPointCoordinates[x+aw*y*2 + 1] = pt.lon;
		}

		demand = new int[aw*ah][aw*ah];

		double rawTotal = 0.0;

		for (int iy = 0; iy < ah; iy++) {
			for (int ix = 0; ix < aw; ix++) {
				for (int jy = 0; jy < ah; jy++) {
					for (int jx = 0; jx < aw; jx++) {
						int i = ix+aw*iy;
						int j = jx+aw*jy;

						if (i == j) continue;

						double distance = Math.sqrt(
								Math.pow((ix - jx), 2)
								+ Math.pow((iy - jy), 2));

						double k = 2;

						rawTotal += (int) Math.floor(
							(((double) grid[j] * grid[i]))
							/ Math.pow(distance, k));
					}
				}
			}
		}

		double scalingFactor = Config.TRIPS_PER_DAY_PER_PERSON_STOCKHOLM_COUNTY * totalPopulation / rawTotal;
		double totalDemand = 0.0;

		for (int iy = 0; iy < ah; iy++) {
			for (int ix = 0; ix < aw; ix++) {
				for (int jy = 0; jy < ah; jy++) {
					for (int jx = 0; jx < aw; jx++) {
						int i = ix+aw*iy;
						int j = jx+aw*jy;

						if (i == j) continue;

						double distance = Math.sqrt(
								Math.pow((ix - jx), 2)
								+ Math.pow((iy - jy), 2));

						double k = 2;
						
						double percentagePeopleTakingPublicTransit = Config.PERCENTAGE_OF_TRIPS_USING_TRANSIT;
						if (distance < Config.MAX_WALK_DISTANCE_INITIAL_AND_FINAL_METRES) {
							percentagePeopleTakingPublicTransit = Config.PERCENTAGE_OF_TRIPS_USING_TRANSIT_UNDER_THRESHOLD;
						}

						demand[i][j] = (int) Math.round(scalingFactor * ((double) grid[j] * grid[i]) / Math.pow(distance, k) * percentagePeopleTakingPublicTransit);
						totalDemand += demand[i][j];
					}
				}
			}
		}

		System.err.println("Total public transit trips a day: " + totalDemand);
	}


	public int getDensity(double lat, double lon) {
		int x = (int) Math.floor(width * ((lon - minLon) / (maxLon - minLon)));
		int y = (int) Math.floor(height * ((lat - minLat) / (maxLat - minLat)));

		return grid[x+(width+1)*y];
	}
}

