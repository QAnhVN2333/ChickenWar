package com.example.chickenwar.managers.betting;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PariMutuelCalculator {

    private PariMutuelCalculator() {
    }

    public static CalculationResult calculate(
            Map<UUID, Long> winnerBets,
            long loserPool,
            double taxPercent,
            double maxPayoutMultiplier
    ) {
        if (winnerBets == null || winnerBets.isEmpty()) {
            return new CalculationResult(Collections.emptyMap(), 0L, 0L);
        }

        long winnerPool = winnerBets.values().stream().mapToLong(Long::longValue).sum();
        if (winnerPool <= 0) {
            return new CalculationResult(Collections.emptyMap(), 0L, 0L);
        }

        Map<UUID, Long> payouts = new HashMap<>();
        long totalTax = 0L;
        long totalExcessByCap = 0L;

        for (Map.Entry<UUID, Long> entry : winnerBets.entrySet()) {
            long wager = entry.getValue();
            if (wager <= 0) {
                continue;
            }

            // Raw pari-mutuel profit before any cap/tax.
            double rawProfit = (double) loserPool * ((double) wager / (double) winnerPool);
            double rawReturn = wager + rawProfit;
            double cappedReturn = rawReturn;

            if (maxPayoutMultiplier > 0) {
                double maxAllowedReturn = Math.floor(wager * maxPayoutMultiplier);
                cappedReturn = Math.min(rawReturn, maxAllowedReturn);
                totalExcessByCap += (long) Math.floor(Math.max(0.0D, rawReturn - cappedReturn));
            }

            // Tax must be calculated from the real (possibly capped) profit only.
            double cappedProfit = Math.max(0.0D, cappedReturn - wager);
            long taxAmount = (long) Math.ceil(cappedProfit * (taxPercent / 100.0D));
            if (taxAmount < 0) {
                taxAmount = 0;
            }

            // Round down net profit to avoid precision-based inflation.
            double netProfitAfterTax = Math.max(0.0D, cappedProfit - taxAmount);
            long payout = wager + (long) Math.floor(netProfitAfterTax);

            payouts.put(entry.getKey(), payout);
            totalTax += taxAmount;
        }

        return new CalculationResult(payouts, totalTax, totalExcessByCap);
    }

    public record CalculationResult(
            Map<UUID, Long> payouts,
            long totalTax,
            long totalExcessByCap
    ) {
    }
}
