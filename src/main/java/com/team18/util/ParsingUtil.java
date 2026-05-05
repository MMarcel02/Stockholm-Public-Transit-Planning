package com.team18.util;

import java.io.IOException;

public class ParsingUtil {

        public static int parseStopTime(String timeString) throws IOException{
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
            throw new IOException("Time string failed parsing into number");
        }
    }
    
}
