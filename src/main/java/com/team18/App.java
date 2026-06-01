package com.team18;

import com.team18.gui.GuiApp;
import com.team18.parser.PopdistParser;
import java.io.IOException;

public class App 
{
	public static void main( String[] args )
	{
		//GuiApp.main(args);    

		try {
			PopdistParser parser = new PopdistParser();
			parser.loadFromCsv("data/stockholm/pop.csv");
		} catch (IOException ex) {}
	}
}

