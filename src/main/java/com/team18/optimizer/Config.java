package com.team18.optimizer;

import java.time.LocalDate;

public class Config {


    // TODO: If have time come up with algorithmic way of pickign reference week 
    public static final LocalDate[] REFERENCE_PERIOD = {
        LocalDate.of(2026, 4, 13), // Monday
        LocalDate.of(2026, 4, 14), // Tuesday
        LocalDate.of(2026, 4, 15), // Wednesday
        LocalDate.of(2026, 4, 16), // Thursday
        LocalDate.of(2026, 4, 17), // Friday
        LocalDate.of(2026, 4, 18), // Saturday
        LocalDate.of(2026, 4, 19)  // Sunday
    };

    public static int MAX_RAPTOR_ROUNDS = 5;

    // Daily trips assuming all modes of transport
    // https://www.regionstockholm.se/496707/contentassets/d423312f6696457c9a4e659d501a86e0/resvaneundersokning_for_stockholmsregionen_2024.pdf
    // On page 5, its in swedish so need to translate
    // TODO: Maybe better to find one for stockholm urban area as trips per day likely to be higher
    public static double TRIPS_PER_DAY_PER_PERSON_STOCKHOLM_COUNTY = 1.8; // used to configure our daily demand matrix
    public static double TRIPS_PER_WEEKDAY_PER_PERSON_STOCKHOLM_COUNTY = 1.9;
    public static double TRIPS_PER_WEEKEND_PER_PERSON_STOCKHOLM_COUNTY = 1.5;

    // TODO: Find good reference for these 
    public static double PERCENTAGE_OF_TRIPS_USING_TRANSIT = 0.75;
    public static double PERCENTAGE_OF_TRIPS_USING_TRANSIT_UNDER_THRESHOLD = 0.25;

    public static double WALK_SPEED_MPS = 5.0 / 3.6;    // 5km/h 
    public static double CAR_SPEED_MPS = 20.0 / 3.6;    // 20 km/h (assuming stockholm rush hour) 
    
    public static double CAR_COST_PER_METRE = 50.0 / 10000.0; // 50 SEK per 10km (includes taxes + fuel + depriciation)
    public static double FLAT_CAR_PENALTY = 300.0;            // For parking + tolls
    public static double CAR_DISTANCE_MULTIPLIER = 2.0;       // If we want to increase the distance to discourage driving over water 

    // TODO: Find a good reference for an actual VOT
    public static double VOT = 150.0 / 3600.0;                // 100 SEK per hour (how much ppl estimate their time is worth)
    public static double PASSENGER_COST_WEIGHT = 0.1;         // how much we actually value passenger cost 

    public static int MAX_TRANSIT_TIME_SECONDS = 2*60*60;     // 2 hrs (if goes over this we assume passenger would rather take car)
    
    public static int MAX_WALK_DISTANCE_TRANSFERS_METRES = 500;           // how much a passenger is willing to transfer
    public static int MAX_WALK_DISTANCE_INITIAL_AND_FINAL_METRES = 1500;   // how much a passenger is willing to walk before/after transit 
}
