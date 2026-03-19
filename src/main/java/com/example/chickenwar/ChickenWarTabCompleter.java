package com.example.chickenwar;

import com.example.chickenwar.config.ConfigKeys;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ChickenWarTabCompleter implements TabCompleter {

    private static final List<String> PLAYER_COMMANDS = List.of("help", "bet", "bossbar", "claim", "jackpot");
    private static final List<String> ADMIN_COMMANDS = List.of("build", "start", "restart", "end", "setwarp", "reload");
    private static final List<String> BET_SIDES = List.of("red", "blue");
    private static final List<String> JACKPOT_ACTIONS = List.of("add", "remove", "set");

    private final ChickenWarPlugin plugin;

    public ChickenWarTabCompleter(ChickenWarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            return completeFirstArgument(sender, args[0]);
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if ("bet".equals(subCommand)) {
            if (args.length == 2) {
                return filterByPrefix(BET_SIDES, args[1]);
            }
            if (args.length == 3) {
                return completeBetAmounts(args[2]);
            }
        }

        if ("jackpot".equals(subCommand) && sender.hasPermission("chickenwar.admin")) {
            if (args.length == 2) {
                return filterByPrefix(JACKPOT_ACTIONS, args[1]);
            }
            if (args.length == 3) {
                return completeBetAmounts(args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> completeFirstArgument(CommandSender sender, String currentInput) {
        List<String> commands = new ArrayList<>(PLAYER_COMMANDS);
        if (sender.hasPermission("chickenwar.admin")) {
            commands.addAll(ADMIN_COMMANDS);
        }
        return filterByPrefix(commands, currentInput);
    }

    private List<String> completeBetAmounts(String currentInput) {
        long minBet = Math.max(1L, plugin.getConfig().getLong(ConfigKeys.BettingLogic.MIN_BET, 1000L));
        List<String> amountHints = Arrays.asList(
                String.valueOf(minBet),
                String.valueOf(minBet * 5L),
                String.valueOf(minBet * 10L)
        );
        return filterByPrefix(amountHints, currentInput);
    }

    private List<String> filterByPrefix(List<String> candidates, String rawInput) {
        String input = rawInput.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(input)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
