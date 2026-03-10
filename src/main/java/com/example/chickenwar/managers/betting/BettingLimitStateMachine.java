package com.example.chickenwar.managers.betting;

public class BettingLimitStateMachine {

    private final boolean autoExpand;
    private final double minRatio;
    private final boolean scalingEnabled;
    private final double decreaseOnCancel;
    private final double increaseOnSuccess;
    private final long absoluteMin;
    private final long absoluteMax;

    private long currentInitialMaxBet;

    public BettingLimitStateMachine(long currentInitialMaxBet,
                                    boolean autoExpand,
                                    double minRatio,
                                    boolean scalingEnabled,
                                    double decreaseOnCancel,
                                    double increaseOnSuccess,
                                    long absoluteMin,
                                    long absoluteMax) {
        this.currentInitialMaxBet = currentInitialMaxBet;
        this.autoExpand = autoExpand;
        this.minRatio = minRatio;
        this.scalingEnabled = scalingEnabled;
        this.decreaseOnCancel = decreaseOnCancel;
        this.increaseOnSuccess = increaseOnSuccess;
        this.absoluteMin = absoluteMin;
        this.absoluteMax = absoluteMax;
    }

    public boolean passesLiquidity(long totalRed, long totalBlue) {
        if (minRatio <= 0) {
            return true;
        }

        long weaker = Math.min(totalRed, totalBlue);
        long stronger = Math.max(totalRed, totalBlue);

        if (stronger <= 0) {
            return true;
        }

        return weaker >= Math.ceil(stronger * minRatio);
    }

    public long getDynamicLimitForSide(long oppositePool) {
        if (!autoExpand || minRatio <= 0) {
            return currentInitialMaxBet;
        }

        long expanded = (long) Math.floor(oppositePool / minRatio);
        return Math.max(currentInitialMaxBet, expanded);
    }

    public void onRoundCancelled() {
        if (!scalingEnabled) {
            return;
        }

        long next = (long) Math.floor(currentInitialMaxBet * (1.0D - decreaseOnCancel));
        currentInitialMaxBet = clamp(next);
    }

    public void onRoundSucceeded() {
        if (!scalingEnabled) {
            return;
        }

        long next = (long) Math.floor(currentInitialMaxBet * (1.0D + increaseOnSuccess));
        currentInitialMaxBet = clamp(next);
    }

    public long getCurrentInitialMaxBet() {
        return currentInitialMaxBet;
    }

    private long clamp(long value) {
        return Math.max(absoluteMin, Math.min(absoluteMax, value));
    }
}

