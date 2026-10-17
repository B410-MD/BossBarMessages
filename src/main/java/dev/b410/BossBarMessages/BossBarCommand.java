package dev.b410.BossBarMessages;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Handles /bossbar: play, list, reload, clear, help. */
public final class BossBarCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("play", "list", "reload", "clear", "help");

    private final BossBarMessages plugin;
    private final BossBarService service;
    private final BossBarScheduler scheduler;
    private final Messages msg;

    public BossBarCommand(BossBarMessages plugin, BossBarService service,
                          BossBarScheduler scheduler, Messages msg) {
        this.plugin = plugin;
        this.service = service;
        this.scheduler = scheduler;
        this.msg = msg;
    }

    // ---------------------------------------------------------------------

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String first = args[0].toLowerCase();
        if (!SUBCOMMANDS.contains(first)) {
            sendUsage(sender);
            return true;
        }

        return switch (first) {
            case "play"   -> handlePlay(sender, args);
            case "list"   -> handleList(sender);
            case "reload" -> handleReload(sender);
            case "clear"  -> handleClear(sender);
            case "help"   -> { sendUsage(sender); yield true; }
            default       -> { sendUsage(sender); yield true; }
        };
    }

    // ---------------------------------------------------------------------
    //  /bossbar play
    // ---------------------------------------------------------------------

    private boolean handlePlay(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String second = args[1].toLowerCase();
        if (second.equals("custom")) {
            return handlePlayCustom(sender, args);
        }

        BossBarDefinition def = plugin.bossBarConfig().findById(second);
        if (def == null) {
            sender.sendMessage(msg.msg("bossbar-not-found", "id", second));
            return true;
        }
        service.display(def);
        sender.sendMessage(msg.msg("play-displayed",
                "id", def.id(),
                "duration", DurationParser.formatTicks(def.durationTicks())));
        return true;
    }

    private boolean handlePlayCustom(CommandSender sender, String[] args) {
        // args: play custom <style> <color> <time> "<msg>" [sound]
        if (args.length < 6) {
            sendUsage(sender);
            return true;
        }

        BarStyle style = parseEnum(BarStyle.class, args[2]);
        if (style == null) {
            sender.sendMessage(msg.msg("unknown-style",
                    "value", args[2],
                    "options", listEnum(BarStyle.values())));
            return true;
        }

        BarColor color = parseEnum(BarColor.class, args[3]);
        if (color == null) {
            sender.sendMessage(msg.msg("unknown-color",
                    "value", args[3],
                    "options", listEnum(BarColor.values())));
            return true;
        }

        final long totalTicks;
        try {
            totalTicks = DurationParser.parseToTicks(args[4]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(msg.msg("invalid-time", "reason", ex.getMessage()));
            return true;
        }

        String tail = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
        ParsedTail parsed = parseMessageAndSound(tail);
        if (parsed == null) {
            sender.sendMessage(msg.msg("message-needs-quotes"));
            sender.sendMessage(msg.raw("message-needs-quotes-example"));
            return true;
        }

        BossBarDefinition def = new BossBarDefinition(
                null, parsed.message(), color, style, totalTicks, parsed.sound()
        );
        service.display(def);

        String soundInfo = parsed.sound() == null
                ? ""
                : msg.raw("sound-info", "sound", parsed.sound());
        sender.sendMessage(msg.msg("ad-hoc-displayed",
                "duration", DurationParser.formatTicks(totalTicks),
                "soundInfo", soundInfo));
        return true;
    }

    // ---------------------------------------------------------------------
    //  /bossbar list
    // ---------------------------------------------------------------------

    private boolean handleList(CommandSender sender) {
        List<BossBarDefinition> defs = plugin.bossBarConfig().definitions();
        if (defs.isEmpty()) {
            sender.sendMessage(msg.msg("list-empty"));
            return true;
        }
        sender.sendMessage(msg.msg("list-header", "count", String.valueOf(defs.size())));
        for (BossBarDefinition d : defs) {
            String soundInfo = d.sound() == null
                    ? ""
                    : msg.raw("sound-info", "sound", d.sound());
            sender.sendMessage(msg.raw("list-entry",
                    "id", d.id(),
                    "color", d.color().name().toLowerCase(),
                    "style", d.style().name().toLowerCase(),
                    "duration", DurationParser.formatTicks(d.durationTicks()),
                    "soundInfo", soundInfo));
        }
        boolean autoOn = plugin.bossBarConfig().isAutoEnabled();
        sender.sendMessage(msg.raw(autoOn ? "list-footer-auto-on" : "list-footer-auto-off"));
        sender.sendMessage(msg.raw("list-footer-regions",
                "count", String.valueOf(scheduler.runningCount())));
        return true;
    }

    // ---------------------------------------------------------------------
    //  /bossbar reload
    // ---------------------------------------------------------------------

    private boolean handleReload(CommandSender sender) {
        try {
            plugin.reloadAll();
            sender.sendMessage(plugin.messages().msg("reload-success",
                    "count", String.valueOf(plugin.bossBarConfig().definitions().size())));
        } catch (Exception ex) {
            sender.sendMessage(msg.msg("reload-error", "reason", String.valueOf(ex.getMessage())));
            plugin.getLogger().warning("Reload failed: " + ex);
        }
        return true;
    }

    // ---------------------------------------------------------------------
    //  /bossbar clear
    // ---------------------------------------------------------------------

    private boolean handleClear(CommandSender sender) {
        // Rotations keep running, only the currently visible bars are removed
        service.clearAll();
        sender.sendMessage(msg.msg("clear-success"));
        return true;
    }

    // ---------------------------------------------------------------------
    //  Tab completion
    // ---------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS.stream(), args[0]);
        }
        String first = args[0].toLowerCase();

        if (first.equals("play")) {
            if (args.length == 2) {
                Stream<String> ids = Stream.concat(
                        Stream.of("custom"),
                        plugin.bossBarConfig().definitions().stream().map(BossBarDefinition::id)
                );
                return filter(ids, args[1]);
            }
            if (args[1].equalsIgnoreCase("custom")) {
                return switch (args.length) {
                    case 3 -> filter(Stream.of(BarStyle.values()).map(s -> s.name().toLowerCase()), args[2]);
                    case 4 -> filter(Stream.of(BarColor.values()).map(c -> c.name().toLowerCase()), args[3]);
                    case 5 -> filter(Stream.of("5s", "10s", "30s", "1m", "5m", "10m", "1h"), args[4]);
                    default -> List.of();
                };
            }
        }
        return List.of();
    }

    private List<String> filter(Stream<String> stream, String prefix) {
        String lower = prefix.toLowerCase();
        return stream.filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // ---------------------------------------------------------------------
    //  Parsing
    // ---------------------------------------------------------------------

    private <E extends Enum<E>> E parseEnum(Class<E> type, String input) {
        try { return Enum.valueOf(type, input.toUpperCase()); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private ParsedTail parseMessageAndSound(String rest) {
        rest = rest.trim();
        if (rest.isEmpty() || rest.charAt(0) != '"') return null;
        int end = rest.indexOf('"', 1);
        if (end == -1) return null;
        String message = rest.substring(1, end);
        String after = rest.substring(end + 1).trim();
        String sound = after.isEmpty() ? null : after.split("\\s+", 2)[0];
        return new ParsedTail(message, sound);
    }

    // ---------------------------------------------------------------------
    //  Help
    // ---------------------------------------------------------------------

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(msg.msg("usage-header"));
        sender.sendMessage(msg.raw("usage-play-custom"));
        sender.sendMessage(msg.raw("usage-play-id"));
        sender.sendMessage(msg.raw("usage-list"));
        sender.sendMessage(msg.raw("usage-reload"));
        sender.sendMessage(msg.raw("usage-clear"));
        sender.sendMessage(msg.raw("usage-styles", "options", listEnum(BarStyle.values())));
        sender.sendMessage(msg.raw("usage-colors", "options", listEnum(BarColor.values())));
    }

    private String listEnum(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(e -> e.name().toLowerCase())
                .collect(Collectors.joining(", "));
    }

    private record ParsedTail(String message, String sound) {}
}