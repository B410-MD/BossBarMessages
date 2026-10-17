package dev.b410.BossBarMessages;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses durations like 30s, 5m, 1h into ticks (1s = 20 ticks). */
public final class DurationParser {

    private static final Pattern PATTERN =
            Pattern.compile("^(\\d+)([smh])$", Pattern.CASE_INSENSITIVE);

    private DurationParser() {}

    /** @throws IllegalArgumentException on bad format or a value of 0 */
    public static long parseToTicks(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Time must not be empty. Use e.g. 30s, 5m, 1h.");
        }
        Matcher m = PATTERN.matcher(input.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "Invalid time format: '" + input + "'. Use e.g. 30s, 5m, 1h.");
        }
        long value = Long.parseLong(m.group(1));
        if (value <= 0) {
            throw new IllegalArgumentException("Time must be greater than zero.");
        }
        long seconds = switch (m.group(2).toLowerCase()) {
            case "s" -> value;
            case "m" -> value * 60L;
            case "h" -> value * 3600L;
            default  -> throw new IllegalArgumentException("Unknown time unit.");
        };
        long ticks = seconds * 20L;
        if (ticks > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Time is too large.");
        }
        return ticks;
    }

    /** Same as parseToTicks, but allows zero (used for "gap"). */
    public static long parseToTicksAllowZero(String input) {
        if (input == null || input.isBlank()) return 0L;
        String t = input.trim();
        if (t.equals("0") || t.equalsIgnoreCase("0s") || t.equalsIgnoreCase("0m") || t.equalsIgnoreCase("0h")) {
            return 0L;
        }
        return parseToTicks(t);
    }

    /** Formats ticks as 5s, 2m or 1h. */
    public static String formatTicks(long ticks) {
        long seconds = ticks / 20L;
        if (seconds == 0) return "0s";
        if (seconds % 3600 == 0) return (seconds / 3600) + "h";
        if (seconds % 60   == 0) return (seconds / 60)   + "m";
        return seconds + "s";
    }
}
