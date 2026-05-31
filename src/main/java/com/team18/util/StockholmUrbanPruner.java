package com.team18.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;


public class StockholmUrbanPruner {

    // Reference used for zip stuff: https://www.baeldung.com/java-compress-and-uncompress
    
    private static HashSet<String> stopIds = new HashSet<>();
    private static HashSet<String> tripIds = new HashSet<>();
    private static HashSet<String> routeIds = new HashSet<>();
    private static HashSet<String> serviceIds = new HashSet<>();
    private static HashSet<String> shapeIds = new HashSet<>();

    public static void main(String[] args) {
        try {
            unzipSl();
            pruneStops();    
            pruneStopTimes();
            pruneTrips();
            pruneRoutes();
            pruneShapes();
            pruneCalendar();
            pruneCalendarDates();
            zipSlCenter();
        } catch (IOException e) {
            e.printStackTrace();
        } 
    }

    private static void unzipSl() throws IOException {
        File slCenterDir = new File("data/stockholm/sl_center");
        slCenterDir.mkdir();
        ZipInputStream zis = new ZipInputStream(new FileInputStream("data/stockholm/sl.zip"));
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            if (!zipEntry.isDirectory()) {
                String rawFileName = new File(zipEntry.getName()).getName();
                File newFile = new File(slCenterDir, rawFileName);
                FileOutputStream fos = new FileOutputStream(newFile);
                zis.transferTo(fos);
                fos.close();
            }
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
    }

    private static void zipSlCenter() throws IOException {
        FileOutputStream fos = new FileOutputStream("data/stockholm/sl_center.zip");
        ZipOutputStream zipOut = new ZipOutputStream(fos);

        File dirToZip = new File("data/stockholm/sl_center");
        File[] children = dirToZip.listFiles();
        for (File childFile : children) {
            zipOut.putNextEntry(new ZipEntry(childFile.getName()));
            FileInputStream fis = new FileInputStream(childFile);
            fis.transferTo(zipOut);
            fis.close();
            zipOut.closeEntry();
            childFile.delete();
        }
        dirToZip.delete();
        zipOut.close();
        fos.close();
    }

    private static void pruneStops() throws IOException {
        File tmp = File.createTempFile("tempStops", "");           
        BufferedReader br = new BufferedReader(new FileReader("data/stockholm/sl_center/stops.txt"));
        BufferedWriter bw = new BufferedWriter(new FileWriter(tmp));
        String header = br.readLine();
        bw.write(String.format("%s%n", header));
        String line;
        while ((line = br.readLine()) != null) {
            String[] lineSplit = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            String id = lineSplit[0];
            double lat = Double.parseDouble(lineSplit[2]);
            double lon = Double.parseDouble(lineSplit[3]);
            if (lat > StockholmUrbanArea.OUTER_MIN_LAT && lat < StockholmUrbanArea.OUTER_MAX_LAT && lon > StockholmUrbanArea.OUTER_MIN_LON && lon < StockholmUrbanArea.OUTER_MAX_LON) {
                stopIds.add(id);
                bw.write(String.format("%s%n", line));
            }
        }

        br.close();
        bw.close();

        File oldFile = new File("data/stockholm/sl_center/stops.txt");
        if (oldFile.delete()) {
            tmp.renameTo(oldFile);
        }
    }

    private static void pruneStopTimes() throws IOException {
        File tmp = File.createTempFile("stopTimes", "");
        BufferedReader br = new BufferedReader(new FileReader("data/stockholm/sl_center/stop_times.txt"));
        BufferedWriter bw = new BufferedWriter(new FileWriter(tmp));
        String header = br.readLine();
        bw.write(String.format("%s%n", header)); 
        String line;
        while ((line = br.readLine()) != null) {
            String[] lineSplit = line.split(",");
            String stopId = lineSplit[3];
            if (stopIds.contains(stopId)) {
                bw.write(String.format("%s%n", line));
            }
        }
        br.close();
        bw.close();
        File oldFile = new File("data/stockholm/sl_center/stop_times.txt");
        if (oldFile.delete()) {
            tmp.renameTo(oldFile);
        }

        removeBadTrips();
    }

    private static void removeBadTrips() throws IOException {
        File tmp = File.createTempFile("stopTimes", "");
        BufferedReader br = new BufferedReader(new FileReader("data/stockholm/sl_center/stop_times.txt"));
        BufferedWriter bw = new BufferedWriter(new FileWriter(tmp));
        String header = br.readLine();
        bw.write(String.format("%s%n", header)); 
        String line, tripId = null;
        int stopSeq = 0;
        boolean ignoreAllNext = false;

        ArrayList<String> lines = new ArrayList<>();
        while ((line = br.readLine()) != null) {
            String[] lineSplit = line.split(",");
            String newTripId = lineSplit[0];
            int newStopSeq = Integer.parseInt(lineSplit[4]);
            
            if (tripId == null) {
                tripId = newTripId;
                stopSeq = newStopSeq;
                lines.add(line);
            } else if (!newTripId.equals(tripId)) {
                if (lines.size() >= 2) {
                    tripIds.add(tripId);
                    for (String l : lines) {
                        bw.write(String.format("%s%n", l));
                    }
                }
                lines.clear();
                lines.add(line);
                tripId = newTripId;
                stopSeq = newStopSeq;
                ignoreAllNext = false;
            } else if (!ignoreAllNext && (stopSeq + 1 == newStopSeq)) {
                lines.add(line);
                stopSeq = newStopSeq;
            } else {
                ignoreAllNext = true;
            }
        }

        // Add final trip
        if (lines.size() >= 2) {
            tripIds.add(tripId);
            for (String l : lines) {
                bw.write(String.format("%s%n", l));
            }
        }

        br.close();
        bw.close();

        File oldFile = new File("data/stockholm/sl_center/stop_times.txt");
        if (oldFile.delete()) {
            tmp.renameTo(oldFile);
        }
    }

    private static void pruneTrips() throws IOException {
        File tmp = File.createTempFile("trips", "");
        BufferedReader br = new BufferedReader(new FileReader("data/stockholm/sl_center/trips.txt"));
        BufferedWriter bw = new BufferedWriter(new FileWriter(tmp));
        String header = br.readLine();
        bw.write(String.format("%s%n", header));   
        String line;
        while ((line = br.readLine()) != null) {
            String[] lineSplit = line.split(",", -1);
            String tripId = lineSplit[2];

            String routeId = lineSplit[0];
            String serviceId = lineSplit[1];
            String shapeid = lineSplit[5];

            if (tripIds.contains(tripId)) {
                bw.write(String.format("%s%n", line));
                routeIds.add(routeId);
                serviceIds.add(serviceId);
                shapeIds.add(shapeid);
            }
        }
        br.close();
        bw.close();
        File oldFile = new File("data/stockholm/sl_center/trips.txt");
        if (oldFile.delete()) {
            tmp.renameTo(oldFile);
        }
    }
    
    private static void pruneRoutes() throws IOException {
        File tmp = File.createTempFile("routes", "");
        BufferedReader br = new BufferedReader(new FileReader("data/stockholm/sl_center/routes.txt"));
        BufferedWriter bw = new BufferedWriter(new FileWriter(tmp));
        String header = br.readLine();
        bw.write(String.format("%s%n", header));   
        String line;
        while ((line = br.readLine()) != null) {
            String[] lineSplit = line.split(",");

            String routeId = lineSplit[0];

            if (routeIds.contains(routeId)) {
                bw.write(String.format("%s%n", line));
            }
        }
        br.close();
        bw.close();
        File oldFile = new File("data/stockholm/sl_center/routes.txt");
        if (oldFile.delete()) {
            tmp.renameTo(oldFile);
        }
    }

    private static void pruneShapes() throws IOException {
        File tmp = File.createTempFile("routes", "");
        BufferedReader br = new BufferedReader(new FileReader("data/stockholm/sl_center/shapes.txt"));
        BufferedWriter bw = new BufferedWriter(new FileWriter(tmp));
        String header = br.readLine();
        bw.write(String.format("%s%n", header));   
        String line;
        while ((line = br.readLine()) != null) {
            String[] lineSplit = line.split(",");

            String shapeId = lineSplit[0];

            if (shapeIds.contains(shapeId)) {
                bw.write(String.format("%s%n", line));
            }
        }
        br.close();
        bw.close();
        File oldFile = new File("data/stockholm/sl_center/shapes.txt");
        if (oldFile.delete()) {
            tmp.renameTo(oldFile);
        }
    }

    private static void pruneCalendar() throws IOException {
        File tmp = File.createTempFile("routes", "");
        BufferedReader br = new BufferedReader(new FileReader("data/stockholm/sl_center/calendar.txt"));
        BufferedWriter bw = new BufferedWriter(new FileWriter(tmp));
        String header = br.readLine();
        bw.write(String.format("%s%n", header));   
        String line;
        while ((line = br.readLine()) != null) {
            String[] lineSplit = line.split(",");

            String serviceId = lineSplit[0];

            if (serviceIds.contains(serviceId)) {
                bw.write(String.format("%s%n", line));
            }
        }
        br.close();
        bw.close();
        File oldFile = new File("data/stockholm/sl_center/calendar.txt");
        if (oldFile.delete()) {
            tmp.renameTo(oldFile);
        }
    }

    private static void pruneCalendarDates() throws IOException {
        File tmp = File.createTempFile("calendar_dates", "");
        BufferedReader br = new BufferedReader(new FileReader("data/stockholm/sl_center/calendar_dates.txt"));
        BufferedWriter bw = new BufferedWriter(new FileWriter(tmp));
        String header = br.readLine();
        bw.write(String.format("%s%n", header));   
        String line;
        while ((line = br.readLine()) != null) {
            String[] lineSplit = line.split(",");
            
            String serviceId = lineSplit[0]; 

            if (serviceIds.contains(serviceId)) {
                bw.write(String.format("%s%n", line));
            }
        }
        br.close();
        bw.close();
        File oldFile = new File("data/stockholm/sl_center/calendar_dates.txt");
        if (oldFile.delete()) {
            tmp.renameTo(oldFile);
        }
    }


}
