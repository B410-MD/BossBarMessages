package dev.b410.BossBarMessages;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Immutable snapshot of config.yml. */
public final class BossBarConfig {

    private final String prefix;
    private final String lang;

    private final boolean autoEnabled;
    private final RegionRotation defaultRotation;
    private final Map<String, RegionRotation> regionRotations;
    private final Map<String, BossBarDefinition> definitionsById;

    private BossBarConfig(String prefix, String lang,
                          boolean autoEnabled,
                          RegionRotation defaultRotation,
                          Map<String, RegionRotation> regionRotations,
                          Map<String, BossBarDefinition> definitionsById) {
        this.prefix = prefix;
        this.lang = lang;
        this.autoEnabled = autoEnabled;
        this.defaultRotation = defaultRotation;
        this.regionRotations = Collections.unmodifiableMap(new LinkedHashMap<>(regionRotations));
        this.definitionsById = Collections.unmodifiableMap(new LinkedHashMap<>(definitionsById));
    }

    public String prefix() { return prefix; }
    public String lang()   { return lang; }
    public boolean isAutoEnabled() { return autoEnabled; }
    public RegionRotation defaultRotation() { return defaultRotation; }
    public Map<String, RegionRotation> regionRotations() { return regionRotations; }

    public List<BossBarDefinition> definitions() {
        return List.copyOf(definitionsById.values());
    }

    public BossBarDefinition findById(String id) {
        if (id == null) return null;
        return definitionsById.get(id.toLowerCase());
    }

    /**
     * Keys of enabled regions. Disabled regions are left out, so players there fall through
     * to the next match. An enabled region with no bossbars is a silent zone.
     */
    public Set<String> knownRegionKeys() {
        Set<String> out = new HashSet<>();
        for (var e : regionRotations.entrySet()) {
            if (e.getValue().enabled()) out.add(e.getKey());
        }
        return out;
    }

    // ---------------------------------------------------------------------

    public static BossBarConfig load(BossBarMessages plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        // Bukkit fills missing keys from the bundled config.yml, which would bring back
        // deleted lists (e.g. a region without 'bossbars'). Read only what the user wrote.
        cfg.setDefaults(new YamlConfiguration());
        Logger log = plugin.getLogger();

        String prefix = cfg.getString("prefix", "&6[BossBar] &r");
        String lang   = cfg.getString("lang", "en");

        Map<String, BossBarDefinition> defs = new LinkedHashMap<>();
        List<Map<?, ?>> raw = cfg.getMapList("bossbars");
        int index = 0;
        for (Map<?, ?> entry : raw) {
            index++;
            try {
                BossBarDefinition def = parseEntry(entry);
                String key = def.id().toLowerCase();
                if (defs.containsKey(key)) {
                    log.warning("bossbars[#" + index + "]: duplicate id '" + def.id() + "', skipping.");
                    continue;
                }
                defs.put(key, def);
            } catch (Exception ex) {
                log.warning("Skipping bossbars[#" + index + "]: " + ex.getMessage());
            }
        }

        boolean autoEnabled = cfg.getBoolean("auto-broadcast.enabled", true);

        ConfigurationSection ab = cfg.getConfigurationSection("auto-broadcast");
        RegionRotation defRotation = parseRotation("default",
                ab != null ? ab.getConfigurationSection("default") : null,
                defs.keySet(), log);

        Map<String, RegionRotation> regions = new LinkedHashMap<>();
        ConfigurationSection regionsSection = ab != null ? ab.getConfigurationSection("regions") : null;
        if (regionsSection != null) {
            for (String key : regionsSection.getKeys(false)) {
                String norm = key.toLowerCase();
                if (norm.equals("default")) {
                    log.warning("auto-broadcast.regions: 'default' is a reserved key, skipping.");
                    continue;
                }
                RegionRotation r = parseRotation(norm, regionsSection.getConfigurationSection(key),
                        defs.keySet(), log);
                regions.put(norm, r);
            }
        }

        return new BossBarConfig(prefix, lang, autoEnabled, defRotation, regions, defs);
    }

    // ---------------------------------------------------------------------

    private static BossBarDefinition parseEntry(Map<?, ?> entry) {
        String id       = requireString(entry, "id");
        String message  = requireString(entry, "message");
        String colorStr = requireString(entry, "color");
        String styleStr = requireString(entry, "style");
        String timeStr  = requireString(entry, "duration");
        Object soundObj = entry.get("sound");
        String sound    = (soundObj == null) ? null : soundObj.toString().trim();
        if (sound != null && sound.isEmpty()) sound = null;

        BarColor color;
        try { color = BarColor.valueOf(colorStr.toUpperCase()); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("unknown color '" + colorStr + "'"); }

        BarStyle style;
        try { style = BarStyle.valueOf(styleStr.toUpperCase()); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("unknown style '" + styleStr + "'"); }

        long durationTicks = DurationParser.parseToTicks(timeStr);
        return new BossBarDefinition(id, message, color, style, durationTicks, sound);
    }

    private static RegionRotation parseRotation(String regionKey,
                                                ConfigurationSection section,
                                                Set<String> knownBossbarIds,
                                                Logger log) {
        if (section == null) {
            return new RegionRotation(regionKey, false, 0L, true, List.of());
        }

        boolean enabled = section.getBoolean("enabled", true);
        boolean loop    = section.getBoolean("loop", true);

        long gapTicks;
        try {
            gapTicks = DurationParser.parseToTicksAllowZero(section.getString("gap", "0s"));
        } catch (IllegalArgumentException ex) {
            log.warning("Region '" + regionKey + "': invalid 'gap' (" + ex.getMessage() + "), using 0s.");
            gapTicks = 0L;
        }

        List<String> rawIds = section.getStringList("bossbars");
        List<String> validIds = new ArrayList<>(rawIds.size());
        Set<String> seen = new HashSet<>();
        for (String id : rawIds) {
            if (id == null) continue;
            String norm = id.toLowerCase();
            if (!knownBossbarIds.contains(norm)) {
                log.warning("Region '" + regionKey + "': bossbar '" + id + "' does not exist, skipping.");
                continue;
            }
            if (seen.add(norm)) validIds.add(norm);
        }

        if (enabled && validIds.isEmpty() && !regionKey.equals(RegionResolver.DEFAULT_KEY)) {
            log.info("Region '" + regionKey + "' has no bossbars, players there will see none.");
        }

        return new RegionRotation(regionKey, enabled, gapTicks, loop, validIds);
    }

    private static String requireString(Map<?, ?> entry, String key) {
        Object v = entry.get(key);
        if (v == null) throw new IllegalArgumentException("missing required field '" + key + "'");
        String s = v.toString().trim();
        if (s.isEmpty()) throw new IllegalArgumentException("field '" + key + "' must not be empty");
        return s;
    }

    /** Active rotations and their sizes, for the startup log. */
    public Map<String, Integer> countsByRegion() {
        Map<String, Integer> out = new HashMap<>();
        if (defaultRotation.enabled()) out.put("default", defaultRotation.bossbarIds().size());
        for (var e : regionRotations.entrySet()) {
            if (e.getValue().enabled()) out.put(e.getKey(), e.getValue().bossbarIds().size());
        }
        return out;
    }
}
