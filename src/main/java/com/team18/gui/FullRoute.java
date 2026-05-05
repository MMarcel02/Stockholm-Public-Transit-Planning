package com.team18.gui;

import java.util.List;

import com.team18.model.RouteStep;

public class FullRoute {
	public double startLat;
	public double startLon;
	public List<RouteStep> steps;

	public FullRoute(double startLat, double startLon, List<RouteStep> steps) {
		this.startLat = startLat;
		this.startLon = startLon;
		this.steps = steps;
	}
}

