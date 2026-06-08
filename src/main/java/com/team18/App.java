package com.team18;

import java.util.Map;
import java.io.IOException;
import java.io.FileWriter;

import com.team18.gui.GuiApp;
import com.team18.model.Route;
import com.team18.optimizer.Config;
import com.team18.optimizer.Optimizer;
import com.team18.parser.GTFSParser;
import com.team18.parser.PopdistParser;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.raptor.RaptorNetwork;


public class App 
{
	public static void main( String[] args )
	{	
		// GuiApp.main(args);
		try {
			if (args.length != 1) {
				throw new IOException("need 1 argument for csv output :)");
			}
			
			GTFSParser gparser = new GTFSParser();
            gparser.loadFromZip("data/stockholm/sl_center.zip");
			
			RaptorBuilder builder = new RaptorBuilder();
			RaptorNetwork network = builder.build(gparser.agencies, gparser.stops, gparser.routes, gparser.trips, gparser.serviceByCalendar);

			PopdistParser parser = new PopdistParser();
			parser.loadFromCsv("data/stockholm/population.csv");
			
			Optimizer optimizer = new Optimizer(network, parser.demandPointCoordinates, parser.demand);

			long curr = System.currentTimeMillis();
			// optimizer.multiThreadedOptimize();
			Map<String, Double> routeToCostImpact = optimizer.getRouteRemovedToCostImpact(Config.REPRESENTATIVE_WEEKDAY);
			long end = System.currentTimeMillis();
			System.err.println("Time for total optimization multi threaded (mins): " + ((end - curr) / 60000.0));

			FileWriter wr = new FileWriter(args[0]);

			wr.write("routeId,cost\n");
			for (String routeId : routeToCostImpact.keySet()) {
				wr.write(String.format("%s,%f\n", routeId,
							routeToCostImpact.get(routeId)));
			}

			wr.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

