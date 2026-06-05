package com.team18.gui;

import java.util.List;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import java.util.Locale;

import com.team18.model.RouteStep;
import com.team18.model.RouteStepType;
import com.team18.util.GeoCalculator;

public class RouteDisplay {
	VBox vbox;

	public RouteDisplay(VBox vbox) {
		this.vbox = vbox;
	}

	public void display(List<RouteStep> steps) {
		vbox.getChildren().clear();

		if (steps.isEmpty()) {
			vbox.getChildren().add(new Label("No route found"));
			return;
		}

		vbox.getChildren().add(buildSummary(steps));

		for (RouteStep step: steps) {
			VBox stepCard = new VBox(5);

			stepCard.setStyle(
				"-fx-background-color: #f4f4f4; " +
				"-fx-padding: 10; " +
				"-fx-background-radius: 5; " +
				"-fx-border-color: #ddd; " +
				"-fx-border-radius: 5;"
			);

			String modeText;
			if (step.routeStepType == RouteStepType.TRANSIT) {
				modeText =
					step.route.longName + " " +
					step.route.shortName + " " +
					step.headSign;
			} else {
				modeText = "WALK";
			}

			Label modeLabel = new Label(modeText);
			modeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2196F3;");

			String destText;
			if (step.routeStepType == RouteStepType.DIRECT_WALK
					|| step.routeStepType == RouteStepType.WALK_TO_DEST) {
				destText = "To destination";
			} else {
				destText = "To " + step.toStop.name;
			}

			int duration = (int) Math.round(step.durationMinutes);
			Label detailsLabel;

			if (duration == 0) {
				detailsLabel = new Label(destText + " ( <1 min)");
			} else {
				detailsLabel = new Label(destText + " (" + duration + " mins)");
			}

			detailsLabel.setWrapText(true);

			stepCard.getChildren().addAll(modeLabel, detailsLabel);
			vbox.getChildren().add(stepCard);
		}
	}

	public void displayInvalidInput() {
		vbox.getChildren().clear();

		Label errorLabel =
			new Label("Invalid input. Choose a stop suggestion or enter coordinates.");
		errorLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");

		vbox.getChildren().add(errorLabel);
	}

	VBox buildSummary(List<RouteStep> steps) {
		double totalMinutes = 0.0;
		double totalMeters = 0.0;

		for (RouteStep step : steps) {
			totalMinutes += step.durationMinutes;
			totalMeters += GeoCalculator.calculateEquirectangularDistance(
					step.latFrom, step.lonFrom,
					step.latTo, step.lonTo
			);
		}

		VBox summary = new VBox(4);
		summary.getStyleClass().add("route-summary-card");

		Label title = new Label("Trip summary");
		title.getStyleClass().add("route-summary-title");

		Label totals = new Label(
			formatDuration(totalMinutes) + " | " +
			formatKilometers(totalMeters));

		totals.getStyleClass().add("route-summary-value");

		summary.getChildren().addAll(title, totals);

		return summary;
	}

	String formatDuration(double minutes) {
		int roundedMinutes = (int) Math.round(minutes);

		if (roundedMinutes <= 0) {
			return "<1 min";
		}

		if (roundedMinutes < 60) {
			return roundedMinutes + " min";
		}

		int hours = roundedMinutes / 60;
		int remMinutes = roundedMinutes % 60;
		if (remMinutes == 0) {
			return hours + " hr";
		}

		return hours + " hr " + remMinutes + " min";
	}

	private String formatKilometers(double meters) {
		return String.format(Locale.US, "%.1f km", meters / 1000.0);
	}
}

