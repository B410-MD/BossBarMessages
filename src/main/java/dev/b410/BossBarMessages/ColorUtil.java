package dev.b410.BossBarMessages;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Translates &#RRGGBB hex colors and standard & color codes. Both can be mixed. */
public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9A-Fa-f]{6})");

    private ColorUtil() {}

    public static String translate(String input) {
        if (input == null) return null;

        Matcher m = HEX_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder(input.length() + 16);

        while (m.find()) {
            String hex = m.group(1).toLowerCase();
            String replacement;
            try {
                replacement = net.md_5.bungee.api.ChatColor.of("#" + hex).toString();
            } catch (IllegalArgumentException ex) {
                replacement = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);

        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
