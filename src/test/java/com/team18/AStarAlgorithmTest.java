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
import com.team18.routing.AStar.TransitGraph;
import com.team18.routing.raptor.RaptorAlgorithm;
import com.team18.routing.AStar.AStarRouter;

public class AStarAlgorithmTest {
    
    private static TransitGraph aStarNetwork;
    private GTFSParser parser = new GTFSParser();
    
    @BeforeAll
    private static void initializeNetworkOnce(GTFSParser parser) {
        try {
            parser.loadFromZip("data/stockholm/sl_center.zip");
            TransitGraph builder = new TransitGraph();
            builder.build(parser);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void shouldBe0MinWalkWhenStartAndEndAreIdentical() {
        try {
            AStarRouter astar = new AStarRouter(parser);
            List<RouteStep> steps = astar.getFastestTrip(59.2500, 17.9000, 59.2500, 17.9000, ParsingUtil.timeStringToSecondsAfterMidnight("08:30"));
    
            assertEquals(1, steps.size());
            RouteStep step = steps.get(0);
            assertEquals(0, step.waitTimeSecs + step.tripTimeSecs, "Duration should be exactly 0 minutes");
            assertTrue(step.routeStepType == RouteStepType.DIRECT_WALK, "Step should be a direct walking step");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
