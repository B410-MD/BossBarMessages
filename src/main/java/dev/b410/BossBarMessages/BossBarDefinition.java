package dev.b410.BossBarMessages;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.Objects;

/** A single bossbar. {@code id} is null for ad-hoc bars, {@code sound} is optional. */
public record BossBarDefinition(
        String id,
        String message,
        BarColor color,
        BarStyle style,
        long durationTicks,
        String sound
) {
    public BossBarDefinition {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(style, "style");
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be > 0");
        }
    }
}
