package com.team18.gui;

import java.util.List;

import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import java.util.Locale;

import com.team18.model.RouteStep;
import com.team18.model.RouteStepType;
import com.team18.util.GeoCalculator;
import com.team18.util.ParsingUtil;

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

			String startTimeStr = ParsingUtil.secondsAfterMidnightToTimeString(step.startTimeSecondsAfterMidnight);
            int waitMins = (int) Math.round(step.waitTimeSecs / 60.0);
            int tripMins = (int) Math.round(step.tripTimeSecs / 60.0);

            String detailStyle = "-fx-padding: 0 0 0 15; -fx-text-fill: #5f6368;";

            if (step.routeStepType == RouteStepType.TRANSIT) {
                
                // waiting
                if (waitMins > 0) {
                    Label waitTitle = new Label(startTimeStr + " | Wait");
                    waitTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #0d4b9c;");
                    
                    Label waitDuration = new Label(waitMins + " min");
                    waitDuration.setStyle(detailStyle + " -fx-padding: 0 0 8 15;");
                    
                    stepCard.getChildren().addAll(waitTitle, waitDuration);
                }

                // Riding
                int rideStartSecs = step.startTimeSecondsAfterMidnight + step.waitTimeSecs;
                String rideStartStr = ParsingUtil.secondsAfterMidnightToTimeString(rideStartSecs);

                String vehicleName = step.route.routeType.name() + " " + step.route.shortName;
                Label rideTitle = new Label(rideStartStr + " | " + vehicleName);
                rideTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #0d4b9c;");
                stepCard.getChildren().add(rideTitle);

                if (step.route.longName != null && !step.route.longName.isEmpty()) {
                    Label longNameLabel = new Label(step.route.longName);
                    longNameLabel.setStyle(detailStyle);
                    longNameLabel.setWrapText(true);
                    stepCard.getChildren().add(longNameLabel);
                }

                Label rideDuration = new Label(tripMins + " min");
                rideDuration.setStyle(detailStyle);
                
                Label destLabel = new Label("To " + step.toStop.name);
                destLabel.setStyle(detailStyle);
                destLabel.setWrapText(true);

                stepCard.getChildren().addAll(rideDuration, destLabel);

            } else {
                // walking
                Label walkTitle = new Label(startTimeStr + " | Walk");
                walkTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #0d4b9c;");
                
                String durationTxt = tripMins == 0 ? "<1 min" : tripMins + " min";
                Label walkDuration = new Label(durationTxt);
                walkDuration.setStyle(detailStyle);
                
                String destText;
                if (step.routeStepType == RouteStepType.DIRECT_WALK || step.routeStepType == RouteStepType.WALK_TO_DEST) {
                    destText = "To destination";
                } else {
                    destText = "To " + step.toStop.name;
                }
                
                Label walkDest = new Label(destText);
                walkDest.setStyle(detailStyle);
                walkDest.setWrapText(true);
                
                stepCard.getChildren().addAll(walkTitle, walkDuration, walkDest);
            }

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
		int totalSecs = 0;
		double totalMeters = 0.0;
		
		for (RouteStep step : steps) {
			totalSecs += (step.waitTimeSecs + step.tripTimeSecs);
			totalMeters += GeoCalculator.calculateEquirectangularDistance(
				step.latFrom, step.lonFrom,
				step.latTo, step.lonTo
			);
		}

		RouteStep firstStep = steps.get(0);
        String startTimeStr = ParsingUtil.secondsAfterMidnightToTimeString(firstStep.startTimeSecondsAfterMidnight);

		RouteStep lastStep = steps.get(steps.size() - 1);
        String arrivalTimeStr = ParsingUtil.secondsAfterMidnightToTimeString(lastStep.startTimeSecondsAfterMidnight + lastStep.waitTimeSecs + lastStep.tripTimeSecs);
		
		double totalMinutes = totalSecs / 60.0;

		VBox summary = new VBox(4);
		summary.getStyleClass().add("route-summary-card");

		Label title = new Label("Trip summary");
		title.getStyleClass().add("route-summary-title");

		Label totals = new Label(
			formatDuration(totalMinutes) + " | " +
			formatKilometers(totalMeters));

		totals.getStyleClass().add("route-summary-value");

		Label scheduleLabel = new Label(startTimeStr + " : " + arrivalTimeStr);
        scheduleLabel.setStyle("-fx-font-size: 15px; -fx-text-fill: #0d4b9c; -fx-font-weight: bold;");

		summary.getChildren().addAll(title, scheduleLabel, totals);

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

