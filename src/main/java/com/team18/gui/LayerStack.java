package com.team18.gui;

import java.util.ArrayList;

import javafx.scene.Group;

import com.team18.parser.GTFSParser;
import com.team18.routing.raptor.RaptorNetwork;

public class LayerStack {
	double dragStartX = 0;
	double dragStartY = 0;
	double groupTranslateX = 0;
	double groupTranslateY = 0;

	public NavigationLayer navLayer;
	public MapLayer mapLayer;
	public HeatmapLayer heatmapLayer;
	public BoundingBoxLayer bbLayer;
	public StopLayer stopLayer;

	ArrayList<Layer> layers = new ArrayList<>();

	Group group = new Group();

	public LayerStack(GTFSParser parser, RaptorNetwork network,
			JourneyInput journeyInput) {
		layers.add(mapLayer = new MapLayer());
		layers.add(heatmapLayer = new HeatmapLayer(network));
		layers.add(bbLayer = new BoundingBoxLayer());
		layers.add(stopLayer = new StopLayer(parser, network, journeyInput));

		for (Layer layer: layers) {
			Group layerGroup = layer.getGroup();
			layerGroup.setMouseTransparent(true);

			group.getChildren().add(layerGroup);
		}

		group.setOnMousePressed(ev -> {
			dragStartX = ev.getSceneX();
			dragStartY = ev.getSceneY();
			groupTranslateX = group.getTranslateX();
			groupTranslateY = group.getTranslateY();

			int delta = 0;
			if (ev.isMiddleButtonDown()) {
				delta = 1;
			} else if (ev.isSecondaryButtonDown()) {
				delta = -1;
			}
			if (delta == 0) return;

			double factor = Math.pow(2, delta);
			if (factor < 0) factor = 1 / (-factor);

			// so that zoom is centered in the middle. (??)
			group.setTranslateX(group.getTranslateX() * factor);
			group.setTranslateY(group.getTranslateY() * factor);

			CoordSystem.setZoomLevel(CoordSystem.getZoomLevel() + delta);

			render(group.getScene().getWidth(), group.getScene().getHeight());

			for (Layer layer: layers) {
				if (layer.mousePressed(ev)) break;
			}
		});

		group.setOnMouseClicked(ev -> {
			for (Layer layer: layers) {
				if (layer.mouseClicked(ev)) break;
			}
		});

		group.setOnMouseMoved(ev -> {
			for (Layer layer: layers) {
				if (layer.mouseMoved(ev)) break;
			}
		});

		group.setOnMouseExited(ev -> {
			for (Layer layer: layers) {
				if (layer.mouseExited(ev)) break;
			}
		});

		group.setOnMouseDragged(ev -> {
			group.setTranslateX(groupTranslateX + (ev.getSceneX() - dragStartX));
			group.setTranslateY(groupTranslateY + (ev.getSceneY() - dragStartY));

			for (Layer layer: layers) {
				if (layer.mouseDragged(ev)) break;
			}
		});
	}

	public void render(double width, double height) {
		double x = group.getTranslateX();
		double y = group.getTranslateY();

		for (Layer layer: layers) {
			layer.render(x, y, width, height);
		}
	}

	public Group getGroup() {
		return group;
	}
}

