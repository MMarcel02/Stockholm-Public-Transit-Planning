package com.team18.gui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.geometry.Bounds;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class Map
{
	private Image img;
	private ImageView view;

	private double dragStartX = 0;
	private double dragStartY = 0;
	private Rectangle2D viewportBeforeDrag;

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

			viewportBeforeDrag = view.getViewport();
		});

		view.setOnMouseDragged(ev -> {
			Rectangle2D viewport = view.getViewport();

			Bounds bounds = view.getBoundsInParent();

			// Convert from screen-space into viewport-space coordinates

			double x = (dragStartX - ev.getSceneX())
				/ bounds.getWidth() * viewport.getWidth();
			double y = (dragStartY - ev.getSceneY())
				/ bounds.getWidth() * viewport.getWidth();

			view.setViewport(new Rectangle2D(
						viewportBeforeDrag.getMinX() + x,
						viewportBeforeDrag.getMinY() + y,
						viewport.getWidth(), viewport.getHeight()));
		});

		view.setOnScroll(ev -> {
			Rectangle2D viewport = view.getViewport();

			// Delta Y is scaled this way to maintain the aspect ratio
			double dx = ev.getDeltaY() * 2;
			double dy = dx / viewport.getWidth() * viewport.getHeight();

			view.setViewport(new Rectangle2D(
						viewport.getMinX(), viewport.getMinY(),
						viewport.getWidth() + dx, viewport.getHeight() + dy));
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


	private void moveView(double x, double y)
	{
		double width = view.getBoundsInParent().getWidth();
		double height = view.getBoundsInParent().getHeight();

		view.setX((x - view.getX()) - width/2);
		view.setY((y - view.getY()) - height/2);
	}
}
