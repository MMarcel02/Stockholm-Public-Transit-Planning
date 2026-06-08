package com.team18.util;

import javafx.scene.paint.Color;

public class Colors {
	public static Color interpolatePalette(double value, double[] thresholds,
			String[] colors, double opacity) {
		if (value <= thresholds[0]) {
			return Color.web(colors[0], opacity);
		}

		for (int i = 1; i < thresholds.length; i++) {
			if (value <= thresholds[i]) {
				double ratio =
					(value - thresholds[i - 1]) / (thresholds[i] - thresholds[i - 1]);

				Color start = Color.web(colors[i - 1]);
				Color end = Color.web(colors[i]);

				return start.interpolate(end, ratio).deriveColor(0, 1, 1, opacity);
			}
		}

		return Color.web(colors[colors.length - 1], opacity);
	}
}

