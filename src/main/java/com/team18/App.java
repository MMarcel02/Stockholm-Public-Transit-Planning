package com.team18;

import java.io.FileWriter;
import java.util.Map;

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
		//GuiApp.main(args);
		try {

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

			FileWriter wr = new FileWriter("data/costs/run6.csv");

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

