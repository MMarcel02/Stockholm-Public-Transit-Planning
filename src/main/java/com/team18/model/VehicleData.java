package com.team18.model;

import java.util.Map;

import com.team18.model.Route.RouteType;

public final class VehicleData {

    public static final class Range {
        public final double min;
        public final double max;
        public final double mid;

        public Range(double min, double max, double mid) {
            this.min = min;
            this.max = max;
            this.mid = mid;
        }

        @Override
        public String toString() {
            return min == max ? String.valueOf(min) : (min + " - " + max);
        }
    }

    public final RouteType type;

    public final Range passengerCapacity;

    public final Range operatingCostPerHour;

    public final Range stationUpkeepPerDay;

    public final Range stationUpkeepPerYear;

    private VehicleData(RouteType type, Range passengerCapacity, Range operatingCostPerHour, Range stationUpkeepPerDay, Range stationUpkeepPerYear) {
        this.type = type;
        this.passengerCapacity = passengerCapacity;
        this.operatingCostPerHour = operatingCostPerHour;
        this.stationUpkeepPerDay = stationUpkeepPerDay;
        this.stationUpkeepPerYear = stationUpkeepPerYear;
    }

    public static final VehicleData METRO = new VehicleData(RouteType.METRO,
            new Range(1_200, 1_200, 1_200), //passenger capacity
            new Range(3_000, 5_000, 4_000), // operating cost per hour in SEK
            new Range(13_000, 55_000, 34_000), //station upkeep cost per day in SEK
            new Range(5_000_000, 20_000_000, 12_500_000)); // station upkeep cost per year in SEK

    public static final VehicleData TRAIN = new VehicleData(RouteType.TRAIN,
            new Range(1_800, 1_800, 1_800), //passenger capacity
            new Range(4_000, 6_000, 5_000), // operating cost per hour in SEK
            new Range(8_000, 27_000, 17_500), //station upkeep cost per day in SEK
            new Range(3_000_000, 10_000_000, 6_500_000)); // station upkeep cost per year in SEK

    public static final VehicleData TRAM = new VehicleData(RouteType.TRAM,
            new Range(215, 275, 245), //passenger capacity
            new Range(1_500, 2_500, 2_000), // operating cost per hour in SEK
            new Range(400, 800, 600), //station upkeep cost per day in SEK
            new Range(150_000, 300_000, 225_000)); // station upkeep cost per year in SEK

    public static final VehicleData BUS = new VehicleData(RouteType.BUS,
            new Range(70, 120, 95), //passenger capacity
            new Range(900, 1_500, 1_200), // operating cost per hour in SEK
            new Range(30, 80, 55), //station upkeep cost per day in SEK
            new Range(10_000, 30_000, 20_000)); // station upkeep cost per year in SEK

    public static final VehicleData FERRY = new VehicleData(RouteType.FERRY,
            new Range(150, 400, 275), //passenger capacity
            new Range(4_000, 8_000, 6_000), // operating cost per hour in SEK
            new Range(550, 1_350, 950), //station upkeep cost per day in SEK
            new Range(200_000, 500_000, 350_000)); // station upkeep cost per year in SEK

    private static final Map<RouteType, VehicleData> BY_TYPE = Map.of(
            RouteType.METRO, METRO,
            RouteType.TRAIN, TRAIN,
            RouteType.TRAM, TRAM,
            RouteType.BUS, BUS,
            RouteType.FERRY, FERRY
    );

    // RouteType to VehicleData constant, returns null if type is null
    public static VehicleData forType(RouteType type) {
        return BY_TYPE.get(type);
    }
}
