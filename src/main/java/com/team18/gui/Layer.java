package com.team18.gui;

import javafx.scene.Group;

public interface Layer {
	public void render(double x, double y, double width, double height);
	public Group getGroup();
}

