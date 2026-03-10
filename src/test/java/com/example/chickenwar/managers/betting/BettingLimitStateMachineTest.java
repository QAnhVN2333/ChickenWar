package com.example.chickenwar.managers.betting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BettingLimitStateMachineTest {

    @Test
    void shouldFailLiquidityWhenWeakerSideBelowMinRatio() {
        BettingLimitStateMachine machine = new BettingLimitStateMachine(
                50_000L,
                true,
                0.1D,
                true,
                0.15D,
                0.10D,
                10_000L,
                1_000_000L
        );

        assertFalse(machine.passesLiquidity(10_000L, 800L));
        assertTrue(machine.passesLiquidity(10_000L, 1_000L));
    }

    @Test
    void shouldExpandDynamicLimitByOpponentPoolWhenEnabled() {
        BettingLimitStateMachine machine = new BettingLimitStateMachine(
                50_000L,
                true,
                0.1D,
                true,
                0.15D,
                0.10D,
                10_000L,
                1_000_000L
        );

        assertEquals(100_000L, machine.getDynamicLimitForSide(10_000L));
        assertEquals(50_000L, machine.getDynamicLimitForSide(3_000L));
    }

    @Test
    void shouldKeepBaseLimitWhenAutoExpandDisabled() {
        BettingLimitStateMachine machine = new BettingLimitStateMachine(
                50_000L,
                false,
                0.1D,
                true,
                0.15D,
                0.10D,
                10_000L,
                1_000_000L
        );

        assertEquals(50_000L, machine.getDynamicLimitForSide(50_000L));
    }

    @Test
    void shouldDecreaseOnCancelAndClampToAbsoluteMin() {
        BettingLimitStateMachine machine = new BettingLimitStateMachine(
                10_500L,
                true,
                0.1D,
                true,
                0.15D,
                0.10D,
                10_000L,
                1_000_000L
        );

        machine.onRoundCancelled();

        assertEquals(10_000L, machine.getCurrentInitialMaxBet());
    }

    @Test
    void shouldIncreaseOnSuccessAndClampToAbsoluteMax() {
        BettingLimitStateMachine machine = new BettingLimitStateMachine(
                990_000L,
                true,
                0.1D,
                true,
                0.15D,
                0.10D,
                10_000L,
                1_000_000L
        );

        machine.onRoundSucceeded();

        assertEquals(1_000_000L, machine.getCurrentInitialMaxBet());
    }

    @Test
    void shouldApplyRoundTransitionsInSequence() {
        BettingLimitStateMachine machine = new BettingLimitStateMachine(
                50_000L,
                true,
                0.1D,
                true,
                0.2D,
                0.1D,
                10_000L,
                1_000_000L
        );

        machine.onRoundCancelled();
        assertEquals(40_000L, machine.getCurrentInitialMaxBet());

        machine.onRoundSucceeded();
        assertEquals(44_000L, machine.getCurrentInitialMaxBet());
    }
}

