package com.team18.gui;

import javafx.scene.Group;
import javafx.scene.input.MouseEvent;

public interface Layer {
	// Render will force the layer to fully re-render its contents,
	// for example due to a layout change.
	public void render(double x, double y, double width, double height);

	// This function should not do any rendering,
	// and its return value should never change.
	public Group getGroup();


	// The event handlers below return true if they wish to block the event
	// for lower stacks (i.e., they have handled it "fully"),
	// and false if the event should be passed down.

	public default boolean mousePressed(MouseEvent event) {
		return false;
	}

	public default boolean mouseClicked(MouseEvent event) {
		return false;
	}

	public default boolean mouseMoved(MouseEvent event) {
		return false;
	}

	public default boolean mouseExited(MouseEvent event) {
		return false;
	}

	public default boolean mouseDragged(MouseEvent event) {
		return false;
	}
}

