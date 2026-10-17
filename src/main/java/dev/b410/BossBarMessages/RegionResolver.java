package dev.b410.BossBarMessages;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * Picks the rotation region for a player. Match order: biome, world name,
 * environment (overworld / nether / the_end), then "default".
 */
public final class RegionResolver {

    public static final String DEFAULT_KEY = "default";

    private final Set<String> knownRegionKeys;

    /** @param knownRegionKeys lowercase keys of active regions, without "default" */
    public RegionResolver(Set<String> knownRegionKeys) {
        this.knownRegionKeys = knownRegionKeys;
    }

    public String resolve(Player player) {
        World world = player.getWorld();
        return resolve(world, world.getBiome(player.getLocation()));
    }

    public String resolve(World world, Biome biome) {
        String biomeKey = biomeKey(biome);
        if (biomeKey != null && knownRegionKeys.contains(biomeKey)) return biomeKey;

        String worldName = world.getName().toLowerCase();
        if (knownRegionKeys.contains(worldName)) return worldName;

        String envKey = switch (world.getEnvironment()) {
            case NORMAL  -> "overworld";
            case NETHER  -> "nether";
            case THE_END -> "the_end";
            default      -> null;
        };
        if (envKey != null && knownRegionKeys.contains(envKey)) return envKey;

        return DEFAULT_KEY;
    }

    // Keyed#getKey works on Spigot and Paper (Registry#getKey(T) is Paper-only).
    // The cast keeps it working where Biome is still an enum.
    @SuppressWarnings("deprecation")
    private static String biomeKey(Biome biome) {
        if (biome == null) return null;
        try {
            NamespacedKey key = ((Keyed) biome).getKey();
            return key.getKey().toLowerCase();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
