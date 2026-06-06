package com.team18.gui;

import java.util.ArrayList;

import javafx.scene.Group;
import javafx.scene.input.MouseEvent;

import com.team18.parser.GTFSParser;
import com.team18.routing.raptor.RaptorNetwork;

public class LayerStack implements Layer {
	double dragStartX = 0;
	double dragStartY = 0;
	double groupTranslateX = 0;
	double groupTranslateY = 0;

	public MapLayer mapLayer;
	public HeatmapLayer heatmapLayer;
	public BoundingBoxLayer bbLayer;
	public NavigationLayer navLayer;
	public StopLayer stopLayer;

	ArrayList<Layer> layers = new ArrayList<>();

	Group group = new Group();

	public LayerStack(GTFSParser parser, RaptorNetwork network,
			JourneyInput journeyInput) {
		layers.add(mapLayer = new MapLayer());
		layers.add(heatmapLayer = new HeatmapLayer(network));
		layers.add(bbLayer = new BoundingBoxLayer());
		layers.add(navLayer = new NavigationLayer(parser));
		layers.add(stopLayer = new StopLayer(parser, network, journeyInput));

		for (Layer layer: layers) {
			Group layerGroup = layer.getGroup();
			layerGroup.setMouseTransparent(true);

			group.getChildren().add(layerGroup);
		}
	}

	public void shift(double x, double y) {
		double localX = group.getTranslateX();
		double localY = group.getTranslateY();

		for (Layer layer: layers) {
			layer.shift(localX, localY);
		}
	}

	public void render(double width, double height) {
		for (Layer layer: layers) {
			layer.render(width, height);
		}
	}

	public Group getGroup() {
		return group;
	}

	public boolean mousePressed(MouseEvent event) {
		dragStartX = event.getSceneX();
		dragStartY = event.getSceneY();
		groupTranslateX = group.getTranslateX();
		groupTranslateY = group.getTranslateY();

		int delta = 0;
		if (event.isMiddleButtonDown()) {
			delta = 1;
		} else if (event.isSecondaryButtonDown()) {
			delta = -1;
		}

		if (delta != 0) {
			double factor = Math.pow(2, delta);
			if (factor < 0) factor = 1 / (-factor);

			// so that zoom is centered in the middle. (??)
			group.setTranslateX(group.getTranslateX() * factor);
			group.setTranslateY(group.getTranslateY() * factor);

			CoordSystem.setZoomLevel(CoordSystem.getZoomLevel() + delta);

			shift(0, 0);
			render(group.getScene().getWidth(), group.getScene().getHeight());

			return true;
		}

		for (Layer layer: layers) {
			if (layer.mousePressed(event)) return true;
		}

		return false;
	}

	public boolean mouseClicked(MouseEvent event) {
		for (Layer layer: layers) {
			if (layer.mouseClicked(event)) return true;
		}

		return false;
	}

	public boolean mouseMoved(MouseEvent event) {
		for (Layer layer: layers) {
			if (layer.mouseMoved(event)) return true;
		}

		return false;
	}

	public boolean mouseExited(MouseEvent event) {
		for (Layer layer: layers) {
			if (layer.mouseExited(event)) return true;
		}

		return false;
	}

	public boolean mouseDragged(MouseEvent event) {
		group.setTranslateX(groupTranslateX + (event.getSceneX() - dragStartX));
		group.setTranslateY(groupTranslateY + (event.getSceneY() - dragStartY));

		shift(0, 0);

		return true;
	}
}

