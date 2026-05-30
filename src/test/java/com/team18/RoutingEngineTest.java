package com.team18;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.*;

public class RoutingEngineTest {
    // Reference https://stackoverflow.com/questions/1647907/junit-how-to-simulate-system-in-testing

    private ByteArrayInputStream testIn;
    private ByteArrayOutputStream testOut;

    @BeforeEach
    private void setUpOutput() {
        testOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(testOut));
    }

    private void provideInput(String data) {
        testIn = new ByteArrayInputStream(data.getBytes());
        System.setIn(testIn);
    }

    private String getOutput() {
        return testOut.toString();
    }

    @Test
    void engineShouldReturnLoadedOnLoad() {
        try {
            String input = "{\"load\": \"data/stockholm/sl_center.zip\"}\r\n";
            String expectedOutput = "{\"ok\":\"loaded\"}" + System.lineSeparator();
            
            provideInput(input);
            RoutingEngine.main(new String[0]);
            assertEquals(expectedOutput, getOutput());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
