package com.team18;

import com.team18.gui.GuiApp;
import com.team18.optimizer.Optimizer;
import com.team18.parser.GTFSParser;
import com.team18.parser.PopdistParser;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.raptor.RaptorNetwork;

import java.io.IOException;

public class App 
{
	public static void main( String[] args )
	{
		// GuiApp.main(args);
		try {
			PopdistParser parser = new PopdistParser();
			parser.loadFromCsv("data/stockholm/poplatlon.csv");

			GTFSParser gparser = new GTFSParser();
            gparser.loadFromZip("data/stockholm/sl_center.zip");
            RaptorBuilder builder = new RaptorBuilder();
            RaptorNetwork network = builder.build(gparser.agencies, gparser.stops, gparser.routes, gparser.trips);

			Optimizer optimizer = new Optimizer(network, parser.demand);
			optimizer.optimize();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

