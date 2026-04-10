package com.team18.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.HashMap;
import java.util.Map;
import com.team18.model.Stop;

public class GTFSParser {

    public Map<String, String> agencies = new HashMap<>();
    public Map<String, Stop> stops = new HashMap<>();

    public void loadFromZip(String zipFilePath) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipFilePath)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                // the enumeration already goes through whats inside the directories
                // so if we have a directory we can just skip it to avoid errors
                if (entry.isDirectory()) continue;
                System.out.println(entry.getName());
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    BufferedReader reader = new BufferedReader(inputStreamReader);

                    if (entry.getName().endsWith("agency.txt")) {
                        parseAgencies(reader);
                    } 
                    if (entry.getName().endsWith("stops.txt")) {
                        parseStops(reader);
                    } 


                    // if (entry.getName().endsWith("stop_times.txt")) {
                    //     parseStops(reader);
                    // } 
                    // if (entry.getName().endsWith("transfers.txt")) {
                    //     parseStops(reader);
                    // } 
                    // if (entry.getName().endsWith("trips.txt")) {
                    //     parseStops(reader);
                    // } 
                }
            }
        }
    }

    public void parseStops(BufferedReader reader) throws IOException {
        //First line has info about each column, the data inbetween cities is not always in the same columns
        String firstLine = reader.readLine();
        if (firstLine == null) return;
        String[] colNames = firstLine.split(",");
        int idIndex = -1, nameIndex = -1, latIndex = -1, lonIndex = -1;
        for (int i = 0; i < colNames.length; i++) {
            String col = colNames[i].trim();
            if (col.equals("stop_id")) idIndex = i;
            else if(col.equals("stop_name")) nameIndex = i;
            else if(col.equals("stop_lat")) latIndex = i;
            else if(col.equals("stop_lon")) lonIndex = i;
        }

        String line;
        while ((line = reader.readLine()) != null) {
            // AI generated regex to make sure we dont split along , inside the name (if something like that exists)
            String[] lineSplit = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

            try {
                String id = lineSplit[idIndex].replace("\"", "").trim();
                String name = lineSplit[nameIndex].replace("\"", "").trim();
                String latString = lineSplit[latIndex].trim();
                String lonString = lineSplit[lonIndex].trim();

                double lat = Double.parseDouble(latString);
                double lon = Double.parseDouble(lonString);
                
                stops.put(id, new Stop(id, name, lat, lon));
                
            } catch (ArrayIndexOutOfBoundsException e) {
                System.err.println("Skipping malformed line (too short): " + line);
            } catch (NumberFormatException e) {
                System.err.println("Skipping line with invalid numbers: " + line);
            } catch (IllegalArgumentException e) {
                System.err.println("Skipping line due to missing vital data: " + line);
            }
        }
    }

    public void parseAgencies(BufferedReader reader) throws IOException {
        String firstLine = reader.readLine();
        if (firstLine == null) return;
        String[] colNames = firstLine.split(",");
        int idIndex = -1, nameIndex = -1;
        for (int i = 0; i < colNames.length; i++) {
            String col = colNames[i].trim();
            if (col.equals("agency_id")) idIndex = i;
            else if(col.equals("agency_name")) nameIndex = i;
        }

        String line;
        while ((line = reader.readLine()) != null) {
            String[] lineSplit = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

            try {
                String id = lineSplit[idIndex].replace("\"", "").trim();
                String name = lineSplit[nameIndex].replace("\"", "").trim();

                agencies.put(id, name);
                
            } catch (ArrayIndexOutOfBoundsException e) {
                System.err.println("Skipping malformed line (too short): " + line);
            } catch (IllegalArgumentException e) {
                System.err.println("Skipping line due to missing vital data: " + line);
            }
        }
    }
}
