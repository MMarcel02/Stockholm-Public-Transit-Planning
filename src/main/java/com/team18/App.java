package com.team18;

import com.team18.gui.GuiApp;
import com.team18.parser.PopdistParser;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;

public class App 
{
	public static void main( String[] args )
	{
		//GuiApp.main(args);    

		try {
			PopdistParser parser = new PopdistParser();
			parser.loadFromCsv("data/stockholm/pop.csv");

			File output = new File("data/stockholm/poplatlon.csv");
			BufferedWriter bufw = new BufferedWriter(new FileWriter(output));

			bufw.write("lat,lon,population\n");
			for (PopdistParser.Point pt : parser.points) {
				bufw.write(String.format("%f,%f,%d\n", pt.lat, pt.lon, pt.population));
			}

			bufw.close();
		} catch (IOException ex) {}
	}
}

