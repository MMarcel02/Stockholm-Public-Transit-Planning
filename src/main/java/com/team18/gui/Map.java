package com.team18.gui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView; 
import javafx.geometry.Rectangle2D;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;

import java.io.FileInputStream; 
import java.io.FileNotFoundException;

public class Map
{
	private Image img;
	private ImageView view;

	private double dragStartX = 0;
	private double dragStartY = 0;
	private double mapPreDragX = 0;
	private double mapPreDragY = 0;

	public Map()
	{
		try {
			img = new Image(new FileInputStream("./data/map.png"));
			view = new ImageView(img);
		} catch (FileNotFoundException ex) {
			return;
		}

		view.setFitWidth(1200);
		view.setPreserveRatio(true);

		view.setViewport(new Rectangle2D(0, 0, img.getWidth(), img.getHeight()));

		view.setOnMousePressed(ev -> {
			// Save the initial positions

			dragStartX = ev.getSceneX();
			dragStartY = ev.getSceneY();

			mapPreDragX = view.getX();
			mapPreDragY = view.getY();
		});

		view.setOnMouseDragged(ev -> {
			Rectangle2D viewport = view.getViewport();

			// TODO: this is horrendous :))
			// Basically we need a formula to convert from screen pixels to
			// viewport pixels.
			// But before that we need to figure out the proper way to move it
			// in the first place. There is something very off about that.
			double pixelRatio = 0.1;

			view.setViewport(new Rectangle2D(
						viewport.getMinX() - pixelRatio*(ev.getSceneX() - dragStartX),
						viewport.getMinY() - pixelRatio*(ev.getSceneY() - dragStartY),
						viewport.getWidth(),
						viewport.getHeight()));
		});

		view.setOnMouseMoved(ev -> {
		});

		view.setOnScroll(ev -> {
			double delta = ev.getDeltaY() * 2;

			view.setFitWidth(view.getFitWidth() + delta);
		});
	}


	public void handleKey(KeyEvent ev) {
		Rectangle2D viewport = view.getViewport();
		double c = 100;

		switch (ev.getCode()) {
			case KeyCode.A:
				view.setViewport(new Rectangle2D(
							viewport.getMinX() - c, viewport.getMinY(),
							viewport.getWidth(), viewport.getHeight()));
				break;
			case KeyCode.D:
				view.setViewport(new Rectangle2D(
							viewport.getMinX() + c, viewport.getMinY(),
							viewport.getWidth(), viewport.getHeight()));
				break;
			case KeyCode.W:
				view.setViewport(new Rectangle2D(
							viewport.getMinX(), viewport.getMinY() - c,
							viewport.getWidth(), viewport.getHeight()));
				break;
			case KeyCode.S:
				view.setViewport(new Rectangle2D(
							viewport.getMinX(), viewport.getMinY() + c,
							viewport.getWidth(), viewport.getHeight()));
				break;
		}
	}

	public ImageView getNode()
	{
		return view;
	}
}

