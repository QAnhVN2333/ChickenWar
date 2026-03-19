package com.example.chickenwar.managers.betting;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PariMutuelCalculatorTest {

    @Test
    void shouldSplitLoserPoolByRatioAndApplyTax() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        Map<UUID, Long> winners = new LinkedHashMap<>();
        winners.put(a, 100L);
        winners.put(b, 300L);

        PariMutuelCalculator.CalculationResult result = PariMutuelCalculator.calculate(winners, 400L, 5.0D, 10.0D, Collections.emptySet());

        // A share: 25% of loser pool = 100, tax ceil(5) => 95 profit + 100 wager.
        assertEquals(195L, result.payouts().get(a));
        // B share: 75% of loser pool = 300, tax ceil(15) => 285 profit + 300 wager.
        assertEquals(585L, result.payouts().get(b));
        assertEquals(20L, result.totalTax());
        assertEquals(0L, result.totalExcessByCap());
    }

    @Test
    void shouldCapPayoutAndTrackExcessForJackpot() {
        UUID underdog = UUID.randomUUID();

        Map<UUID, Long> winners = new LinkedHashMap<>();
        winners.put(underdog, 100L);

        PariMutuelCalculator.CalculationResult result = PariMutuelCalculator.calculate(winners, 1000L, 0.0D, 5.0D, Collections.emptySet());

        // Raw return is 1100, capped to 500 with x5 cap.
        assertEquals(500L, result.payouts().get(underdog));
        assertEquals(0L, result.totalTax());
        assertEquals(600L, result.totalExcessByCap());
    }

    @Test
    void shouldApplyTaxOnCappedProfitOnly() {
        UUID underdog = UUID.randomUUID();

        Map<UUID, Long> winners = new LinkedHashMap<>();
        winners.put(underdog, 100L);

        PariMutuelCalculator.CalculationResult result = PariMutuelCalculator.calculate(winners, 1000L, 5.0D, 5.0D, Collections.emptySet());

        // Raw return 1100 is capped to 500, so taxable profit is 400 (not 1000).
        assertEquals(480L, result.payouts().get(underdog));
        assertEquals(20L, result.totalTax());
        assertEquals(600L, result.totalExcessByCap());
    }

    @Test
    void shouldRoundTaxUpAndProfitDownForSafety() {
        UUID bettor = UUID.randomUUID();

        Map<UUID, Long> winners = new LinkedHashMap<>();
        winners.put(bettor, 333L);

        PariMutuelCalculator.CalculationResult result = PariMutuelCalculator.calculate(winners, 101L, 5.0D, 10.0D, Collections.emptySet());

        // Profit 101 => tax ceil(5.05)=6, payout = 333 + floor(95) = 428.
        assertEquals(428L, result.payouts().get(bettor));
        assertEquals(6L, result.totalTax());
        assertEquals(0L, result.totalExcessByCap());
    }

    @Test
    void shouldSkipTaxForUntaxedParticipant() {
        UUID serverSeed = UUID.randomUUID();

        Map<UUID, Long> winners = new LinkedHashMap<>();
        winners.put(serverSeed, 100L);

        PariMutuelCalculator.CalculationResult result = PariMutuelCalculator.calculate(
                winners,
                100L,
                10.0D,
                10.0D,
                Set.of(serverSeed)
        );

        // Untaxed participant receives full return (wager + full profit).
        assertEquals(200L, result.payouts().get(serverSeed));
        assertEquals(0L, result.totalTax());
    }
}
