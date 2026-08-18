package com.workshop.agent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorToolTest {

    private final CalculatorTool tool = new CalculatorTool();

    @Test
    void testBasicArithmetic() {
        assertEquals(15.0, tool.calculate("ADD", 10.0, 5.0));
        assertEquals(5.0, tool.calculate("SUBTRACT", 10.0, 5.0));
        assertEquals(50.0, tool.calculate("MULTIPLY", 10.0, 5.0));
        assertEquals(2.0, tool.calculate("DIVIDE", 10.0, 5.0));
    }

    @Test
    void testDivisionByZero() {
        assertThrows(IllegalArgumentException.class, () -> tool.calculate("DIVIDE", 10.0, 0.0));
    }

    @Test
    void testPowerAndRoot() {
        assertEquals(8.0, tool.powerOrRoot("POWER", 2.0, 3.0));
        assertEquals(5.0, tool.powerOrRoot("SQRT", 25.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> tool.powerOrRoot("SQRT", -4.0, 0.0));
    }
}
