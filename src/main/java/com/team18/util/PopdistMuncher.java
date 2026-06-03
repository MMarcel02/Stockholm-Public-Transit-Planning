package com.team18.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import com.team18.parser.CSVParser;
import com.team18.parser.CSVParser.Row;
import com.team18.parser.PopdistParser.Point;

public class PopdistMuncher {
	public static void main(String[] args) throws IOException {
		FileReader reader = new FileReader("data/stockholm/pop.csv");
		CSVParser csvp = new CSVParser(new BufferedReader(reader));
		ArrayList<Point> points = new ArrayList<>();

		Row row;
		while ((row = csvp.nextRow()) != null) {
			String northingString = row.getCol("n");
			String eastingString = row.getCol("e");
			String popString = row.getCol("pop");

			double northing;
			double easting;
			int population;
			try {
				northing = Double.parseDouble(northingString);
				easting = Double.parseDouble(eastingString);
				population = Integer.parseInt(popString);
			} catch (NumberFormatException e) {
				throw new IOException("Invalid coordinate format for population area");
			}


			// The dataset we have uses the Swedish national coordinate reference system,
			// specifically the national map projection (SWEREF99TM).
			// - https://www.scb.se/en/services/open-data-api/open-geodata/grid-statistics/
			//
			// I exported this into a CSV with QGIS, then manually edited the CSV using
			// Vim macros to separate the location column into easy-to-parse northing/easting
			// columns.
			// 
			// What follows is the math from this page,
			// adapted slightly for SWEREF99:
			// - https://fypandroid.wordpress.com/2011/09/03/converting-utm-to-latitude-and-longitude-or-vice-versa/

			easting -= 500000;


			// Move the coordinate to the center of the cell
			//northing += 500;
			//easting += 500;
			northing += 1000;

			double a = 6378137;
			double b = 6356752.3142;
			double k0 = 0.9996;

			double e = 0.08;

			// The SWEREF99 uses the meridian 15° east of Greenwich as the origin,
			// according to:
			// - https://www.lantmateriet.se/en/geodata/gps-geodesy-and-swepos/swedish-reference-systems/
			double lon0 = Math.toRadians(15);

			double m = northing/k0;
			double mu = m / (a *
					(1
					 - 1 * Math.pow(e, 2)/4
					 - 3 * Math.pow(e, 4)/64
					 - 5 * Math.pow(e, 6)/256));

			double e1 = (1 - Math.sqrt(1 - e*e))
					/ (1 + Math.sqrt(1 - e*e));

			double j1 = (3    * Math.pow(e1, 1) / 2  - 27 * Math.pow(e1, 3) / 32);
			double j2 = (21   * Math.pow(e1, 2) / 16 - 55 * Math.pow(e1, 4) / 32);
			double j3 = (151  * Math.pow(e1, 3) / 96);
			double j4 = (1097 * Math.pow(e1, 4) / 512);

			double fp = mu
				+ j1*Math.sin(2*mu)
				+ j2*Math.sin(4*mu)
				+ j3*Math.sin(6*mu)
				+ j4*Math.sin(8*mu);
			double sinfp = Math.sin(fp);
			double cosfp = Math.cos(fp);
			double tanfp = Math.tan(fp);

			double ep2 = (e*e)/(1-e*e);
			double c1 = ep2*cosfp*cosfp;
			double t1 = tanfp*tanfp;

			double r1 = a*(1-e*e)/Math.sqrt(Math.pow(1-e*e*sinfp*sinfp, 3));
			double n1 = a/Math.sqrt(1-e*e*sinfp*sinfp);
			double d = easting/(n1*k0);

			double q1 = n1*tanfp/r1;
			double q2 = (d*d)/2;
			double q3 = (5 + 3*t1 + 10*c1 - 4*c1*c1 - 9*ep2) * Math.pow(d, 4) / 24;
			double q4 = (61 + 90*t1 + 298*c1 + 45*t1*t1 - 3*c1*c1 - 252*ep2)
				* Math.pow(d, 6) / 720;
			double q5 = d;
			double q6 = (1 + 2*t1 + c1) * Math.pow(d, 3) / 6;
			double q7 = (5 - 2*c1 + 28*t1 - 3*c1*c1 + e*ep2 + 24*t1*t1)
				* Math.pow(d, 5) / 120;

			double lat = fp - q1 * (q2 - q3 + q4);
			double lon = lon0 + (q5 - q6 + q7) / cosfp;

			points.add(new Point(Math.toDegrees(lat), Math.toDegrees(lon), population));
		}

		reader.close();

		File output = new File("data/stockholm/population.csv");
		BufferedWriter bufw = new BufferedWriter(new FileWriter(output));

		bufw.write("lat,lon,population\n");
		for (Point pt : points) {
			double lat = pt.lat;
			double lon = pt.lon;

			if (lat > StockholmUrbanArea.INNER_MIN_LAT && lat < StockholmUrbanArea.INNER_MAX_LAT && lon > StockholmUrbanArea.INNER_MIN_LON && lon < StockholmUrbanArea.INNER_MAX_LON) {
				bufw.write(String.format("%f,%f,%d\n", pt.lat, pt.lon, pt.population));
			}
		}

		bufw.close();
	}
}

