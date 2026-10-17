package dev.b410.BossBarMessages;

import java.util.List;
import java.util.Objects;

/** Rotation settings for one region (or "default"). */
public record RegionRotation(
        String regionKey,
        boolean enabled,
        long gapTicks,
        boolean loop,
        List<String> bossbarIds
) {
    public RegionRotation {
        Objects.requireNonNull(regionKey, "regionKey");
        Objects.requireNonNull(bossbarIds, "bossbarIds");
        if (gapTicks < 0) throw new IllegalArgumentException("gapTicks must be >= 0");
        bossbarIds = List.copyOf(bossbarIds);
    }
}
