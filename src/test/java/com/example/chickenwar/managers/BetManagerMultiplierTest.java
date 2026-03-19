package com.example.chickenwar.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BetManagerMultiplierTest {

    @Test
    void shouldReturnDefaultWhenAnySideHasNoBets() {
        // A missing side means there is no valid underdog ratio yet.
        assertEquals(1.0D, BetManager.calculateUnderdogMultiplier(0L, 1000L, 5.0D));
        assertEquals(1.0D, BetManager.calculateUnderdogMultiplier(1000L, 0L, 5.0D));
    }

    @Test
    void shouldCalculateRawUnderdogMultiplierWhenBelowCap() {
        // 1000 vs 1000 -> (1000 + 1000) / 1000 = 2.0
        assertEquals(2.0D, BetManager.calculateUnderdogMultiplier(1000L, 1000L, 5.0D));
    }

    @Test
    void shouldClampUnderdogMultiplierToConfiguredMaximum() {
        // 10000 vs 1000 -> raw 11.0, but must be clamped to x5.0
        assertEquals(5.0D, BetManager.calculateUnderdogMultiplier(10_000L, 1_000L, 5.0D));
    }

    @Test
    void shouldKeepRawMultiplierWhenCapIsDisabled() {
        // Non-positive cap means no clamping is applied.
        assertEquals(11.0D, BetManager.calculateUnderdogMultiplier(10_000L, 1_000L, 0.0D));
    }
}

