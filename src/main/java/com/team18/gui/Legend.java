package com.team18.gui;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.Label;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.geometry.Pos;

public class Legend {
	VBox vbox;

	List<Item> items = new ArrayList<>();

	public static class Item {
		public final String title;
		public final double[] thresholds;
		public final String[] colors;

		public Item(String title, double[] thresholds, String[] colors) {
			this.title = title;
			this.thresholds = thresholds;
			this.colors = colors;
		}
	}

	public Legend(VBox vbox) {
		this.vbox = vbox;
	}

	public void add(Item item) {
		items.add(item);
		update();
	}

	public void remove(Item item) {
		items.remove(item);
		update();
	}

	void update() {
		vbox.setManaged(true);

		if (!items.isEmpty()) {
			vbox.setVisible(true);
		} else {
			vbox.setVisible(false);
			return;
		}

		vbox.getChildren().clear();

		for (Item item: items) {
			VBox legend = new VBox();
			legend.setSpacing(5);


			Label title = new Label(item.title);
			title.getStyleClass().add("legend-label");

			legend.getChildren().add(title);


			HBox colorBox = new HBox();
			colorBox.setSpacing(10);
			colorBox.setAlignment(Pos.BOTTOM_LEFT);
			addColors(item, colorBox);

			legend.getChildren().add(colorBox);

			vbox.getChildren().add(legend);
		}
	}

	void addColors(Item item, HBox colorBox) {
		for (int i = 0; i < item.thresholds.length; i++) {
			VBox singleColor = new VBox(2);
			singleColor.setAlignment(Pos.BOTTOM_LEFT);

			Rectangle colorBlock = new Rectangle(30, 15);
			colorBlock.setFill(Color.web(item.colors[i]));
			colorBlock.setStroke(Color.BLACK);
			colorBlock.setStrokeWidth(0.5);

			String labelText = (i == item.thresholds.length - 1)
					? (int)item.thresholds[i] + "+"
					: String.valueOf((int)item.thresholds[i]);

			Label label = new Label(labelText);
			label.setStyle("-fx-font-size: 10px;");

			singleColor.getChildren().addAll(colorBlock, label);
			colorBox.getChildren().add(singleColor);
		}
	}
}

