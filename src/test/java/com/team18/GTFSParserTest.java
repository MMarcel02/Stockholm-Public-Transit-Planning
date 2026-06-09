package com.team18;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.team18.model.Calendar;
import com.team18.model.CalendarDates;
import com.team18.model.Route;
import com.team18.model.Stop;
import com.team18.model.StopTime;
import com.team18.model.Trip;
import com.team18.parser.CSVParser;
import com.team18.parser.GTFSParser;

public class GTFSParserTest {

    private GTFSParser parser;

    @BeforeEach
    void setUp() {
        parser = new GTFSParser();
    }

    private CSVParser csvFrom(String csv) {
        return new CSVParser(new BufferedReader(new StringReader(csv)));
    }

    // -----------
    // parseStops
    // -----------

    @Test
    void parseStops_validRow_isStored() throws Exception {
        String csv = "stop_id,stop_name,stop_lat,stop_lon\n"
                   + "S1,Central,59.33,18.06\n";

        parser.parseStops(csvFrom(csv));

        assertEquals(1, parser.stops.size());
        Stop s = parser.stops.get("S1");
        assertNotNull(s);

        assertEquals("Central", s.name);
        assertEquals(59.33, s.lat, 0.0001);
        assertEquals(18.06, s.lon, 0.0001);
    }

    @Test
    void parseStops_multipleRows_allStored() throws Exception {
        String csv = "stop_id,stop_name,stop_lat,stop_lon\n"
                + "S1,Central,59.33,18.06\n"
                + "S2,Slussen,59.32,18.07\n";

        parser.parseStops(csvFrom(csv));

        assertEquals(2, parser.stops.size());
        assertNotNull(parser.stops.get("S1"));
        assertNotNull(parser.stops.get("S2"));
    }

    @Test
    void parseStops_missingRequiredColumn_throws() {
        String csv = "stop_id,stop_name,stop_lat\n"
                   + "S1,Central,59.33\n";

        assertThrows(IOException.class, () -> parser.parseStops(csvFrom(csv)));
    }

    @Test
    void parseStops_emptyValue_throws() {
        String csv = "stop_id,stop_name,stop_lat,stop_lon\n"
                + ",Central,59.33,18.06\n";

        assertThrows(IOException.class, () -> parser.parseStops(csvFrom(csv)));
    }

    @Test
    void parseStops_nonNumericCoordinate_throws() {
        String csv = "stop_id,stop_name,stop_lat,stop_lon\n"
                + "S1,Central,abc,18.06\n";

        assertThrows(IOException.class, () -> parser.parseStops(csvFrom(csv)));
    }

    @Test
    void parseStops_latitudeOutOfRange_throws() {
        String csv = "stop_id,stop_name,stop_lat,stop_lon\n"
                + "S1,Central,95,18.06\n";

        assertThrows(IOException.class, () -> parser.parseStops(csvFrom(csv)));
    }

    @Test
    void parseStops_longitudeOutOfRange_throws() {
        String csv = "stop_id,stop_name,stop_lat,stop_lon\n"
                + "S1,Central,59.33,200.00\n";

        assertThrows(IOException.class, () -> parser.parseStops(csvFrom(csv)));
    }

    @Test
    void parseStops_boundaryCoordinates_accepted() throws Exception {
        String csv = "stop_id,stop_name,stop_lat,stop_lon\n"
                + "S1,Central,90.00,180.00\n"
                + "S2,Central,-90.00,-180.00\n";

        parser.parseStops(csvFrom(csv));

        assertEquals(2, parser.stops.size());

        Stop s1 = parser.stops.get("S1");
        assertNotNull(s1);
        assertEquals(90.00, s1.lat, 0.0001);
        assertEquals(180.00, s1.lon, 0.0001);

        Stop s2 = parser.stops.get("S2");
        assertNotNull(s2);
        assertEquals(-90.00, s2.lat, 0.0001);
        assertEquals(-180.00, s2.lon, 0.0001);
    }

    // -----------------
    // parseAgencies
    // -----------------

    @Test
    void parseAgencies_missingNameColumn_throws() {
        String csv = "agency_id,\n" +
                    "14010000000001001,Upplands lokaltrafik\n";

        assertThrows(IOException.class, () -> parser.parseAgencies(csvFrom(csv)));
    }

    @Test
    void parseAgencies_withAgencyId_usesIdAsKey() throws Exception {
        String csv = "agency_id,agency_name\n" +
                "14010000205921809,Trafikverket\n";

        parser.parseAgencies(csvFrom(csv));

        assertEquals("Trafikverket", parser.agencies.get("14010000205921809"));
    }

    @Test
    void parseAgencies_withoutAgencyId_keyedAsDefault() throws Exception {
        String csv = "agency_name\n"
                + "Trafikverket\n";

        parser.parseAgencies(csvFrom(csv));

        assertEquals("Trafikverket", parser.agencies.get("default"));
    }

    @Test
    void parseAgencies_emptyName_throws() {
        String csv = "agency_id,agency_name\n"
                + "14010000205921809,";

        assertThrows(IOException.class, () -> parser.parseAgencies(csvFrom(csv)));
    }

    // -----------------
    // parseRoutes
    // -----------------

    @Test
    void parseRoutes_missingRouteIdColumn_throws() {
        String csv = ",route_name\n";

        assertThrows(IOException.class, () -> parser.parseRoutes(csvFrom(csv)));
    }

    @Test
    void parseRoutes_missingBothNameColumns_throws() {
        String csv = "route_id,route_type\n"
                   + "R1,700\n";

        assertThrows(IOException.class, () -> parser.parseRoutes(csvFrom(csv)));
    }

    @Test
    void parseRoutes_singleAgency_autoAssignsOperator() throws Exception {
        parser.agencies.put("A1", "Metro");

        String csv = "route_id,route_short_name,route_type\n"
                   + "R1,10,401\n";

        parser.parseRoutes(csvFrom(csv));

        Route r = parser.routes.get("R1");
        assertNotNull(r);
        assertEquals("Metro", r.operator);
    }

    @Test
    void parseRoutes_multipleAgencies_looksUpOperatorById() throws Exception {
        parser.agencies.put("A1", "Metro");
        parser.agencies.put("A2", "Bus Co");

        String csv = "route_id,agency_id,route_short_name,route_type\n"
                   + "R1,A2,10,3\n";

        parser.parseRoutes(csvFrom(csv));

        Route r = parser.routes.get("R1");
        assertNotNull(r);
        assertEquals("Bus Co", r.operator);
    }

    @Test
    void parseRoutes_multipleAgencies_unknownAgencyId_throws() {
        parser.agencies.put("A1", "Metro");
        parser.agencies.put("A2", "Bus Co");

        String csv = "route_id,agency_id,route_short_name\n"
                   + "R1,A9,10\n";

        assertThrows(IOException.class, () -> parser.parseRoutes(csvFrom(csv)));
    }

    @Test
    void parseRoutes_multipleAgencies_noAgencyIdColumn_throws() {
        parser.agencies.put("A1", "Metro");
        parser.agencies.put("A2", "Bus Co");

        String csv = "route_id,route_short_name\n"
                   + "R1,10\n";

        assertThrows(IOException.class, () -> parser.parseRoutes(csvFrom(csv)));
    }

    @Test
    void parseRoutes_emptyRouteId_throws() {
        parser.agencies.put("A1", "Metro");

        String csv = "route_id,route_short_name\n"
                   + ",10\n";

        assertThrows(IOException.class, () -> parser.parseRoutes(csvFrom(csv)));
    }

    // ---------------
    // parseTrips
    // ---------------

    private Route seedRoute(String id) {
        Route r = new Route(id, "Metro", "10", "Blå linjen", Route.RouteType.METRO);
        parser.routes.put(id, r);
        return r;
    }

    private Trip seedTrip(String tripId, String routeId) {
        Route r = parser.routes.get(routeId);
        if (r == null) r = seedRoute(routeId);
        Trip t = new Trip(tripId, r, "SVC1", "", null);
        parser.trips.put(tripId, t);
        return t;
    }

    private Stop seedStop(String id) {
        Stop s = new Stop(id, "Stop " + id, 59.33, 18.06);
        parser.stops.put(id, s);
        return s;
    }

    @Test
    void parseTrips_missingRequiredColumns_throws() {
        String csv = "trip_id,service_id\n"
                   + "T1,S1\n";

        assertThrows(IOException.class, () -> parser.parseTrips(csvFrom(csv)));
    }

    @Test
    void parseTrips_validTrip_addedToTripsAndRoute() throws Exception {
        Route r = seedRoute("R1");

        String csv = "trip_id,service_id,route_id\n"
                + "T1,S1,R1\n";

        parser.parseTrips(csvFrom(csv));

        assertTrue(parser.trips.containsKey("T1"));

        Trip trip =  parser.trips.get("T1");
        assertNotNull(trip);
        assertTrue(r.trips.contains(trip));
    }

    @Test
    void parseTrips_unknownRouteId_throws() {
        String csv = "trip_id,service_id,route_id\n"
                + "T1,S1,R1\n";

        assertThrows(IOException.class, () -> parser.parseTrips(csvFrom(csv)));
    }

    @Test
    void parseTrips_emptyRequiredValue_throws() {
        String csv = "trip_id,service_id,route_id\n"
                + "T1,S1,\n";

        assertThrows(IOException.class, () -> parser.parseTrips(csvFrom(csv)));
    }

    @Test
    void parseTrips_emptyShapeId_storedAsNull() throws Exception {
        seedRoute("R1");
        String csv = "trip_id,service_id,route_id,shape_id\n"
                    + "T1,S1,R1,\n";

        parser.parseTrips(csvFrom(csv));

        Trip trip = parser.trips.get("T1");
        assertNotNull(trip);
        assertNull(trip.shapeId);
    }

    // ----------------
    // parseStopTimes
    // ----------------

    @Test
    void parseStopTimes_missingRequiredColumns_throws() {
        String csv = ",arrival_time,departure_time,,stop_sequence\n";

        assertThrows(IOException.class, () -> parser.parseStopTimes(csvFrom(csv)));
    }

    @Test
    void parseStopTimes_validRow_addedToTrip() throws Exception {
        Trip trip = seedTrip("T1", "R1");
        seedStop("S1");

        String csv = "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
                   + "T1,08:00:00,08:00:30,S1,1\n";

        parser.parseStopTimes(csvFrom(csv));

        assertEquals(1, trip.stopTimes.size());
        StopTime st = trip.stopTimes.get(0);
        assertEquals("S1", st.stop.id);
        assertEquals(8 * 3600, st.arrivalTime);
        assertEquals(8 * 3600 + 30, st.departureTime);
        assertEquals(1, st.stopSequence);
    }

    @Test
    void parseStopTimes_unknownTripId_throws() {
        seedStop("S1");
        String csv = "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
                + "Z18,22:00:00,22:00:00,S1,3\n";

        assertThrows(IOException.class, () -> parser.parseStopTimes(csvFrom(csv)));
    }

    @Test
    void parseStopTimes_unknownStopId_throws() {
        seedTrip("T1", "R1");
        String csv = "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
                    + "T1,22:00:00,22:00:00,S_missing,3\n";

        assertThrows(IOException.class, () -> parser.parseStopTimes(csvFrom(csv)));
    }

    @Test
    void parseStopTimes_onlyArrivalTime_copiesToDeparture() throws Exception {
        Trip trip = seedTrip("T1", "R1");
        seedStop("S1");

        String csv = "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
                   + "T1,08:00:00,,S1,1\n";

        parser.parseStopTimes(csvFrom(csv));

        StopTime st = trip.stopTimes.get(0);
        assertEquals(st.arrivalTime, st.departureTime);
        assertEquals(8 * 3600, st.departureTime);
    }

    @Test
    void parseStopTimes_bothTimesEmpty_throws() {
        seedTrip("T1", "R1");
        seedStop("S1");
        String csv = "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
                    + "T1,,,S1,3\n";

        assertThrows(IOException.class, () -> parser.parseStopTimes(csvFrom(csv)));
    }

    @Test
    void parseStopTimes_nonIntegerStopSequence_throws() {
        seedTrip("T1", "R1");
        seedStop("S1");
        String csv = "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
                + "T1,22:00:00,22:00:00,S1,x\n";

        assertThrows(IOException.class, () -> parser.parseStopTimes(csvFrom(csv)));
    }

    @Test
    void parseStopTimes_outOfOrderRows_sortedBySequence() throws Exception {
        Trip trip = seedTrip("T1", "R1");
        seedStop("S1");

        String csv = "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
                   + "T1,08:30:00,08:30:00,S1,3\n"
                   + "T1,08:00:00,08:00:00,S1,1\n"
                   + "T1,08:15:00,08:15:00,S1,2\n";

        parser.parseStopTimes(csvFrom(csv));

        assertEquals(3, trip.stopTimes.size());
        assertEquals(1, trip.stopTimes.get(0).stopSequence);
        assertEquals(2, trip.stopTimes.get(1).stopSequence);
        assertEquals(3, trip.stopTimes.get(2).stopSequence);

    }

    // -------------
    // parseShapes
    // -------------

    @Test
    void parseShapes_missingRequiredColumns_throws() {
        String csv = ",shape_pt_lat,,shape_pt_sequence";

        assertThrows(IOException.class, () -> parser.parseShapes(csvFrom(csv)));
    }

    @Test
    void parseShapes_validPoints_groupedByShapeId() throws Exception {
        String csv = "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\n"
                    + "1014010000482329256,59.341873,18.118316,1\n"
                    + "1014010000482329256,59.340944,18.116478,3\n";

        parser.parseShapes(csvFrom(csv));

        assertEquals(2, parser.shapes.get("1014010000482329256").size());
    }

    @Test
    void parseShapes_rowWithEmptyRequiredField_isSkippedNotThrown() throws Exception {
        String csv = "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\n"
                   + "SH1,59.34,18.11,1\n"
                   + "SH1,,18.12,2\n";

        parser.parseShapes(csvFrom(csv));

        assertEquals(1, parser.shapes.get("SH1").size());
    }

    @Test
    void parseShapes_distTraveledUnparseable_isNull() throws Exception {
        String csv = "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence,shape_dist_traveled\n"
                   + "SH1,59.34,18.11,1,abc\n";

        parser.parseShapes(csvFrom(csv));

        assertNull(parser.shapes.get("SH1").get(0).distTraveled);
    }

    @Test
    void parseShapes_outOfOrderPoints_sortedBySequence() throws Exception {
        String csv = "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence\n"
                   + "SH1,59.34,18.11,2\n"
                   + "SH1,59.35,18.12,1\n";

        parser.parseShapes(csvFrom(csv));

        assertEquals(1, parser.shapes.get("SH1").get(0).sequence);
        assertEquals(2, parser.shapes.get("SH1").get(1).sequence);
    }

    // ----------------
    // parseCalendar
    // ----------------

    @Test
    void parseCalendar_missingRequiredColumns_throws() {
        String csv = "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday\n"
                   + "SVC1,1,1,1,1,1,0,0\n";

        assertThrows(IOException.class, () -> parser.parseCalendar(csvFrom(csv)));
    }

    @Test
    void parseCalendar_validRow_storesConcatenatedWeek() throws Exception {
        String csv = "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n"
                   + "S1,1,1,1,1,1,0,0,20240101,20241231\n";

        parser.parseCalendar(csvFrom(csv));

        Calendar c = parser.calendar.get("S1");
        assertNotNull(c);
        assertTrue(c.week[0]);
        assertTrue(c.week[1]);
        assertTrue(c.week[2]);
        assertTrue(c.week[3]);
        assertTrue(c.week[4]);
        assertFalse(c.week[5]);
        assertFalse(c.week[6]);
        assertEquals("20240101", c.startDate);
        assertEquals("20241231", c.endDate);
    }

    @Test
    void parseCalendar_emptyServiceId_throws() {
        String csv = "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n"
                   + ",1,1,1,1,1,0,0,20240101,20241231\n";

        assertThrows(IOException.class, () -> parser.parseCalendar(csvFrom(csv)));
    }

    @Test
    void parseCalendar_incompleteWeek_throws() {
        String csv = "service_id,monday,tuesday,wednesday,thursday,friday,saturday,start_date,end_date\n"
                   + "S1,1,1,1,1,1,0,20240101,20241231\n";

        assertThrows(IOException.class, () -> parser.parseCalendar(csvFrom(csv)));
    }

    @Test
    void parseCalendar_emptyDate_throws() {
        String csv = "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n"
                   + "S1,1,1,1,1,1,0,0,,20241231\n";

        assertThrows(IOException.class, () -> parser.parseCalendar(csvFrom(csv)));
    }

    // -------------------
    // parseCalendarDates
    // -------------------

    @Test
    void parseCalendarDates_missingRequiredColumns_throws() {
        String csv = "service_id,date\n"
                   + "S1,20240115\n";

        assertThrows(IOException.class, () -> parser.parseCalendarDates(csvFrom(csv)));
    }

    @Test
    void parseCalendarDates_validRow_isStored() throws Exception {
        String csv = "service_id,date,exception_type\n"
                   + "SVC1,20240115,1\n";

        parser.parseCalendarDates(csvFrom(csv));

        CalendarDates key = new CalendarDates("SVC1", LocalDate.of(2024, 1, 15));
        assertEquals("1", parser.calendar_dates.get(key));
    }

    @Test
    void parseCalendarDates_emptyServiceId_throws() {
        String csv = "service_id,date,exception_type\n"
                   + ",20240115,1\n";

        assertThrows(IOException.class, () -> parser.parseCalendarDates(csvFrom(csv)));
    }

    @Test
    void parseCalendarDates_emptyDate_throws() {
        String csv = "service_id,date,exception_type\n"
                   + "S1,,1\n";

        assertThrows(IOException.class, () -> parser.parseCalendarDates(csvFrom(csv)));
    }

    @Test
    void parseCalendarDates_emptyExceptionType_throws() {
        String csv = "service_id,date,exception_type\n"
                   + "S1,20240115,\n";

        assertThrows(IOException.class, () -> parser.parseCalendarDates(csvFrom(csv)));
    }
}
