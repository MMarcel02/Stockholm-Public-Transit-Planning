package com.team18.util;

public class ParsingUtil {

    public static int timeStringToSecondsAfterMidnight(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return -1;
        }
        try {
            String[] timeSplit = timeString.split(":");
            int hours = Integer.parseInt(timeSplit[0]);
            int minutes = Integer.parseInt(timeSplit[1]);
            int secondsAfterMidnight = hours*3600 + minutes*60;

            if (timeSplit.length == 3) {
                int seconds = Integer.parseInt(timeSplit[2]);
                secondsAfterMidnight += seconds;
            }
            return secondsAfterMidnight;
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Time string failed parsing into number");
        }
    }

    public static String secondsAfterMidnightToTimeString(int secondsAfterMidnight) {
        int totalMinutes = (int) Math.round(secondsAfterMidnight / 60.0);

        int hours = (totalMinutes / 60) % 24;
        int minutes = totalMinutes % 60;

        return String.format("%02d:%02d", hours, minutes);
    }
    
}
