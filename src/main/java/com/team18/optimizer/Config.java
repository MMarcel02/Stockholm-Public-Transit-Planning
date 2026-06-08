package com.team18.optimizer;

import java.time.LocalDate;
import java.time.Month;
import java.util.Set;

import com.team18.util.ParsingUtil;

public class Config {

    // Our network has the following 
    // Buses: 427
    // Trams: 12
    // Metros: 7
    // Trains: 4
    // Ferries: 21
    
    public static LocalDate REPRESENTATIVE_WEEKDAY; 

    public static final Set<LocalDate> SWEDISH_PUBLIC_HOLIDAYS = Set.of(
        LocalDate.of(2026, Month.APRIL, 3),   // Good Friday 
        LocalDate.of(2026, Month.APRIL, 6),   // Easter Monday 
        LocalDate.of(2026, Month.MAY, 1),     // May Day 
        LocalDate.of(2026, Month.MAY, 14)     // Ascension Day 
    );

    // TODO: If have time come up with algorithmic way of pickign reference week 
    // Turns out doing all will take too long, better to pick one
    public static final LocalDate[] REFERENCE_PERIOD = {
        LocalDate.of(2026, 4, 13), // Monday
        LocalDate.of(2026, 4, 14), // Tuesday
        LocalDate.of(2026, 4, 15), // Wednesday
        LocalDate.of(2026, 4, 16), // Thursday
        LocalDate.of(2026, 4, 17), // Friday
        LocalDate.of(2026, 4, 18), // Saturday
        LocalDate.of(2026, 4, 19)  // Sunday
    };


    public static final int[] REFERENCE_TIMES = {
        ParsingUtil.timeStringToSecondsAfterMidnight("8:00"),
        ParsingUtil.timeStringToSecondsAfterMidnight("12:30"),
        ParsingUtil.timeStringToSecondsAfterMidnight("17:00")
    };


    public static final double[] REFERENCE_WEIGHTS = {
        0.35,
        0.3,
        0.35
    };


    public static int MAX_RAPTOR_ROUNDS = 5;

    // Daily trips assuming all modes of transport
    // https://www.regionstockholm.se/496707/contentassets/d423312f6696457c9a4e659d501a86e0/resvaneundersokning_for_stockholmsregionen_2024.pdf
    // On page 5, its in swedish so need to translate
    // Also here for the netherlands (could argue its more accurate as have actual city data)
    // https://opendata.cbs.nl/#/CBS/en/dataset/84710ENG/table?dl=942C9
    // The response rate was 35%, and the final sample included 45,467 respondents making 102,588 trips, of which 31,961 were transit trips.
    public static double TRIPS_PER_DAY_PER_PERSON_STOCKHOLM_COUNTY = 2.7; // used to configure our daily demand matrix
    public static double TRIPS_PER_WEEKDAY_PER_PERSON_STOCKHOLM_COUNTY = 1.9;
    public static double TRIPS_PER_WEEKEND_PER_PERSON_STOCKHOLM_COUNTY = 1.5;

    // From urban area to inner city about 70% use public transit 
    // Within inner city about 40% use public transit
    // We use this to estimate that trips under 2 km 40 % will use transit
    // and over 2km 70% will use it since we are in the urban area          
    // https://link.springer.com/article/10.1186/s12544-021-00488-0#Sec6
    // also here https://www.sciencedirect.com/science/article/pii/S0966692319304016#s0030
    public static double PERCENTAGE_OF_TRIPS_USING_TRANSIT = 0.6;
    public static double PERCENTAGE_OF_TRIPS_USING_TRANSIT_UNDER_THRESHOLD = 0.4;
    public static double FRICTION_FACTOR = 2.5; // relatively high, to model most ppl travelling to city and back

    public static double OPERATOR_CONTRACT_OVERHEAD = 1.12; // how much operators charge sl over pure vehicle operating costs

    public static double WALK_SPEED_MPS = 5.0 / 3.6;    // 5km/h 
    public static double CAR_SPEED_MPS = 30.0 / 3.6;    // 30 km/h (assuming stockholm rush hour) 
    
    public static double CAR_COST_PER_METRE = 50.0 / 10000.0; // 50 SEK per 10km (includes taxes + fuel + depriciation)
    public static double FLAT_CAR_PENALTY = 400.0;            // For parking + tolls
    public static double CAR_DISTANCE_MULTIPLIER = 2.0;       // If we want to increase the distance to discourage driving over water 

    // TODO: Find a good reference for an actual VOT
    public static double VOT = 200.0 / 3600.0;                // 100 SEK per hour (how much ppl estimate their time is worth)
    public static double PASSENGER_COST_WEIGHT = 1.1;         // how much we actually value passenger cost 

    public static int MAX_TRANSIT_TIME_SECONDS = 2*60*60;     // 2 hrs (if goes over this we assume passenger would rather take car)
    
    public static int MAX_WALK_DISTANCE_TRANSFERS_METRES = 700;            // how much a passenger is willing to transfer
    public static int MAX_WALK_DISTANCE_INITIAL_AND_FINAL_METRES = 1500;   // how much a passenger is willing to walk before/after transit 
    public static int TRANSFER_PENALTY = 60;                               // time in seconds added to each transfer

    public static final double EARTH_RADIUS_METERS = 6371000.0;
}
