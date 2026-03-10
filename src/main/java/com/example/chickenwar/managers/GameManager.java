package com.example.chickenwar.managers;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import com.example.chickenwar.ChickenWarPlugin;
import com.example.chickenwar.config.ConfigKeys;
import com.example.chickenwar.utils.MessageUtils;
import com.example.chickenwar.utils.SoundUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GameManager {
    private enum GameState {
        IDLE,
        BETTING,
        COUNTDOWN,
        RUNNING,
        ENDING
    }

    private final ChickenWarPlugin plugin;
    private final ArenaManager arenaManager;
    private final BetManager betManager;
    private final SkillManager skillManager;

    private final Set<UUID> hiddenBossBarPlayers = new HashSet<>();
    private final Map<GameState, BossBar> bossBars = new EnumMap<>(GameState.class);

    private GameState gameState = GameState.IDLE;
    private BukkitRunnable gameTask;
    private BukkitRunnable countdownTask;
    private BukkitRunnable autoStartTask;
    private BukkitRunnable autoRestartTask;
    private BukkitRunnable idleUiTask;
    private boolean payoutProcessed;
    private int idleBroadcastTickerSeconds;

    private static final LegacyComponentSerializer BOSSBAR_SERIALIZER = LegacyComponentSerializer.legacySection();

    public GameManager(ChickenWarPlugin plugin) {
        this.plugin = plugin;
        this.arenaManager = new ArenaManager(plugin);
        this.betManager = new BetManager(plugin);
        this.skillManager = new SkillManager(plugin);

        // Recover arena blocks and stale fighter entities from previous crash.
        arenaManager.restoreArenaFromDiskIfPresent();
        arenaManager.cleanupOrphanFighters();

        startIdleUiTask();
    }

    public void buildAndInvite(Player builder) {
        String worldName = plugin.getConfig().getString(ConfigKeys.Warp.WORLD);
        if (worldName == null || !builder.getWorld().getName().equals(worldName)) {
            MessageUtils.send(builder, msg("game.build.invalid-world"), NamedTextColor.RED);
            return;
        }

        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            MessageUtils.send(builder, msg("game.build.world-missing"), NamedTextColor.RED);
            return;
        }

        if (arenaManager.hasArena()) {
            MessageUtils.send(builder, msg("game.build.arena-active"), NamedTextColor.RED);
            return;
        }

        Location warpLoc = new Location(world,
                plugin.getConfig().getDouble(ConfigKeys.Warp.X),
                plugin.getConfig().getDouble(ConfigKeys.Warp.Y),
                plugin.getConfig().getDouble(ConfigKeys.Warp.Z));

        cancelSchedulers();
        payoutProcessed = false;

        if (!arenaManager.buildArena(warpLoc)) {
            MessageUtils.send(builder, msg("game.build.arena-create-failed"), NamedTextColor.RED);
            return;
        }

        betManager.openBetting();
        gameState = GameState.BETTING;
        announceBettingOpened();
        updateBettingBossBar();
    }

    public void startFight(Player sender) {
        if (!checkPermission(sender)) {
            return;
        }
        if (!arenaManager.hasArena()) {
            MessageUtils.send(sender, msg("game.start.no-arena"), NamedTextColor.RED);
            return;
        }
        if (!betManager.hasAnyBet()) {
            MessageUtils.send(sender, msg("game.start.no-bet"), NamedTextColor.RED);
            return;
        }
        if (gameState == GameState.COUNTDOWN || gameState == GameState.RUNNING) {
            MessageUtils.send(sender, msg("game.start.already-running"), NamedTextColor.RED);
            return;
        }

        beginPreStartCountdown();
        MessageUtils.broadcast(msg("game.start.countdown-triggered"), NamedTextColor.AQUA);
    }

    public boolean placeBet(Player player, String side, double amount) {
        if (gameState != GameState.BETTING) {
            MessageUtils.send(player, msg("game.bet.invalid-state"), NamedTextColor.RED);
            return false;
        }

        boolean firstBet = !betManager.hasAnyBet();
        boolean placed = betManager.placeBet(player, side, amount);
        if (!placed) {
            return false;
        }

        updateBettingBossBar();

        // Auto-start countdown begins only after the very first successful bet.
        if (firstBet && plugin.getConfig().getBoolean(ConfigKeys.AutoStart.ENABLED, true)) {
            scheduleAutoStart();
        }
        return true;
    }

    public void onChickenDeath(Entity deadChicken) {
        if (gameState != GameState.RUNNING || deadChicken == null) {
            return;
        }

        Chicken c1 = arenaManager.getChicken1();
        Chicken c2 = arenaManager.getChicken2();
        if (c1 == null || c2 == null) {
            return;
        }

        String winnerSide = null;
        Chicken winner = null;
        Chicken loser = null;

        if (deadChicken.getUniqueId().equals(c1.getUniqueId())) {
            winnerSide = "blue";
            winner = c2;
            loser = c1;
        } else if (deadChicken.getUniqueId().equals(c2.getUniqueId())) {
            winnerSide = "red";
            winner = c1;
            loser = c2;
        }

        if (winner == null || winnerSide == null) {
            return;
        }

        gameState = GameState.ENDING;
        stopGameLoop();
        hideAllBossBars();
        betManager.closeBetting();

        cleanupWinner(winner);
        if (loser != null && !loser.isDead()) {
            loser.remove();
        }

        String winnerName = msg("game.result.default-winner-name");
        if (winner.customName() != null) {
            winnerName = PlainTextComponentSerializer.plainText().serialize(winner.customName());
        }

        BetManager.PayoutSummary payoutSummary = betManager.processPayout(winnerSide);
        payoutProcessed = true;

        broadcastMatchResult(winnerName, payoutSummary);
        sendEndingFeedback(payoutSummary);

        winner.getWorld().spawnParticle(Particle.FIREWORK, winner.getLocation(), 40, 0.5, 0.5, 0.5, 0.1);
        scheduleAutoRestart();
    }

    public void forceEnd() {
        // Any forced end means the round is cancelled unless payout is already done.
        if (!payoutProcessed && betManager.hasAnyBet()) {
            betManager.refundAll();
        }

        cancelSchedulers();
        stopGameLoop();
        hideAllBossBars();
        betManager.closeBetting();
        arenaManager.clearArena();
        arenaManager.cleanupOrphanFighters();

        gameState = GameState.IDLE;
        payoutProcessed = false;

        MessageUtils.broadcast(msg("game.force-end.closed"), NamedTextColor.GREEN);
    }

    public void restartMatch(Player sender) {
        if (!checkPermission(sender)) {
            return;
        }
        if (!arenaManager.hasArena()) {
            MessageUtils.send(sender, msg("game.restart.no-arena"), NamedTextColor.RED);
            return;
        }

        // Restart cancels current round and always refunds if payout did not happen.
        if (!payoutProcessed && betManager.hasAnyBet()) {
            betManager.refundAll();
        }

        cancelSchedulers();
        stopGameLoop();
        hideAllBossBars();
        arenaManager.spawnFighters();

        payoutProcessed = false;
        gameState = GameState.BETTING;
        betManager.openBetting();

        MessageUtils.broadcast(msg("game.restart.done"), NamedTextColor.GREEN);
        announceBettingOpened();
        updateBettingBossBar();
    }

    public void endFight(Player sender) {
        if (checkPermission(sender)) {
            forceEnd();
        }
    }

    public boolean toggleBossBar(Player player) {
        UUID id = player.getUniqueId();
        if (hiddenBossBarPlayers.contains(id)) {
            hiddenBossBarPlayers.remove(id);
            refreshBossBarForCurrentState();
            return true;
        }

        hiddenBossBarPlayers.add(id);
        hideBossBarForPlayer(player);
        return false;
    }

    public boolean isBossBarEnabledFor(Player player) {
        return !hiddenBossBarPlayers.contains(player.getUniqueId());
    }

    public boolean isFighter(Entity entity) {
        return arenaManager.isFighter(entity);
    }

    public boolean canDamageFighter(Entity damager, Entity victim) {
        return gameState == GameState.RUNNING && arenaManager.areCurrentOpponents(damager, victim);
    }

    private void beginPreStartCountdown() {
        if (gameState != GameState.BETTING) {
            return;
        }

        cancelAutoStartTask();
        cancelAutoRestartTask();

        int prestartSeconds = getConfiguredPrestartSeconds();
        gameState = GameState.COUNTDOWN;

        if (!betManager.lockBettingAndValidateLiquidity()) {
            gameState = GameState.BETTING;
            if (arenaManager.hasArena()) {
                betManager.openBetting();
                announceLiquidityCancelled();
                updateBettingBossBar();
            }
            return;
        }

        MessageUtils.broadcast(msg("game.countdown.locked", Map.of("seconds", String.valueOf(prestartSeconds))), NamedTextColor.GOLD);
        SoundUtil.playConfiguredToPlayers(plugin, "sounds.bet-lock", getPlayersNearArena());

        countdownTask = new BukkitRunnable() {
            int secondsLeft = prestartSeconds;

            @Override
            public void run() {
                if (gameState != GameState.COUNTDOWN) {
                    cancel();
                    return;
                }

                if (secondsLeft <= 0) {
                    cancel();
                    launchFight();
                    return;
                }

                updateCountdownBossBar(secondsLeft, prestartSeconds);
                handleCountdownUx(secondsLeft);
                secondsLeft--;
            }
        };
        countdownTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void launchFight() {
        if (!arenaManager.hasArena()) {
            forceEnd();
            return;
        }
        if (checkChickensDead()) {
            arenaManager.spawnFighters();
        }

        gameState = GameState.RUNNING;

        MessageUtils.broadcast(msg("game.launch.start"), NamedTextColor.GOLD);
        MessageUtils.broadcast(msg("game.launch.red-pool", Map.of("amount", String.valueOf(Math.round(betManager.getTotalRed())))), NamedTextColor.RED);
        MessageUtils.broadcast(msg("game.launch.blue-pool", Map.of("amount", String.valueOf(Math.round(betManager.getTotalBlue())))), NamedTextColor.BLUE);

        updateRunningBossBar();

        setupChicken(arenaManager.getChicken1(), arenaManager.getChicken2());
        setupChicken(arenaManager.getChicken2(), arenaManager.getChicken1());

        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameState != GameState.RUNNING || checkChickensDead()) {
                    cancel();
                    return;
                }

                skillManager.tryCastSkill(arenaManager.getChicken1(), arenaManager.getChicken2());
                if (checkChickensDead()) {
                    cancel();
                    return;
                }
                skillManager.tryCastSkill(arenaManager.getChicken2(), arenaManager.getChicken1());
            }
        };
        gameTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void scheduleAutoStart() {
        cancelAutoStartTask();

        int autoStartSeconds = Math.max(1, plugin.getConfig().getInt(ConfigKeys.AutoStart.DELAY_SECONDS, 60));
        autoStartTask = new BukkitRunnable() {
            @Override
            public void run() {
                autoStartTask = null;
                if (gameState != GameState.BETTING || !betManager.hasAnyBet()) {
                    return;
                }
                beginPreStartCountdown();
                MessageUtils.broadcast(msg("game.auto-start.triggered"), NamedTextColor.AQUA);
            }
        };
        autoStartTask.runTaskLater(plugin, autoStartSeconds * 20L);
        MessageUtils.broadcast(msg("game.auto-start.scheduled", Map.of("seconds", String.valueOf(autoStartSeconds))), NamedTextColor.YELLOW);
    }

    private void scheduleAutoRestart() {
        if (!plugin.getConfig().getBoolean(ConfigKeys.AutoRestart.ENABLED, true)) {
            gameState = GameState.IDLE;
            return;
        }

        cancelAutoRestartTask();

        int delay = Math.max(1, plugin.getConfig().getInt(ConfigKeys.AutoRestart.DELAY_SECONDS, 5));
        autoRestartTask = new BukkitRunnable() {
            @Override
            public void run() {
                autoRestartTask = null;
                if (!arenaManager.hasArena()) {
                    return;
                }
                arenaManager.spawnFighters();
                betManager.openBetting();
                payoutProcessed = false;
                gameState = GameState.BETTING;
                MessageUtils.broadcast(msg("game.auto-restart.done"), NamedTextColor.GREEN);
                announceBettingOpened();
                updateBettingBossBar();
            }
        };
        autoRestartTask.runTaskLater(plugin, delay * 20L);
    }

    private void startIdleUiTask() {
        idleUiTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameState != GameState.IDLE) {
                    idleBroadcastTickerSeconds = 0;
                    return;
                }

                sendIdleActionBar();
                tickIdleBroadcast();
            }
        };
        idleUiTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void sendIdleActionBar() {
        if (!isActionBarEnabled()) {
            return;
        }

        Location center = arenaManager.getArenaCenter();
        if (center == null) {
            return;
        }

        int nextMatchSeconds = Math.max(1, plugin.getConfig().getInt(ConfigKeys.AutoStart.DELAY_SECONDS, 60));
        Map<String, String> placeholders = Map.of(
                "time", formatTime(nextMatchSeconds),
                "jackpot_amount", String.valueOf(betManager.getJackpot())
        );

        Component actionBar = plugin.getConfigManager().getMessageComponent("broadcasts.idle-actionbar", placeholders);
        for (Player player : getPlayersNearArenaForActionBar()) {
            MessageUtils.sendActionBar(player, actionBar);
        }
    }

    private void tickIdleBroadcast() {
        int intervalMinutes = Math.max(1, plugin.getConfig().getInt("ui.idle-broadcast-minutes", 2));
        int intervalSeconds = intervalMinutes * 60;
        idleBroadcastTickerSeconds++;

        if (idleBroadcastTickerSeconds < intervalSeconds) {
            return;
        }

        idleBroadcastTickerSeconds = 0;
        Map<String, String> placeholders = Map.of(
                "jackpot", String.valueOf(betManager.getJackpot()),
                "time", formatTime(Math.max(1, plugin.getConfig().getInt(ConfigKeys.AutoStart.DELAY_SECONDS, 60)))
        );
        MessageUtils.broadcast(plugin.getConfigManager().getMessageComponent("broadcasts.idle-reminder", placeholders));
    }

    private void announceBettingOpened() {
        SoundUtil.playConfiguredToPlayers(plugin, "sounds.match-start", Bukkit.getOnlinePlayers());

        long seedRed = betManager.getLastSeedRed();
        long seedBlue = betManager.getLastSeedBlue();
        long seedTotal = seedRed + seedBlue;

        Map<String, String> placeholders = Map.of(
                "chicken_a", getChickenDisplayName(arenaManager.getChicken1(), plugin.getConfig().getString(ConfigKeys.Arena.CHICKEN1_NAME, "Red")),
                "chicken_b", getChickenDisplayName(arenaManager.getChicken2(), plugin.getConfig().getString(ConfigKeys.Arena.CHICKEN2_NAME, "Blue")),
                "seed", String.valueOf(seedTotal),
                "seed_total", String.valueOf(seedTotal),
                "seed_red", String.valueOf(seedRed),
                "seed_blue", String.valueOf(seedBlue)
        );

        MessageUtils.broadcastLines(plugin.getConfigManager().getMessageList("broadcasts.match-start", placeholders));
    }

    private void announceLiquidityCancelled() {
        SoundUtil.playConfiguredToPlayers(plugin, "sounds.match-cancelled", getPlayersNearArena());
        MessageUtils.broadcast(plugin.getConfigManager().getMessageComponent("broadcasts.match-cancelled"));

        Title cancelTitle = Title.title(
                plugin.getConfigManager().getRawComponent("titles.cancelled.title", Map.of()),
                plugin.getConfigManager().getRawComponent("titles.cancelled.subtitle", Map.of())
        );
        for (Player player : getPlayersNearArena()) {
            player.showTitle(cancelTitle);
        }
    }

    private void handleCountdownUx(int secondsLeft) {
        if (secondsLeft > 10) {
            return;
        }

        float pitch = secondsLeft <= 3 ? 1.6f : 1.0f;
        SoundUtil.playConfiguredToPlayersWithPitch(plugin, "sounds.countdown-tick", getPlayersNearArena(), pitch);

        Map<String, String> placeholders = Map.of("seconds", String.valueOf(secondsLeft));
        Title countdownTitle = Title.title(
                plugin.getConfigManager().getRawComponent("titles.countdown.title", placeholders),
                plugin.getConfigManager().getRawComponent("titles.countdown.subtitle", placeholders)
        );
        for (Player player : getPlayersNearArena()) {
            player.showTitle(countdownTitle);
        }

        if (!isActionBarEnabled()) {
            return;
        }

        Component countdownMessage = plugin.getConfigManager().getMessageComponent("broadcasts.countdown", Map.of(
                "seconds", String.valueOf(secondsLeft),
                "multiplier", DECIMAL.format(betManager.getCurrentUnderdogMultiplier())
        ));
        for (Player player : getPlayersNearArenaForActionBar()) {
            MessageUtils.send(player, countdownMessage);
        }
    }

    private void broadcastMatchResult(String winnerName, BetManager.PayoutSummary payoutSummary) {
        double totalPool = payoutSummary.winnerPool() + payoutSummary.loserPool();
        double multiplier = payoutSummary.winnerPool() <= 0 ? 1.0D : (totalPool / payoutSummary.winnerPool());

        MessageUtils.broadcastLines(plugin.getConfigManager().getMessageList("broadcasts.match-result", Map.of(
                "winner_name", winnerName,
                "multiplier", DECIMAL.format(multiplier),
                "tax_percent", DECIMAL.format(plugin.getConfig().getDouble(ConfigKeys.BettingLogic.TAX_PERCENTAGE, 5.0D))
        )));

        List<Map.Entry<UUID, BetManager.WinnerPayout>> topWinners = payoutSummary.winnerPayouts().entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<UUID, BetManager.WinnerPayout> e) -> e.getValue().profit()).reversed())
                .limit(3)
                .toList();

        for (int i = 0; i < topWinners.size(); i++) {
            Map.Entry<UUID, BetManager.WinnerPayout> entry = topWinners.get(i);
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null || name.isBlank()) {
                name = entry.getKey().toString();
            }

            MessageUtils.broadcast(plugin.getConfigManager().parseMessage(msg("broadcasts.top-winner", Map.of(
                    "rank", String.valueOf(i + 1),
                    "player", name,
                    "profit", String.valueOf(entry.getValue().profit())
            ))));
        }

        if (payoutSummary.capHit() && payoutSummary.jackpotOverflow() > 0) {
            MessageUtils.broadcast(plugin.getConfigManager().getMessageComponent("broadcasts.jackpot-overflow", Map.of(
                    "excess", String.valueOf(payoutSummary.jackpotOverflow())
            )));
        }
    }

    private void sendEndingFeedback(BetManager.PayoutSummary payoutSummary) {
        for (Map.Entry<UUID, BetManager.WinnerPayout> winner : payoutSummary.winnerPayouts().entrySet()) {
            Player player = Bukkit.getPlayer(winner.getKey());
            if (player == null) {
                continue;
            }

            SoundUtil.playConfiguredToPlayer(plugin, "sounds.win", player);
            BetManager.WinnerPayout payout = winner.getValue();
            Map<String, String> placeholders = Map.of(
                    "profit", String.valueOf(payout.profit()),
                    "tax", String.valueOf(payout.tax())
            );
            player.showTitle(Title.title(
                    plugin.getConfigManager().getRawComponent("titles.win.title", placeholders),
                    plugin.getConfigManager().getRawComponent("titles.win.subtitle", placeholders)
            ));
        }

        for (UUID loserId : payoutSummary.loserBets().keySet()) {
            Player player = Bukkit.getPlayer(loserId);
            if (player == null) {
                continue;
            }

            SoundUtil.playConfiguredToPlayer(plugin, "sounds.lose", player);
            player.showTitle(Title.title(
                    plugin.getConfigManager().getRawComponent("titles.lose.title", Map.of()),
                    plugin.getConfigManager().getRawComponent("titles.lose.subtitle", Map.of())
            ));
        }
    }

    private int getConfiguredPrestartSeconds() {
        return Math.max(1, plugin.getConfig().getInt(ConfigKeys.Betting.PRESTART_LOCK_SECONDS, 15));
    }

    private void updateBettingBossBar() {
        if (!plugin.getConfig().getBoolean(ConfigKeys.BossBar.ENABLED, true)) {
            hideAllBossBars();
            return;
        }

        BossBar bar = getOrCreateBossBar(GameState.BETTING, BarColor.PURPLE);
        bar.setColor(BarColor.PURPLE);
        bar.setTitle(bossBarTitle("game.bossbar.betting", Map.of(
                "chicken_a", getChickenDisplayName(arenaManager.getChicken1(), plugin.getConfig().getString(ConfigKeys.Arena.CHICKEN1_NAME, "Red")),
                "chicken_b", getChickenDisplayName(arenaManager.getChicken2(), plugin.getConfig().getString(ConfigKeys.Arena.CHICKEN2_NAME, "Blue")),
                "pool_a", String.valueOf(betManager.getTotalRed()),
                "pool_b", String.valueOf(betManager.getTotalBlue())
        )));
        bar.setProgress(1.0);
        assignBossBarPlayers(bar);
    }

    private void updateCountdownBossBar(int secondsLeft, int totalSeconds) {
        if (!plugin.getConfig().getBoolean(ConfigKeys.BossBar.ENABLED, true)) {
            hideAllBossBars();
            return;
        }

        BossBar bar = getOrCreateBossBar(GameState.COUNTDOWN, BarColor.RED);
        float progress = totalSeconds <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) secondsLeft / totalSeconds));
        bar.setProgress(progress);
        bar.setColor(BarColor.RED);
        bar.setTitle(bossBarTitle("game.bossbar.title", Map.of("seconds", String.valueOf(secondsLeft))));
        assignBossBarPlayers(bar);
    }

    private void updateRunningBossBar() {
        if (!plugin.getConfig().getBoolean(ConfigKeys.BossBar.ENABLED, true)) {
            hideAllBossBars();
            return;
        }

        BossBar bar = getOrCreateBossBar(GameState.RUNNING, BarColor.YELLOW);
        bar.setColor(BarColor.YELLOW);
        bar.setProgress(1.0);
        bar.setTitle(bossBarTitle("game.bossbar.running"));
        assignBossBarPlayers(bar);
    }

    private BossBar getOrCreateBossBar(GameState state, BarColor color) {
        hideAllBossBars();
        BossBar bar = bossBars.computeIfAbsent(state, ignored -> Bukkit.createBossBar("ChickenWar", color, BarStyle.SOLID));
        bar.setVisible(true);
        return bar;
    }

    private void assignBossBarPlayers(BossBar bar) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hiddenBossBarPlayers.contains(player.getUniqueId())) {
                bar.removePlayer(player);
                continue;
            }

            if (!isPlayerInArenaRange(player)) {
                bar.removePlayer(player);
                continue;
            }

            bar.addPlayer(player);
        }
    }

    private void refreshBossBarForCurrentState() {
        switch (gameState) {
            case BETTING -> updateBettingBossBar();
            case COUNTDOWN -> updateCountdownBossBar(getConfiguredPrestartSeconds(), getConfiguredPrestartSeconds());
            case RUNNING -> updateRunningBossBar();
            default -> hideAllBossBars();
        }
    }

    private void hideBossBarForPlayer(Player player) {
        for (BossBar bar : bossBars.values()) {
            bar.removePlayer(player);
        }
    }

    private void hideAllBossBars() {
        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
        }
    }

    private void cancelSchedulers() {
        cancelAutoStartTask();
        cancelAutoRestartTask();
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void cancelAutoStartTask() {
        if (autoStartTask != null) {
            autoStartTask.cancel();
            autoStartTask = null;
        }
    }

    private void cancelAutoRestartTask() {
        if (autoRestartTask != null) {
            autoRestartTask.cancel();
            autoRestartTask = null;
        }
    }

    private boolean checkPermission(Player p) {
        if (p.hasPermission("chickenwar.admin")) {
            return true;
        }
        MessageUtils.send(p, msg("game.permission.denied"), NamedTextColor.RED);
        return false;
    }

    private void stopGameLoop() {
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }
    }

    private boolean checkChickensDead() {
        return arenaManager.getChicken1() == null || arenaManager.getChicken1().isDead()
                || arenaManager.getChicken2() == null || arenaManager.getChicken2().isDead();
    }

    private void cleanupWinner(Chicken winner) {
        if (winner == null || winner.isDead()) {
            return;
        }
        winner.setInvulnerable(true);
        AttributeInstance maxHealth = winner.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            winner.setHealth(maxHealth.getValue());
        }
        winner.clearActivePotionEffects();
        winner.setFireTicks(0);
        winner.setTarget(null);
        Bukkit.getMobGoals().removeAllGoals(winner);
    }

    private void setupChicken(Chicken chicken, LivingEntity target) {
        if (chicken == null || target == null) {
            return;
        }
        chicken.setAI(true);
        chicken.setInvulnerable(false);
        chicken.clearActivePotionEffects();

        double hp = plugin.getConfig().getDouble(ConfigKeys.Combat.CHICKEN_HEALTH, 60.0);
        AttributeInstance attr = chicken.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(hp);
        }
        chicken.setHealth(hp);

        Bukkit.getMobGoals().removeGoal(chicken, VanillaGoal.PANIC);
        Bukkit.getMobGoals().addGoal(chicken, 1, new ChickenWarAttackGoal(chicken, plugin));
        chicken.setTarget(target);
    }

    private String msg(String path) {
        return plugin.getConfigManager().getMessage(path);
    }

    private String msg(String path, Map<String, String> placeholders) {
        return plugin.getConfigManager().getMessage(path, placeholders);
    }

    private String bossBarTitle(String path) {
        return BOSSBAR_SERIALIZER.serialize(plugin.getConfigManager().getMessageComponent(path));
    }

    private String bossBarTitle(String path, Map<String, String> placeholders) {
        return BOSSBAR_SERIALIZER.serialize(plugin.getConfigManager().getMessageComponent(path, placeholders));
    }

    private boolean isActionBarEnabled() {
        return plugin.getConfig().getBoolean(ConfigKeys.ActionBar.ENABLED, true);
    }

    private boolean isPlayerInArenaRange(Player player) {
        return isPlayerInConfiguredRange(player, ConfigKeys.BossBar.RADIUS);
    }

    private boolean isPlayerInActionBarRange(Player player) {
        return isPlayerInConfiguredRange(player, ConfigKeys.ActionBar.RADIUS);
    }

    private boolean isPlayerInConfiguredRange(Player player, String radiusPath) {
        Location center = arenaManager.getArenaCenter();
        if (center == null) {
            return false;
        }

        double radius = plugin.getConfig().getDouble(radiusPath, -1.0D);
        if (radius < 0) {
            return player.getWorld().equals(center.getWorld());
        }

        if (!player.getWorld().equals(center.getWorld())) {
            return false;
        }
        return player.getLocation().distanceSquared(center) <= radius * radius;
    }

    private List<Player> getPlayersNearArena() {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPlayerInArenaRange(player)) {
                players.add(player);
            }
        }
        return players;
    }

    private List<Player> getPlayersNearArenaForActionBar() {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPlayerInActionBarRange(player)) {
                players.add(player);
            }
        }
        return players;
    }

    private String getChickenDisplayName(Chicken chicken, String fallback) {
        if (chicken == null || chicken.customName() == null) {
            return fallback;
        }
        return PlainTextComponentSerializer.plainText().serialize(chicken.customName());
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public BetManager getBetManager() {
        return betManager;
    }

    private static final DecimalFormat DECIMAL = new DecimalFormat("0.00");

    public static class ChickenWarAttackGoal implements Goal<Chicken> {
        private final Chicken chicken;
        private final ChickenWarPlugin plugin;
        private final GoalKey<Chicken> key;
        private long lastAttackTime;

        public ChickenWarAttackGoal(Chicken chicken, ChickenWarPlugin plugin) {
            this.chicken = chicken;
            this.plugin = plugin;
            this.key = GoalKey.of(Chicken.class, new NamespacedKey("chickenwar", "attack"));
        }

        @Override
        public boolean shouldActivate() {
            return chicken.getTarget() != null && !chicken.getTarget().isDead();
        }

        @Override
        public void tick() {
            LivingEntity target = chicken.getTarget();
            if (target == null) {
                return;
            }
            chicken.getPathfinder().moveTo(target, 1.4);
            if (chicken.getLocation().distanceSquared(target.getLocation()) < 2.5) {
                long now = System.currentTimeMillis();
                if (now - lastAttackTime > 800) {
                    chicken.swingMainHand();
                    target.damage(plugin.getConfig().getDouble(ConfigKeys.Combat.BASE_DAMAGE, 5.0), chicken);
                    lastAttackTime = now;
                }
            }
        }

        @Override
        public @NotNull GoalKey<Chicken> getKey() {
            return key;
        }

        @Override
        public @NotNull EnumSet<GoalType> getTypes() {
            return EnumSet.of(GoalType.TARGET, GoalType.MOVE);
        }
    }
}

