package com.team18;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.team18.parser.GTFSParser;
import com.team18.model.RouteStep;
import com.team18.model.RouteStepType;
import com.team18.util.ParsingUtil;
import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.routing.raptor.RaptorBuilder;
import com.team18.routing.raptor.RaptorNetwork;

public class RaptorAlgorithmTest {
    
    private static RaptorNetwork raptorNetwork;
    
    @BeforeAll
    private static void initializeNetworkOnce() {
        try {
            GTFSParser parser = new GTFSParser();
            parser.loadFromZip("data/stockholm/sl_center.zip");
            RaptorBuilder builder = new RaptorBuilder();
            raptorNetwork = builder.build(parser.agencies, parser.stops, parser.routes, parser.trips);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void shouldBe0MinWalkWhenStartAndEndAreIdentical() {
        try {
            RaptorAlgorithm raptor = new RaptorAlgorithm(raptorNetwork);
            List<RouteStep> steps = raptor.getFastestTrip(59.2500, 17.9000, 59.2500, 17.9000, ParsingUtil.timeStringToSecondsAfterMidnight("08:30"));
    
            assertEquals(1, steps.size());
            RouteStep step = steps.get(0);
            assertEquals(0, step.durationMinutes, "Duration should be exactly 0 minutes");
            assertTrue(step.routeStepType == RouteStepType.DIRECT_WALK, "Step should be a direct walking step");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void shouldFindTransitRouteFromCentralToSpanga() {
        try {
            RaptorAlgorithm raptor = new RaptorAlgorithm(raptorNetwork);
            List<RouteStep> steps = raptor.getFastestTrip(59.3301, 18.0582, 59.392128, 17.903773, ParsingUtil.timeStringToSecondsAfterMidnight("08:30"));

            assertTrue(steps.size() > 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void disablingAStopInARouteShouldYieldADifferentRoute() {
        String stopToDisable = null;
        RaptorAlgorithm raptor = new RaptorAlgorithm(raptorNetwork);

        try {
            List<RouteStep> stepsOriginal = raptor.getFastestTrip(59.3301, 18.0582, 59.392128, 17.903773, ParsingUtil.timeStringToSecondsAfterMidnight("08:30"));
            
            assertTrue(stepsOriginal.size() > 1);
            
            // Disable boarding station forcing new algorithm to take different route
            RouteStep firstStepOriginal = stepsOriginal.get(0);
            stopToDisable = firstStepOriginal.toStop.id;
            raptorNetwork.toggleStop(stopToDisable);

            List<RouteStep> stepsAltered = raptor.getFastestTrip(59.3301, 18.0582, 59.392128, 17.903773, ParsingUtil.timeStringToSecondsAfterMidnight("08:30"));

            assertTrue(stepsAltered.size() > 1);

            RouteStep firstStepAltered = stepsAltered.get(0);

            // Check that it actually took a different route
            assertNotEquals(stopToDisable, firstStepAltered.toStop.id);

            // Check that new route is worse than original 
            RouteStep lastStepOriginal = stepsOriginal.getLast();
            double arrivalTimeOriginal = lastStepOriginal.startTimeSecondsAfterMidnight + lastStepOriginal.durationMinutes*60;

            RouteStep lastStepAltered = stepsAltered.getLast();
            double arrivalTimeAltered = lastStepAltered.startTimeSecondsAfterMidnight + lastStepAltered.durationMinutes*60;

            assertTrue(arrivalTimeOriginal < arrivalTimeAltered, "Disabling a stop should make a slower route");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (stopToDisable != null) {
                raptorNetwork.toggleStop(stopToDisable);
            }
        }
    }

    @Test
    void disablingARouteShouldResultInADifferentJourney() {
        int raptorRouteToDisable = -1;
        RaptorAlgorithm raptor = new RaptorAlgorithm(raptorNetwork);

        try {
            List<RouteStep> stepsOriginal = raptor.getFastestTrip(59.3301, 18.0582, 59.392128, 17.903773, ParsingUtil.timeStringToSecondsAfterMidnight("08:30"));
            
            assertTrue(stepsOriginal.size() > 1);
            
            // Disable boarding station forcing new algorithm to take different route
            RouteStep firstStepOriginal = stepsOriginal.get(0);
            raptorRouteToDisable = firstStepOriginal.raptorRoute.id;
            raptorNetwork.toggleRoute(raptorRouteToDisable);

            List<RouteStep> stepsAltered = raptor.getFastestTrip(59.3301, 18.0582, 59.392128, 17.903773, ParsingUtil.timeStringToSecondsAfterMidnight("08:30"));

            assertTrue(stepsAltered.size() > 1);

            RouteStep firstStepAltered = stepsAltered.get(0);

            // Check that it actually took a different route
            assertNotEquals(raptorRouteToDisable, firstStepAltered.raptorRoute.id);

            // Check that new route is worse than original 
            RouteStep lastStepOriginal = stepsOriginal.getLast();
            double arrivalTimeOriginal = lastStepOriginal.startTimeSecondsAfterMidnight + lastStepOriginal.durationMinutes*60;

            RouteStep lastStepAltered = stepsAltered.getLast();
            double arrivalTimeAltered = lastStepAltered.startTimeSecondsAfterMidnight + lastStepAltered.durationMinutes*60;

            assertTrue(arrivalTimeOriginal < arrivalTimeAltered, "Disabling a route should make a slower journey");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (raptorRouteToDisable != -1) {
                raptorNetwork.toggleRoute(raptorRouteToDisable);
            }
        }
    }



}
