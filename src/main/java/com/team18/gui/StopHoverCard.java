package com.team18.gui;

import java.util.List;

import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Button;
import javafx.scene.control.OverrunStyle;

import com.team18.model.Stop;
import com.team18.routing.raptor.RaptorNetwork;

public class StopHoverCard {
	Stop currentStop = null;
	VBox card = new VBox();

	JourneyInput journeyInput;

	public StopHoverCard(JourneyInput journeyInput, RaptorNetwork network) {
		card.getStyleClass().add("stop-hover-card");
		card.setVisible(false);
		card.setMouseTransparent(false);
		card.setPrefWidth(CARD_WIDTH);
		card.setMinWidth(CARD_WIDTH);
		card.setMaxWidth(CARD_WIDTH);
		card.setOnMouseExited(ev -> hide());

		this.journeyInput = journeyInput;
	}

	public void show(Stop stop, List<String> arrivals,
			double viewWidth, double viewHeight,
			double mouseX, double mouseY) {
		if (stop == currentStop && card.isVisible()) {
			position(viewWidth, viewHeight, mouseX, mouseY);
			return;
		}

		currentStop = stop;
		card.getChildren().clear();


		Label name = new Label(stop.name);
		name.getStyleClass().add("stop-hover-title");
		name.setWrapText(true);
		name.setPrefWidth(CARD_WIDTH - 24);
		name.setMinHeight(38);

		card.getChildren().add(name);


		String coordText = String.format(Locale.US, "%.6f, %.6f", stop.lat, stop.lon);
		Label coordinates = new Label(coordText);
		coordinates.getStyleClass().add("stop-hover-coordinates");
		coordinates.setPrefWidth(CARD_WIDTH - 24);
		coordinates.setTextOverrun(OverrunStyle.CLIP);

		card.getChildren().add(coordinates);


		Label arrivals = new Label("Rides through this stop");
		arrivals.getStyleClass().add("stop-hover-section-title");
		card.getChildren().add(arrivals);


		ListView<String> arrivalsList = new ListView<>();
		arrivalsList.getStyleClass().add("stop-hover-arrivals");
		arrivalsList.setPrefWidth(CARD_WIDTH - 24);
		arrivalsList.setMinWidth(CARD_WIDTH - 24);
		arrivalsList.setPrefHeight(145);
		arrivalsList.setMinHeight(145);
		arrivalsList.getItems().addAll(arrivals);

		card.getChildren().add(arrivalsList);


		Button startButton = new Button("Start");
		startButton.getStyleClass().add("stop-hover-button");
		startButton.setPrefWidth(122);
		startButton.setMinWidth(122);
		startButton.setMinHeight(34);
		startButton.setOnAction(ev -> {
			journeyInput.setStart(stop.name);
			hide();
		});

		Button endButton = new Button("Destination");
		endButton.getStyleClass().add("stop-hover-button");
		endButton.setPrefWidth(122);
		endButton.setMinWidth(122);
		endButton.setMinHeight(34);
		endButton.setOnAction(ev -> {
			journeyInput.setEnd(stop.name);
			hide();
		});

		HBox actionRow = new HBox(8);
		actionRow.setPrefWidth(CARD_WIDTH - 28);
		actionRow.setMinWidth(CARD_WIDTH - 28);
		actionRow.getChildren().addAll(startButton, endButton);

		card.getChildren().add(actionRow);


		Button disableButton = new Button("Disable Stop");
		disableButton.getStyleClass().add("stop-hover-button-muted");
		disableButton.setPrefWidth(CARD_WIDTH - 28);
		disableButton.setMinWidth(CARD_WIDTH - 28);
		disableButton.setMinHeight(34);
		disableButton.setOnAction(ev -> network.toggleStop(stop.id));

		card.getChildren().add(disableButton);

		card.setVisible(true);
		card.toFront();
		card.applyCss();
		card.autosize();
		card.layout();

		position(viewWidth, viewHeight, mouseX, mouseY);
	}

	public void hide() {
		card.setVisible(false);
	}

	public boolean overlapping(double mouseX, double mouseY) {
		if (!card.isVisible()) {
			return false;
		}

		double margin = 18;
		double minX = card.getLayoutX() - margin;
		double minY = card.getLayoutY() - margin;
		double maxX = card.getLayoutX() + card.getWidth() + margin;
		double maxY = card.getLayoutY() + card.getHeight() + margin;

		return mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY;
	}

	public boolean hovered() {
		return card.isHover();
	}


	void position(double viewWidth, double viewHeight, double mouseX, double mouseY) {
		double width = Math.max(card.getWidth(), CARD_WIDTH);
		double height = Math.max(card.getHeight(), CARD_HEIGHT);
		double margin = 8;

		double maxX = Math.max(margin, viewWidth - cardWidth - margin);
		double maxY = Math.max(margin, viewHeight - cardHeight - margin);
		double x = Math.min(Math.max(mouseX, margin), maxX);
		double y = Math.min(Math.max(mouseY, margin), maxY);

		card.relocate(x, y);
	}


	public VBox getVBox() {
		return card;
	}
}

