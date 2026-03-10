package com.example.chickenwar.config;

public final class ConfigKeys {

    private ConfigKeys() {
    }

    public static final class Warp {
        public static final String WORLD = "warp.world";
        public static final String X = "warp.x";
        public static final String Y = "warp.y";
        public static final String Z = "warp.z";
        public static final String YAW = "warp.yaw";
        public static final String PITCH = "warp.pitch";

        private Warp() {
        }
    }

    public static final class Arena {
        public static final String FLOOR_FENCE = "arena.floor-fence";
        public static final String FLOOR_MATERIAL_EVEN = "arena.floor-material_even";
        public static final String FLOOR_MATERIAL_ODD = "arena.floor-material_odd";
        public static final String CHICKEN1_NAME = "arena.chicken1-name";
        public static final String CHICKEN2_NAME = "arena.chicken2-name";

        private Arena() {
        }
    }

    public static final class Betting {
        public static final String PRESTART_LOCK_SECONDS = "betting.prestart-lock-seconds";

        private Betting() {
        }
    }

    public static final class AutoStart {
        public static final String ENABLED = "auto-start.enabled";
        public static final String DELAY_SECONDS = "auto-start.delay-seconds";

        private AutoStart() {
        }
    }

    public static final class AutoRestart {
        public static final String ENABLED = "auto-restart.enabled";
        public static final String DELAY_SECONDS = "auto-restart.delay-seconds";

        private AutoRestart() {
        }
    }

    public static final class BossBar {
        public static final String ENABLED = "bossbar.enabled";
        public static final String RADIUS = "bossbar.radius";

        private BossBar() {
        }
    }

    public static final class ActionBar {
        public static final String ENABLED = "actionbar.enabled";
        public static final String RADIUS = "actionbar.radius";

        private ActionBar() {
        }
    }

    public static final class Combat {
        public static final String CHICKEN_HEALTH = "chicken-health";
        public static final String BASE_DAMAGE = "base-damage";
        public static final String SKILL_CHANCE = "skill-chance";

        private Combat() {
        }
    }

    public static final class BettingLogic {
        public static final String TAX_PERCENTAGE = "betting-logic.tax.percentage";
        public static final String JACKPOT_ENABLED = "betting-logic.jackpot-system.enabled";
        public static final String MAX_PAYOUT_MULTIPLIER = "betting-logic.jackpot-system.excess-handling.max-payout-multiplier";
        public static final String POT_CONTRIBUTION_PERCENT = "betting-logic.jackpot-system.excess-handling.pot-contribution-percent";
        public static final String POT_EXTRACT_PERCENT = "betting-logic.jackpot-system.seeding.pot-extract-percent";
        public static final String MAX_SEED_AMOUNT = "betting-logic.jackpot-system.seeding.max-seed-amount";
        public static final String SPLIT_MODE = "betting-logic.jackpot-system.seeding.split-mode";
        public static final String MAX_SKEW_RATIO = "betting-logic.jackpot-system.seeding.max-skew-ratio";
        public static final String MIN_RATIO = "betting-logic.liquidity.min-ratio";
        public static final String REFUND_ON_FAIL = "betting-logic.liquidity.refund-on-fail";
        public static final String MIN_BET = "betting-logic.limits.min-bet";
        public static final String INITIAL_MAX_BET = "betting-logic.limits.initial-max-bet";
        public static final String AUTO_EXPAND = "betting-logic.limits.auto-expand";
        public static final String SCALING_ENABLED = "betting-logic.scaling.enabled";
        public static final String DECREASE_ON_CANCEL = "betting-logic.scaling.decrease-on-cancel";
        public static final String INCREASE_ON_SUCCESS = "betting-logic.scaling.increase-on-success";
        public static final String ABSOLUTE_MIN = "betting-logic.scaling.absolute-min";
        public static final String ABSOLUTE_MAX = "betting-logic.scaling.absolute-max";

        private BettingLogic() {
        }
    }
}
