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
			Map<String, Double> bestRoutesToDisable = optimizer.multiThreadedOptimize();
			long end = System.currentTimeMillis();
			System.err.println("Time for 3 map calcs (mins): " + ((end - curr) / 60000.0));

			curr = System.currentTimeMillis();
			Map<String, Double> routeToCostImpact = optimizer.getRouteRemovedToCostImpact(Config.REPRESENTATIVE_WEEKDAY);
			end = System.currentTimeMillis();
			System.err.println("Time for one map calc (mins): " + ((end - curr) / 60000.0));
			
			FileWriter wr = new FileWriter("data/costs/run7.csv");

			wr.write("routeId,cost\n");
			for (String routeId : routeToCostImpact.keySet()) {
				wr.write(String.format("%s,%f\n", routeId,
							routeToCostImpact.get(routeId)));
			}

			wr.close();

			wr = new FileWriter("data/costs/run7Best3RoutesByGreedy.csv");

			wr.write("routeId,cost\n");
			for (String routeId : bestRoutesToDisable.keySet()) {
				wr.write(String.format("%s,%f\n", routeId,
							bestRoutesToDisable.get(routeId)));
			}

			wr.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

