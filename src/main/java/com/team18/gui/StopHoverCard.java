package com.team18.gui;

import java.util.List;
import java.util.Locale;

import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Button;
import javafx.scene.control.OverrunStyle;

import com.team18.model.Stop;
import com.team18.routing.raptor.RaptorNetwork;
import com.team18.parser.GTFSParser;

public class StopHoverCard {
	final double CARD_WIDTH = 268;
	final double CARD_HEIGHT = 390;

	Stop currentStop = null;
	VBox card = new VBox(5);

	JourneyInput journeyInput;
	GTFSParser parser;

	RaptorNetwork network;
	private Runnable onToggleCallback;
	public void setOnToggleCallback(Runnable callback) {
		this.onToggleCallback = callback;
	}

	public StopHoverCard(JourneyInput journeyInput, RaptorNetwork network,
			GTFSParser parser) {
		card.getStyleClass().add("stop-hover-card");
		card.setVisible(false);
		card.setMouseTransparent(false);
		card.setPrefWidth(CARD_WIDTH);
		card.setMinWidth(CARD_WIDTH);
		card.setMaxWidth(CARD_WIDTH);

		this.journeyInput = journeyInput;
		this.network = network;
		this.parser = parser;
	}

	public boolean isVisible() {
        return card.isVisible();
    }

	public void show(Stop stop, List<StopLayer.Arrival> arrivals) {

		if (stop == currentStop && card.isVisible()) {
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


		Label arrivalsLabel = new Label("Rides through this stop");
		arrivalsLabel.getStyleClass().add("stop-hover-section-title");
		card.getChildren().add(arrivalsLabel);


		ListView<StopLayer.Arrival> arrivalsList = new ListView<>();
		arrivalsList.getStyleClass().add("stop-hover-arrivals");
		arrivalsList.setPrefWidth(CARD_WIDTH - 24);
		arrivalsList.setMinWidth(CARD_WIDTH - 24);
		arrivalsList.setPrefHeight(145);
		arrivalsList.setMinHeight(145);
		arrivalsList.getItems().addAll(arrivals);

		card.getChildren().add(arrivalsList);


		Button startButton = new Button("Origin");
		startButton.getStyleClass().add("primary-button-with-border");
		startButton.setMaxWidth(Double.MAX_VALUE);
		startButton.setPrefWidth(0);
		startButton.setMinHeight(34);
		HBox.setHgrow(startButton, javafx.scene.layout.Priority.ALWAYS);
		startButton.setOnAction(ev -> {
			journeyInput.setStart(stop.name);
			hide();
		});

		Button endButton = new Button("Destination");
		endButton.getStyleClass().add("primary-button-with-border");
		endButton.setMaxWidth(Double.MAX_VALUE);
		endButton.setPrefWidth(0);
		endButton.setMinHeight(34);
		HBox.setHgrow(endButton, javafx.scene.layout.Priority.ALWAYS);
		endButton.setOnAction(ev -> {
			journeyInput.setEnd(stop.name);
			hide();
		});

		HBox actionRow = new HBox(5);
		actionRow.setPrefWidth(CARD_WIDTH - 28);
		actionRow.setMinWidth(CARD_WIDTH - 28);
		actionRow.getChildren().addAll(startButton, endButton);

		card.getChildren().add(actionRow);

		int internalId = network.stopStringToIntMap.get(stop.id);
		boolean isEnabled = network.stopsEnabledArr[internalId];

		Button disableButton = new Button(isEnabled ? "Disable Stop" : "Enable Stop");
		disableButton.getStyleClass().add("outline-button");
		disableButton.setMaxWidth(Double.MAX_VALUE);
		disableButton.setMinHeight(34);
		disableButton.setOnAction(ev -> {
			for (Stop toToggle: parser.stops.values()) {
				if (!toToggle.name.equals(stop.name)) continue;
				network.toggleStop(toToggle.id);
			}

			hide();
			if (onToggleCallback != null) {
				onToggleCallback.run();
			}
		});

		card.getChildren().add(disableButton);
		card.setVisible(true);
		card.toFront();
		card.applyCss();
		card.autosize();
		card.layout();

		// position(viewWidth, viewHeight, mouseX, mouseY);
	}

	public void hide() {
		card.setVisible(false);
	}

	public boolean overlaps(double mouseX, double mouseY) {
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
        
        // Fixed margins to position it on the top right
        double marginRight = 20; 
        
        // Adjust this value so it sits perfectly below your map settings menu!
        // 80 to 100 is usually a good starting point for a standard top-bar menu.
        double marginTop = 80; 

        // Calculate the fixed X and Y coordinates
        double x = viewWidth - width - marginRight;
        double y = marginTop;

        card.relocate(x, y);
    }


	public VBox getVBox() {
		return card;
	}
}

