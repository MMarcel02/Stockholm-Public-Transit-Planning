package com.team18.gui;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.io.IOException;
import java.time.LocalDate;

import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;

import com.team18.parser.GTFSParser;
import com.team18.util.ParsingUtil;
import com.team18.model.Stop;

import javafx.scene.control.DatePicker;

public class JourneyInput {
	static final int MAX_SUGGESTIONS = 10;

	TextField startField;
	TextField endField;
	TextField timeField;
	DatePicker datePicker;

	// True when it's being set through code.
	boolean fieldsLocked = false;

	List<Stop> stops = new ArrayList<>();

	public JourneyInput(GTFSParser parser,
			TextField startField, TextField endField, TextField timeField, DatePicker datePicker) {
		this.startField = startField;
		this.endField = endField;
		this.timeField = timeField;
		this.datePicker = datePicker;

		stops = new ArrayList<>(parser.stops.values());
		stops.sort(Comparator.comparing(stop -> stop.name.toLowerCase()));

		setupAutocomplete(startField);
		setupAutocomplete(endField);
	}

	public void setStart(String text) { setField(startField, text); }
	public String getStart() { return startField.getText(); }

	public void setEnd(String text) { setField(endField, text); }
	public String getEnd() { return endField.getText(); }

	public void setTime(String text) { setField(timeField, text); }
	public String getTime() { return timeField.getText(); }

	public double[] resolveStart() { return resolve(getStart()); }
	public double[] resolveEnd() { return resolve(getEnd()); }

	public int getTimeInSeconds() throws IOException {
		return ParsingUtil.timeStringToSecondsAfterMidnight(getTime());
	}

	double[] resolve(String text) {
		Stop stop = findStop(text);
		if (stop != null) {
			return new double[] {stop.lat, stop.lon};
		}

		List<Stop> suggestions = findSuggestions(text);
		if (!suggestions.isEmpty()) {
			Stop firstSuggestion = suggestions.get(0);
			return new double[] {firstSuggestion.lat, firstSuggestion.lon};
		}

		String[] parts = text.split(",");
		if (parts.length != 2) {
			throw new IllegalArgumentException(
					"Location must be a stop name or lat, lon pair.");
		}

		double lat = Double.parseDouble(parts[0].trim());
		double lon = Double.parseDouble(parts[1].trim());

		return new double[] {lat, lon};
	}


	void setupAutocomplete(TextField field) {
		ContextMenu menu = new ContextMenu();
		menu.getStyleClass().add("suggestions-menu");

		field.textProperty().addListener((observable, oldValue, newValue) -> {
			if (fieldsLocked) return;
			showSuggestions(field, menu);
		});

		field.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
			if (!isFocused) {
				menu.hide();
			} else if (!field.getText().isBlank()) {
				showSuggestions(field, menu);
			}
		});
	}

	void showSuggestions(TextField field, ContextMenu menu) {
		List<Stop> suggestions = findSuggestions(field.getText());
		if (suggestions.isEmpty() || !field.isFocused()) {
			menu.hide();
			return;
		}

		menu.getItems().clear();
		for (Stop stop: suggestions) {
			Label label = new Label(stop.name);
			label.getStyleClass().add("suggestion-item");

			CustomMenuItem item = new CustomMenuItem(label, true);
			item.setOnAction(event -> {
				setField(field, stop.name);
			});
			menu.getItems().add(item);
		}

		if (!menu.isShowing()) {
			menu.show(field, Side.BOTTOM, 0, 0);
		}
	}

	List<Stop> findSuggestions(String query) {
		String normQuery = normalize(query);
		if (normQuery.length() < 2) return List.of();

		List<Stop> prefixMatches = new ArrayList<>();
		List<Stop> containsMatches = new ArrayList<>();

		for (Stop stop: stops) {
			String normName = normalize(stop.name);

			if (normName.startsWith(normQuery)) {
				prefixMatches.add(stop);
			} else if (normName.contains(normQuery)) {
				containsMatches.add(stop);
			}
		}

		List<Stop> suggestions = new ArrayList<>();
		addUniqueStops(suggestions, prefixMatches);
		addUniqueStops(suggestions, containsMatches);

		if (suggestions.size() > MAX_SUGGESTIONS) {
			return suggestions.subList(0, MAX_SUGGESTIONS);
		}

		return suggestions;
	}

	void addUniqueStops(List<Stop> target, List<Stop> candidates) {
		for (Stop candidate: candidates) {
			boolean alreadyAdded = false;
			for (Stop existing: target) {
				if (existing.name.equalsIgnoreCase(candidate.name)) {
					alreadyAdded = true;
					break;
				}
			}

			if (!alreadyAdded) {
				target.add(candidate);
			}

			if (target.size() >= MAX_SUGGESTIONS) return;
		}
	}

	public Stop findStop(String name) {
		String normalizedName = normalize(name);
		for (Stop stop: stops) {
			if (normalize(stop.name).equals(normalizedName)) {
				return stop;
			}
		}
		return null;
	}

	String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}

	void setField(TextField field, String text) {
		fieldsLocked = true;
		field.setText(text);
		field.positionCaret(text.length());
		fieldsLocked = false;
	}

	// use schedule from previous day for journeys starting between midnight and 4am
	public LocalDate getEffectiveDate() throws IOException {
		LocalDate date = datePicker.getValue();
		if (date == null) date = LocalDate.now();
		
		int time = getTimeInSeconds();
		if (time >= 0 && time < 4 * 3600) {
			return date.minusDays(1);
		}
		return date;
	}

	// since we are taking the one from prev day, we need to format it as something like 25:00 for gtfs 
	public int getEffectiveTimeInSeconds() throws IOException {
		int time = getTimeInSeconds();
		if (time >= 0 && time < 4 * 3600) {
			return time + 86400;
		}
		return time;
	}

}

