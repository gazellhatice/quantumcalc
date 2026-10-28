package com.quantumcalc.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class StatsCalculatorTest {

    @Test
    void factorial() {
        assertEquals(1, StatsCalculator.factorial(0));
        assertEquals(120, StatsCalculator.factorial(5));
        assertThrows(IllegalArgumentException.class, () -> StatsCalculator.factorial(-1));
        assertThrows(IllegalArgumentException.class, () -> StatsCalculator.factorial(171));
    }

    @Test
    void combinationAndPermutation() {
        assertEquals(10, StatsCalculator.combination(5, 2));
        assertEquals(20, StatsCalculator.permutation(5, 2));
        assertThrows(IllegalArgumentException.class, () -> StatsCalculator.combination(3, 5));
    }

    @Test
    void standardDeviation() {
        List<Double> data = List.of(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
        assertEquals(2.0, StatsCalculator.standardDeviation(data, false), 1e-9);
        assertEquals(2.138089935, StatsCalculator.standardDeviation(data, true), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> StatsCalculator.standardDeviation(List.of(1.0), true));
    }
}
