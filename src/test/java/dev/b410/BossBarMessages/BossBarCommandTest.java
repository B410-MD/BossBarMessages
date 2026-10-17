package dev.b410.BossBarMessages;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BossBarCommandTest {

    private BossBarMessages plugin;
    private BossBarCommand cmd;
    private File dataFolder;
    private final List<String> out = new ArrayList<>();
    private CommandSender sender;
    private final Command command = mock(Command.class);

    @BeforeEach
    void setUp() {
        TestEnv.install();
        dataFolder = TestEnv.tempDir();
        plugin = TestEnv.plugin(TestEnv.bundledConfig(), dataFolder);
        BossBarConfig cfg = BossBarConfig.load(plugin);
        Messages messages = new Messages(plugin, cfg.lang(), cfg.prefix());
        when(plugin.bossBarConfig()).thenReturn(cfg);
        when(plugin.messages()).thenReturn(messages);

        BossBarService service = new BossBarService(plugin);
        cmd = new BossBarCommand(plugin, service, new BossBarScheduler(plugin, service), messages);

        sender = mock(CommandSender.class);
        doAnswer(inv -> out.add(ChatColor.stripColor(inv.getArgument(0)))).when(sender).sendMessage(anyString());
    }

    private List<String> run(String... args) {
        out.clear();
        assertTrue(cmd.onCommand(sender, command, "bossbar", args));
        return out;
    }

    @Test
    void noArgsAndUnknownSubcommandShowUsage() {
        assertEquals("[BossBar] Commands:", run().getFirst());
        assertEquals("[BossBar] Commands:", run("nope").getFirst());
        assertEquals("[BossBar] Commands:", run("help").getFirst());
    }

    @Test
    void playById() {
        assertEquals(List.of("[BossBar] Playing 'welcome' (10s)."), run("play", "WELCOME"));
        assertEquals("§aWelcome to the server!", TestEnv.bars.getLast().title);
    }

    @Test
    void playUnknownId() {
        assertEquals(List.of("[BossBar] Bossbar with id 'nope' not found."), run("play", "nope"));
        assertTrue(TestEnv.bars.isEmpty());
    }

    @Test
    void playCustomWithSpacesAndSound() {
        assertEquals(List.of("[BossBar] Displayed bossbar for 5s (sound: minecraft:ambient.cave)."),
                run("play", "custom", "segmented_10", "PURPLE", "5s", "\"Hello", "&lbig", "world\"", "minecraft:ambient.cave"));
        TestEnv.FakeBar bar = TestEnv.bars.getLast();
        assertEquals("Hello §lbig world", bar.title);
        assertEquals(org.bukkit.boss.BarStyle.SEGMENTED_10, bar.style);
        assertEquals(org.bukkit.boss.BarColor.PURPLE, bar.color);
    }

    @Test
    void playCustomValidation() {
        assertTrue(run("play", "custom", "dotted", "red", "5s", "\"x\"").getFirst().startsWith("[BossBar] Unknown style: 'dotted'"));
        assertTrue(run("play", "custom", "solid", "orange", "5s", "\"x\"").getFirst().startsWith("[BossBar] Unknown color: 'orange'"));
        assertTrue(run("play", "custom", "solid", "red", "0s", "\"x\"").getFirst().startsWith("[BossBar] Time must be greater than zero"));
        assertTrue(run("play", "custom", "solid", "red", "5x", "\"x\"").getFirst().startsWith("[BossBar] Invalid time format"));
        assertEquals("[BossBar] Message must be wrapped in double quotes.", run("play", "custom", "solid", "red", "5s", "x").getFirst());
        assertEquals("[BossBar] Message must be wrapped in double quotes.", run("play", "custom", "solid", "red", "5s", "\"x").getFirst());
        assertEquals("[BossBar] Commands:", run("play", "custom", "solid").getFirst());
        assertTrue(TestEnv.bars.isEmpty());
    }

    @Test
    void list() {
        List<String> lines = run("list");
        assertEquals("[BossBar] Defined bossbars (5):", lines.getFirst());
        assertTrue(lines.contains(" • welcome - green / solid / 10s (sound: minecraft:block.note_block.pling)"), lines.toString());
        assertTrue(lines.contains("Auto-broadcast: ON"));
    }

    @Test
    void clear() {
        run("play", "welcome");
        assertEquals(List.of("[BossBar] Cleared all currently visible bossbars."), run("clear"));
        assertTrue(!TestEnv.bars.getLast().visible);
    }

    @Test
    void reloadCallsPlugin() {
        assertEquals(List.of("[BossBar] Config reloaded. Loaded 5 bossbar(s)."), run("reload"));
        verify(plugin).reloadAll();
    }

    @Test
    void tabCompletion() {
        assertEquals(List.of("play", "list", "reload", "clear", "help"), cmd.onTabComplete(sender, command, "bossbar", new String[]{""}));
        assertEquals(List.of("play"), cmd.onTabComplete(sender, command, "bossbar", new String[]{"P"}));
        assertEquals(List.of("welcome"), cmd.onTabComplete(sender, command, "bossbar", new String[]{"play", "w"}));
        assertEquals(List.of("custom"), cmd.onTabComplete(sender, command, "bossbar", new String[]{"play", "c"}));
        assertTrue(cmd.onTabComplete(sender, command, "bossbar", new String[]{"play", "custom", "seg"}).contains("segmented_20"));
        assertEquals(List.of("red"), cmd.onTabComplete(sender, command, "bossbar", new String[]{"play", "custom", "solid", "r"}));
        assertEquals(List.of(), cmd.onTabComplete(sender, command, "bossbar", new String[]{"list", "x"}));
    }

    @Test
    void languageFilesAreExtractedAndFallbackWorks() throws Exception {
        assertTrue(new File(dataFolder, "langs/en.yml").isFile());
        assertTrue(new File(dataFolder, "langs/pl.yml").isFile());

        Messages pl = new Messages(plugin, "pl", "");
        assertEquals("Nie znaleziono bossbara o id 'x'.", ChatColor.stripColor(pl.msg("bossbar-not-found", "id", "x")));

        Messages missing = new Messages(plugin, "de", "");
        assertEquals("Bossbar with id 'x' not found.", ChatColor.stripColor(missing.msg("bossbar-not-found", "id", "x")));

        Files.writeString(new File(dataFolder, "langs/partial.yml").toPath(), "clear-success: \"&aDone\"\n");
        Messages partial = new Messages(plugin, "partial", "");
        assertEquals("Done", ChatColor.stripColor(partial.msg("clear-success")));
        assertEquals("Commands:", ChatColor.stripColor(partial.msg("usage-header")));
        assertEquals("{missing:nope}", partial.msg("nope"));
    }
}
