package com.team18.gui;

import javafx.scene.shape.Circle;

// A point of interest on the map.
// Shop, bus station, public building, etc.
public class Landmark {
	// In the future this will have other things,
	// such as type and size/importance.
	public double lat;
	public double lon;

	public Landmark(double lat, double lon) {
		this.lat = lat;
		this.lon = lon;
	}

	public Circle render() {
		Circle point = new Circle();
		point.setRadius(5);

		return point;
	}
}

