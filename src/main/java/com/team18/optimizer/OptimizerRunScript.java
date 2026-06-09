package com.team18.optimizer;

import java.io.FileWriter;
import java.util.Map;

import com.team18.parser.GTFSParser;
import com.team18.parser.PopdistParser;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.raptor.RaptorNetwork;

public class OptimizerRunScript 
{
	public static void main( String[] args )
	{	
		try {
			GTFSParser gparser = new GTFSParser();
			gparser.loadFromZip("data/stockholm/sl_center.zip");

			RaptorBuilder builder = new RaptorBuilder();
			RaptorNetwork network = builder.build(gparser.agencies, gparser.stops, gparser.routes, gparser.trips, gparser.serviceByCalendar);

			PopdistParser parser = new PopdistParser();
			parser.loadFromCsv("data/stockholm/population.csv");

			Optimizer optimizer = new Optimizer(network, parser.demandPointCoordinates, parser.demand);

			// ONE RUN FOR ALL ROUTES
			long curr = System.currentTimeMillis();
			Map<String, Double> routeToCostImpact = optimizer.getRouteRemovedToCostImpact(Config.REPRESENTATIVE_WEEKDAY);
			long end = System.currentTimeMillis();
			System.err.println("Time for 1 map calc (mins): " + ((end - curr) / 60000.0));
			
			FileWriter wr = new FileWriter("data/costs/run_final_all_routes.csv");
			wr.write("routeId,cost\n");
			for (String routeId : routeToCostImpact.keySet()) {
				Double value = routeToCostImpact.get(routeId);
				wr.write(String.format("%s,%f\n", routeId, value));
			}
			wr.close();
			
			// BEST 3 With GREEDY
			// long curr = System.currentTimeMillis();
			// Map<String, Double> bestRoutesToDisable = optimizer.multiThreadedOptimize();
			// long end = System.currentTimeMillis();
			// System.err.println("Time for 3 map calcs (mins): " + ((end - curr) / 60000.0));

			// FileWriter wr = new FileWriter("data/costs/run_final_3_best.csv");
			// wr.write("routeId,cost\n");
			// for (String routeId : bestRoutesToDisable.keySet()) {
			// 	Double value = bestRoutesToDisable.get(routeId);
			// 	wr.write(String.format("%s,%f\n", routeId, value));
			// }
			// wr.close();

			// ONE THAT OUTPUTS OTHER DATA TO TO USE IN THE ANALYZER
			// USE FOR FINDING GOOD RATIO + MULTIPLIER
			// wr = new FileWriter("data/costs/run_final_all_routes_testing.csv");
			// curr = System.currentTimeMillis();
			// Map<String, double[]> routeToCostImpactTESTING = optimizer.getRouteCostImpactForAnalysis(Config.REPRESENTATIVE_WEEKDAY);
			// end = System.currentTimeMillis();

			// System.err.println("Time for one map calc (mins): " + ((end - curr) / 60000.0));
			// wr.write("routeId,routeCost,costIncreasePassenger,cost\n");
			// for (String routeId : routeToCostImpactTESTING.keySet()) {
			// 	double[] values = routeToCostImpactTESTING.get(routeId);
			// 	wr.write(String.format("%s,%f,%f,%f\n", routeId, values[0], values[1], values[2]));
			// }
			// wr.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

