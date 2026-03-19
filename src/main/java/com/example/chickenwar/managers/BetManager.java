package com.example.chickenwar.managers;

import com.example.chickenwar.ChickenWarPlugin;
import com.example.chickenwar.config.ConfigKeys;
import com.example.chickenwar.managers.betting.BettingLimitStateMachine;
import com.example.chickenwar.managers.betting.BettingStateStore;
import com.example.chickenwar.managers.betting.PariMutuelCalculator;
import com.example.chickenwar.utils.MessageUtils;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class BetManager {
    private static final UUID SERVER_POT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ChickenWarPlugin plugin;
    private final Random random = new Random();

    private final Map<UUID, Long> redBets = new HashMap<>();
    private final Map<UUID, Long> blueBets = new HashMap<>();
    private final Map<UUID, Long> pendingClaims = new HashMap<>();

    private final File stateFile;

    private boolean bettingOpen = false;
    private long jackpot = 0L;
    private long currentInitialMaxBet;
    private long lastSeedRed;
    private long lastSeedBlue;

    public BetManager(ChickenWarPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "data.yml");
        this.currentInitialMaxBet = getInitialMaxBetFromConfig();
        loadState();
        recoverUnfinishedRoundIfNeeded();
    }

    public void openBetting() {
        redBets.clear();
        blueBets.clear();
        bettingOpen = true;
        lastSeedRed = 0L;
        lastSeedBlue = 0L;

        if (isJackpotEnabled()) {
            seedFromJackpot();
        }
        saveState();
    }

    public boolean lockBettingAndValidateLiquidity() {
        closeBetting();
        if (passesLiquidity()) {
            return true;
        }

        boolean refundOnFail = plugin.getConfig().getBoolean(ConfigKeys.BettingLogic.REFUND_ON_FAIL, true);
        if (refundOnFail) {
            refundAll(true);
        } else {
            // Drop round bets as configured when liquidity validation fails without refund.
            clearRoundBets();
            saveState();
            MessageUtils.broadcast(msg("bet.liquidity-failed"), NamedTextColor.RED);
        }

        applyScalingOnCancel();
        return false;
    }

    public void closeBetting() {
        bettingOpen = false;
        saveState();
    }

    public boolean isBettingOpen() {
        return bettingOpen;
    }

    public boolean hasAnyBet() {
        return hasHumanBet(redBets) || hasHumanBet(blueBets);
    }

    public boolean hasAnyRoundBet() {
        return hasPositiveBet(redBets) || hasPositiveBet(blueBets);
    }

    public boolean hasPlayerBet(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return redBets.getOrDefault(playerId, 0L) > 0L || blueBets.getOrDefault(playerId, 0L) > 0L;
    }

    public Map<UUID, Long> getHumanBettors() {
        Map<UUID, Long> bettors = new HashMap<>();
        mergeHumanBets(bettors, redBets);
        mergeHumanBets(bettors, blueBets);
        return bettors;
    }

    public boolean placeBet(Player player, String side, double amountRaw) {
        if (!bettingOpen) {
            MessageUtils.send(player, msg("bet.closed"), NamedTextColor.RED);
            return false;
        }

        if (!"red".equals(side) && !"blue".equals(side)) {
            MessageUtils.send(player, msg("bet.invalid-side"), NamedTextColor.RED);
            return false;
        }

        long amount = (long) Math.ceil(amountRaw);
        if (amount < getMinBet()) {
            MessageUtils.send(player, msg("bet.min-bet", Map.of("amount", String.valueOf(getMinBet()))), NamedTextColor.RED);
            return false;
        }

        Economy eco = ChickenWarPlugin.getEconomy();
        if (eco == null) {
            MessageUtils.send(player, msg("bet.economy-error"), NamedTextColor.RED);
            return false;
        }

        if (!eco.has(player, amount)) {
            MessageUtils.send(player, msg("bet.not-enough-money"), NamedTextColor.RED);
            return false;
        }

        if (("red".equals(side) && blueBets.containsKey(player.getUniqueId()))
                || ("blue".equals(side) && redBets.containsKey(player.getUniqueId()))) {
            MessageUtils.send(player, msg("bet.already.other.side"), NamedTextColor.RED);
            return false;
        }

        long sideLimit = getDynamicLimitForSide(side);
        long currentOnSide = "red".equals(side) ? getTotalRed() : getTotalBlue();
        if (currentOnSide + amount > sideLimit) {
            MessageUtils.send(player, msg("bet.max-limit", Map.of("amount", String.valueOf(sideLimit))), NamedTextColor.RED);
            return false;
        }

        EconomyResponse withdraw = eco.withdrawPlayer(player, amount);
        if (!withdraw.transactionSuccess()) {
            MessageUtils.send(player, msg("bet.economy-error"), NamedTextColor.RED);
            return false;
        }

        if ("red".equals(side)) {
            redBets.put(player.getUniqueId(), redBets.getOrDefault(player.getUniqueId(), 0L) + amount);
        } else {
            blueBets.put(player.getUniqueId(), blueBets.getOrDefault(player.getUniqueId(), 0L) + amount);
        }

        MessageUtils.broadcast(msg("bet.placed", Map.of(
                "player", player.getName(),
                "amount", String.valueOf(amount),
                "side", "red".equals(side) ? msg("bet.side-red") : msg("bet.side-blue"),
                "total_red", String.valueOf(getTotalRed()),
                "total_blue", String.valueOf(getTotalBlue())
        )), NamedTextColor.YELLOW);

        saveState();
        return true;
    }

    public void refundAll() {
        refundAll(false);
    }

    public void refundAll(boolean liquidityFail) {
        closeBetting();

        boolean refunded = false;
        for (Map.Entry<UUID, Long> entry : redBets.entrySet()) {
            refunded |= refundEntry(entry);
        }
        for (Map.Entry<UUID, Long> entry : blueBets.entrySet()) {
            refunded |= refundEntry(entry);
        }

        redBets.clear();
        blueBets.clear();
        saveState();

        if (refunded) {
            MessageUtils.broadcast(msg(liquidityFail ? "bet.refund-all-liquidity" : "bet.refund-all"), NamedTextColor.RED);
        }
    }

    public PayoutSummary processPayout(String winnerSide) {
        closeBetting();

        Map<UUID, Long> winners = "red".equals(winnerSide) ? redBets : blueBets;
        Map<UUID, Long> losers = "red".equals(winnerSide) ? blueBets : redBets;

        long loserPool = losers.values().stream().mapToLong(Long::longValue).sum();
        long winnerPool = winners.values().stream().mapToLong(Long::longValue).sum();
        boolean jackpotEnabled = isJackpotEnabled();

        if (winners.isEmpty() || winnerPool <= 0) {
            if (jackpotEnabled) {
                jackpot += loserPool;
            }
            MessageUtils.broadcast(msg("bet.payout.no-winner-bets"), NamedTextColor.GRAY);
            clearRoundBets();
            saveState();
            return PayoutSummary.empty();
        }

        PariMutuelCalculator.CalculationResult result = PariMutuelCalculator.calculate(
                winners,
                loserPool,
                getTaxPercent(),
                getMaxPayoutMultiplier(),
                Set.of(SERVER_POT_UUID)
        );

        MessageUtils.broadcast(msg("bet.payout.title"), NamedTextColor.GOLD);

        long totalPaid = 0L;
        Map<UUID, WinnerPayout> winnerDetails = new HashMap<>();
        for (Map.Entry<UUID, Long> entry : result.payouts().entrySet()) {
            UUID playerId = entry.getKey();
            long payout = (long) Math.floor(entry.getValue());
            totalPaid += payout;

            if (SERVER_POT_UUID.equals(playerId)) {
                if (jackpotEnabled) {
                    jackpot += payout;
                }
                continue;
            }

            long wager = winners.getOrDefault(playerId, 0L);
            long tax = calculatePlayerTax(wager, loserPool, winnerPool);
            long profit = Math.max(0L, payout - wager);
            winnerDetails.put(playerId, new WinnerPayout(wager, payout, profit, tax));

            payoutOrStoreClaim(playerId, payout);
            Player online = Bukkit.getPlayer(playerId);
            if (online != null) {
                MessageUtils.send(online, msg("bet.payout.win-player", Map.of("amount", String.valueOf(payout))), NamedTextColor.GREEN);
            }
        }

        for (UUID uid : losers.keySet()) {
            if (SERVER_POT_UUID.equals(uid)) {
                continue;
            }
            Player p = Bukkit.getPlayer(uid);
            if (p != null) {
                MessageUtils.send(p, msg("bet.payout.lose-player"), NamedTextColor.GRAY);
            }
        }

        long poolTotal = winnerPool + loserPool;
        long retainedByHouse = Math.max(0L, poolTotal - totalPaid);
        long cappedExcessToJackpot = 0L;
        if (jackpotEnabled) {
            cappedExcessToJackpot = (long) Math.floor(result.totalExcessByCap() * (getPotContributionPercent() / 100.0D));
            jackpot += Math.max(0L, cappedExcessToJackpot);
        }

        // Tax and rounding remain as sink to avoid economy inflation.
        retainedByHouse = Math.max(0L, retainedByHouse - cappedExcessToJackpot);

        applyScalingOnSuccess();
        clearRoundBets();
        saveState();

        return new PayoutSummary(
                new HashMap<>(winnerDetails),
                getHumanBetsCopy(losers),
                winnerPool,
                loserPool,
                cappedExcessToJackpot,
                result.totalExcessByCap() > 0
        );
    }

    public long claimPending(Player player) {
        UUID id = player.getUniqueId();
        long amount = pendingClaims.getOrDefault(id, 0L);
        if (amount <= 0) {
            return 0L;
        }

        if (depositToOffline(id, amount)) {
            pendingClaims.remove(id);
            saveState();
            return amount;
        }

        return 0L;
    }

    public boolean hasPendingClaim(UUID playerId) {
        return pendingClaims.getOrDefault(playerId, 0L) > 0;
    }

    public long getPendingClaim(UUID playerId) {
        return pendingClaims.getOrDefault(playerId, 0L);
    }

    public long getTotalRed() {
        return redBets.values().stream().mapToLong(Long::longValue).sum();
    }

    public long getTotalBlue() {
        return blueBets.values().stream().mapToLong(Long::longValue).sum();
    }

    private void payoutOrStoreClaim(UUID playerId, long amount) {
        if (amount <= 0) {
            return;
        }
        if (!depositToOffline(playerId, amount)) {
            pendingClaims.put(playerId, pendingClaims.getOrDefault(playerId, 0L) + amount);
        }
    }

    private boolean depositToOffline(UUID playerId, long amount) {
        Economy eco = ChickenWarPlugin.getEconomy();
        if (eco == null) {
            return false;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        EconomyResponse response = eco.depositPlayer(offline, amount);
        return response.transactionSuccess();
    }

    private boolean refundEntry(Map.Entry<UUID, Long> entry) {
        if (entry.getValue() <= 0) {
            return false;
        }

        if (SERVER_POT_UUID.equals(entry.getKey())) {
            if (isJackpotEnabled()) {
                jackpot += entry.getValue();
            }
            return true;
        }

        if (depositToOffline(entry.getKey(), entry.getValue())) {
            return true;
        }

        pendingClaims.put(entry.getKey(), pendingClaims.getOrDefault(entry.getKey(), 0L) + entry.getValue());
        return true;
    }

    private boolean passesLiquidity() {
        BettingLimitStateMachine stateMachine = createLimitStateMachine();
        return stateMachine.passesLiquidity(getTotalRed(), getTotalBlue());
    }

    private long getDynamicLimitForSide(String side) {
        long opposite = "red".equals(side) ? getTotalBlue() : getTotalRed();
        BettingLimitStateMachine stateMachine = createLimitStateMachine();
        return stateMachine.getDynamicLimitForSide(opposite);
    }

    private void seedFromJackpot() {
        double extractPercent = plugin.getConfig().getDouble(ConfigKeys.BettingLogic.POT_EXTRACT_PERCENT, 0.0D);
        long maxSeed = plugin.getConfig().getLong(ConfigKeys.BettingLogic.MAX_SEED_AMOUNT, 0L);
        if (extractPercent <= 0 || jackpot <= 0) {
            return;
        }

        long extracted = (long) Math.ceil(jackpot * (extractPercent / 100.0D));
        if (maxSeed > 0) {
            extracted = Math.min(extracted, maxSeed);
        }
        extracted = Math.min(extracted, jackpot);
        if (extracted <= 0) {
            return;
        }

        jackpot -= extracted;

        SplitMode splitMode = resolveSplitMode();
        long redSeed;
        long blueSeed;

        if (splitMode == SplitMode.EVEN) {
            redSeed = extracted / 2;
            blueSeed = extracted - redSeed;
        } else {
            double maxSkewRatio = plugin.getConfig().getDouble(ConfigKeys.BettingLogic.MAX_SKEW_RATIO, 0.8D);
            double ratio = 0.5D + (random.nextDouble() * Math.max(0.0D, maxSkewRatio - 0.5D));

            long dominant = (long) Math.ceil(extracted * ratio);
            long underdog = extracted - dominant;
            boolean redDominant = random.nextBoolean();

            redSeed = redDominant ? dominant : underdog;
            blueSeed = redDominant ? underdog : dominant;
        }

        redBets.put(SERVER_POT_UUID, Math.max(0L, redSeed));
        blueBets.put(SERVER_POT_UUID, Math.max(0L, blueSeed));
        lastSeedRed = Math.max(0L, redSeed);
        lastSeedBlue = Math.max(0L, blueSeed);
    }

    private SplitMode resolveSplitMode() {
        String configuredMode = plugin.getConfig().getString(ConfigKeys.BettingLogic.SPLIT_MODE, SplitMode.RANDOM_SKEWED.name());
        if (configuredMode == null || configuredMode.isBlank()) {
            return SplitMode.RANDOM_SKEWED;
        }

        try {
            return SplitMode.valueOf(configuredMode.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Invalid split-mode '" + configuredMode + "'. Fallback to RANDOM_SKEWED.");
            return SplitMode.RANDOM_SKEWED;
        }
    }

    private void clearRoundBets() {
        redBets.clear();
        blueBets.clear();
    }

    private void applyScalingOnCancel() {
        BettingLimitStateMachine stateMachine = createLimitStateMachine();
        stateMachine.onRoundCancelled();
        currentInitialMaxBet = stateMachine.getCurrentInitialMaxBet();
    }

    private void applyScalingOnSuccess() {
        BettingLimitStateMachine stateMachine = createLimitStateMachine();
        stateMachine.onRoundSucceeded();
        currentInitialMaxBet = stateMachine.getCurrentInitialMaxBet();
    }

    private BettingLimitStateMachine createLimitStateMachine() {
        long initialFromConfig = getInitialMaxBetFromConfig();
        long configuredMin = plugin.getConfig().getLong(ConfigKeys.BettingLogic.ABSOLUTE_MIN, initialFromConfig);
        long configuredMax = plugin.getConfig().getLong(ConfigKeys.BettingLogic.ABSOLUTE_MAX, initialFromConfig);
        long absoluteMin = Math.min(configuredMin, configuredMax);
        long absoluteMax = Math.max(configuredMin, configuredMax);

        return new BettingLimitStateMachine(
                currentInitialMaxBet,
                plugin.getConfig().getBoolean(ConfigKeys.BettingLogic.AUTO_EXPAND, true),
                plugin.getConfig().getDouble(ConfigKeys.BettingLogic.MIN_RATIO, 0.1D),
                plugin.getConfig().getBoolean(ConfigKeys.BettingLogic.SCALING_ENABLED, true),
                plugin.getConfig().getDouble(ConfigKeys.BettingLogic.DECREASE_ON_CANCEL, 0.15D),
                plugin.getConfig().getDouble(ConfigKeys.BettingLogic.INCREASE_ON_SUCCESS, 0.10D),
                absoluteMin,
                absoluteMax
        );
    }

    private void recoverUnfinishedRoundIfNeeded() {
        if (redBets.isEmpty() && blueBets.isEmpty()) {
            return;
        }

        plugin.getLogger().warning("Detected unfinished betting round. Auto-refunding contributors now.");
        refundAll();
        clearRoundBets();
        saveState();
    }

    private void loadState() {
        BettingStateStore.BettingState state = BettingStateStore.load(stateFile);
        jackpot = Math.max(0L, state.jackpot());

        if (state.currentInitialMaxBet() > 0L) {
            currentInitialMaxBet = state.currentInitialMaxBet();
        }

        redBets.putAll(state.redBets());
        blueBets.putAll(state.blueBets());
        pendingClaims.putAll(state.pendingClaims());
    }

    private void saveState() {
        BettingStateStore.BettingState state = new BettingStateStore.BettingState(
                jackpot,
                currentInitialMaxBet,
                !redBets.isEmpty() || !blueBets.isEmpty(),
                new HashMap<>(redBets),
                new HashMap<>(blueBets),
                new HashMap<>(pendingClaims)
        );
        BettingStateStore.save(stateFile, state);
    }

    private double getTaxPercent() {
        return plugin.getConfig().getDouble(ConfigKeys.BettingLogic.TAX_PERCENTAGE, 5.0D);
    }

    private double getMaxPayoutMultiplier() {
        if (plugin.getConfig().isSet(ConfigKeys.BettingLogic.MAX_PAYOUT_MULTIPLIER)) {
            return plugin.getConfig().getDouble(ConfigKeys.BettingLogic.MAX_PAYOUT_MULTIPLIER, 5.0D);
        }
        // Backward-compatible fallback for legacy configs used in older docs/issues.
        return plugin.getConfig().getDouble("max-multiplier", 5.0D);
    }

    private double getPotContributionPercent() {
        return plugin.getConfig().getDouble(ConfigKeys.BettingLogic.POT_CONTRIBUTION_PERCENT, 100.0D);
    }

    private boolean isJackpotEnabled() {
        return plugin.getConfig().getBoolean(ConfigKeys.BettingLogic.JACKPOT_ENABLED, true);
    }

    private long getMinBet() {
        return plugin.getConfig().getLong(ConfigKeys.BettingLogic.MIN_BET, 1000L);
    }

    private long getInitialMaxBetFromConfig() {
        return plugin.getConfig().getLong(ConfigKeys.BettingLogic.INITIAL_MAX_BET, 50000L);
    }

    private String msg(String path) {
        return plugin.getConfigManager().getMessage(path);
    }

    private String msg(String path, Map<String, String> placeholders) {
        return plugin.getConfigManager().getMessage(path, placeholders);
    }

    private boolean hasHumanBet(Map<UUID, Long> sideBets) {
        return sideBets.entrySet().stream().anyMatch(entry -> !SERVER_POT_UUID.equals(entry.getKey()) && entry.getValue() > 0);
    }

    private void mergeHumanBets(Map<UUID, Long> target, Map<UUID, Long> source) {
        for (Map.Entry<UUID, Long> entry : source.entrySet()) {
            if (SERVER_POT_UUID.equals(entry.getKey()) || entry.getValue() <= 0L) {
                continue;
            }
            target.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
    }

    private long calculatePlayerTax(long wager, long loserPool, long winnerPool) {
        if (wager <= 0 || winnerPool <= 0 || loserPool <= 0) {
            return 0L;
        }

        double rawProfit = (double) loserPool * ((double) wager / (double) winnerPool);
        double rawReturn = wager + rawProfit;
        double cappedReturn = rawReturn;

        double maxPayoutMultiplier = getMaxPayoutMultiplier();
        if (maxPayoutMultiplier > 0) {
            double maxAllowedReturn = Math.floor(wager * maxPayoutMultiplier);
            cappedReturn = Math.min(rawReturn, maxAllowedReturn);
        }

        double cappedProfit = Math.max(0.0D, cappedReturn - wager);
        long taxAmount = (long) Math.ceil(cappedProfit * (getTaxPercent() / 100.0D));
        return Math.max(0L, taxAmount);
    }

    private long getServerSeedOnSide(Map<UUID, Long> sideBets) {
        return Math.max(0L, sideBets.getOrDefault(SERVER_POT_UUID, 0L));
    }

    private boolean hasPositiveBet(Map<UUID, Long> sideBets) {
        return sideBets.values().stream().anyMatch(value -> value != null && value > 0L);
    }

    private Map<UUID, Long> getHumanBetsCopy(Map<UUID, Long> source) {
        Map<UUID, Long> output = new HashMap<>();
        for (Map.Entry<UUID, Long> entry : source.entrySet()) {
            if (SERVER_POT_UUID.equals(entry.getKey()) || entry.getValue() <= 0L) {
                continue;
            }
            output.put(entry.getKey(), entry.getValue());
        }
        return output;
    }


    public long getJackpot() {
        return jackpot;
    }

    public boolean addJackpot(long amount) {
        if (amount <= 0L) {
            return false;
        }
        jackpot += amount;
        saveState();
        return true;
    }

    public boolean removeJackpot(long amount) {
        if (amount <= 0L || amount > jackpot) {
            return false;
        }
        jackpot -= amount;
        saveState();
        return true;
    }

    public boolean setJackpot(long amount) {
        if (amount < 0L) {
            return false;
        }
        jackpot = amount;
        saveState();
        return true;
    }

    public long getCurrentRoundServerSeed() {
        return getServerSeedOnSide(redBets) + getServerSeedOnSide(blueBets);
    }

    public long getLastSeedAmount() {
        return lastSeedRed + lastSeedBlue;
    }

    public long getLastSeedRed() {
        return lastSeedRed;
    }

    public long getLastSeedBlue() {
        return lastSeedBlue;
    }

    public double getCurrentUnderdogMultiplier() {
        return calculateUnderdogMultiplier(getTotalRed(), getTotalBlue(), getMaxPayoutMultiplier());
    }

    static double calculateUnderdogMultiplier(long redTotal, long blueTotal, double maxMultiplier) {
        long red = Math.max(0L, redTotal);
        long blue = Math.max(0L, blueTotal);
        if (red <= 0 || blue <= 0) {
            return 1.0D;
        }

        long underdogPool = Math.min(red, blue);
        long favoritePool = Math.max(red, blue);
        if (underdogPool <= 0) {
            return 1.0D;
        }

        double rawMultiplier = ((double) (underdogPool + favoritePool)) / underdogPool;
        if (maxMultiplier <= 0.0D) {
            return rawMultiplier;
        }

        // Clamp the displayed multiplier so it never exceeds configured maximum.
        return Math.min(rawMultiplier, maxMultiplier);
    }

    public record WinnerPayout(long wager, long payout, long profit, long tax) {
    }

    public record PayoutSummary(
            Map<UUID, WinnerPayout> winnerPayouts,
            Map<UUID, Long> loserBets,
            long winnerPool,
            long loserPool,
            long jackpotOverflow,
            boolean capHit
    ) {
        public static PayoutSummary empty() {
            return new PayoutSummary(new HashMap<>(), new HashMap<>(), 0L, 0L, 0L, false);
        }
    }

    private enum SplitMode {
        RANDOM_SKEWED,
        EVEN
    }
}
