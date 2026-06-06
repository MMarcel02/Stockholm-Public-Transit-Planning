package com.team18;

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
			PopdistParser parser = new PopdistParser();
			parser.loadFromCsv("data/stockholm/population.csv");

			GTFSParser gparser = new GTFSParser();

            gparser.loadFromZip("data/stockholm/sl_center.zip");
            RaptorBuilder builder = new RaptorBuilder();
            RaptorNetwork network = builder.build(gparser.agencies, gparser.stops, gparser.routes, gparser.trips, gparser.serviceByCalendar);

			Optimizer optimizer = new Optimizer(network, parser.demandPointCoordinates, parser.demand);

			long curr = System.currentTimeMillis();
			optimizer.multiThreadedOptimize();
			long end = System.currentTimeMillis();
			System.out.println("Time for total optimization multi threaded (mins): " + ((end - curr) / 60000.0));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

