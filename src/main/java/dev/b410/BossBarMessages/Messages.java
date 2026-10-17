package dev.b410.BossBarMessages;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Loads langs/<lang>.yml from the data folder. Missing keys fall back to the bundled en.yml.
 * Placeholders: msg("key", "name", "value") replaces {name}.
 */
public final class Messages {

    private final BossBarMessages plugin;
    private final String prefix;
    private final YamlConfiguration langFile;
    private final YamlConfiguration fallback;

    public Messages(BossBarMessages plugin, String langCode, String rawPrefix) {
        this.plugin = plugin;
        this.prefix = ColorUtil.translate(rawPrefix == null ? "" : rawPrefix);

        // Extract bundled language files on first run
        ensureBundledLang("en.yml");
        ensureBundledLang("pl.yml");

        this.fallback = loadFromJar("langs/en.yml");
        this.langFile = loadLangFromDisk(langCode);
    }

    /** Colored message with prefix. */
    public String msg(String key, String... placeholders) {
        return prefix + raw(key, placeholders);
    }

    /** Colored message without prefix, e.g. for multi-line help. */
    public String raw(String key, String... placeholders) {
        String template = langFile.getString(key);
        if (template == null && fallback != null) template = fallback.getString(key);
        if (template == null) template = "{missing:" + key + "}";

        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            template = template.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return ColorUtil.translate(template);
    }

    // ---------------------------------------------------------------------

    private YamlConfiguration loadLangFromDisk(String langCode) {
        File langsDir = new File(plugin.getDataFolder(), "langs");
        File file = new File(langsDir, langCode + ".yml");

        if (!file.exists()) {
            plugin.getLogger().warning("Language file '" + langCode + ".yml' not found, falling back to en.yml.");
            File en = new File(langsDir, "en.yml");
            if (en.exists()) {
                return YamlConfiguration.loadConfiguration(en);
            }
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void ensureBundledLang(String fileName) {
        File langsDir = new File(plugin.getDataFolder(), "langs");
        if (!langsDir.exists() && !langsDir.mkdirs()) {
            plugin.getLogger().warning("Could not create the langs/ folder.");
            return;
        }
        File target = new File(langsDir, fileName);
        if (target.exists()) return;

        try (InputStream in = plugin.getResource("langs/" + fileName)) {
            if (in == null) {
                plugin.getLogger().warning("Bundled resource langs/" + fileName + " is missing");
                return;
            }
            Files.copy(in, target.toPath());
            plugin.getLogger().info("Created langs/" + fileName);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not copy langs/" + fileName + ": " + ex.getMessage());
        }
    }

    private YamlConfiguration loadFromJar(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) return null;
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException ex) {
            return null;
        }
    }
}