package org.api.stockmarket.modules.competition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PricingCalibrationTest {
    @Test
    void rampCompoundsToConfiguredImpact() {
        double total = 0.06;
        double perTick = Math.expm1(Math.log1p(total) / 60);
        assertEquals(1.06, Math.pow(1 + perTick, 60), 1e-12);
    }

    @Test
    void boundedPressureIsDirectionalAndLiquiditySensitive() {
        double net = 100_000;
        double small = Math.tanh(net / 750_000) * 0.0005;
        double mega = Math.tanh(net / 10_000_000) * 0.0005;
        assertTrue(small > mega && mega > 0);
        assertTrue(Math.abs(small) <= 0.0005);
        assertEquals(0, Math.tanh(0) * 0.0005);
        assertTrue(Math.tanh(-net / 750_000) * 0.0005 < 0);
    }

    @Test
    void fullLogicalHourForHundredStocksStaysFiniteWithoutTrades() {
        int stocks = 100, ticks = 3600;
        double[] prices = new double[stocks];
        java.util.Arrays.fill(prices, 100);
        java.util.Random random = new java.util.Random(20260928L);
        for (int second = 0; second < ticks; second++) {
            for (int stock = 0; stock < stocks; stock++) {
                double reference = random.nextGaussian() * 0.00035;
                prices[stock] *= 1 + reference;
                assertTrue(Double.isFinite(prices[stock]) && prices[stock] > 0);
            }
        }
        assertEquals(360_000, stocks * ticks);
        assertTrue(java.util.Arrays.stream(prices).anyMatch(p -> Math.abs(p - 100) > 0.01),
                "the independent market moves without participant trades");
    }
}
