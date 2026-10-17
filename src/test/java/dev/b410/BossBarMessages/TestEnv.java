package dev.b410.BossBarMessages;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Minimal fake Bukkit server for tests: a tick-driven scheduler, online players
 * and bossbars that remember their players. Installed once per JVM (Bukkit is a singleton).
 */
final class TestEnv {

    static final Logger LOG = Logger.getLogger("BossBarMessagesTest");

    static Server server;
    static FakeScheduler scheduler;
    static final List<Player> online = new ArrayList<>();
    static final List<FakeBar> bars = new ArrayList<>();

    private TestEnv() {}

    static synchronized void install() {
        if (server == null) {
            server = mock(Server.class);
            scheduler = new FakeScheduler();
            lenient().when(server.getLogger()).thenReturn(LOG);
            lenient().when(server.getName()).thenReturn("FakeServer");
            lenient().when(server.getVersion()).thenReturn("test");
            lenient().when(server.getBukkitVersion()).thenReturn("26.3-R0.1-SNAPSHOT");
            lenient().when(server.getScheduler()).thenReturn(scheduler.mock);
            // Registry/Biome static init needs a registry for every type
            lenient().doAnswer(inv -> mock(Registry.class)).when(server).getRegistry(any());
            lenient().when(server.getUnsafe()).thenReturn(mock(UnsafeValues.class));
            lenient().doAnswer(inv -> List.copyOf(online)).when(server).getOnlinePlayers();
            lenient().doAnswer(inv -> {
                FakeBar bar = new FakeBar(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2));
                bars.add(bar);
                return bar.mock;
            }).when(server).createBossBar(anyString(), any(BarColor.class), any(BarStyle.class), any(BarFlag[].class));
            Bukkit.setServer(server);
        }
        reset();
    }

    static void reset() {
        if (scheduler != null) scheduler.clear();
        online.clear();
        bars.clear();
    }

    // ---------------------------------------------------------------------
    //  Fixtures
    // ---------------------------------------------------------------------

    static Biome biome(String key) {
        Biome b = mock(Biome.class);
        lenient().when(((Keyed) b).getKey()).thenReturn(NamespacedKey.minecraft(key));
        return b;
    }

    static World world(String name, World.Environment env, Biome biome) {
        World w = mock(World.class);
        lenient().when(w.getName()).thenReturn(name);
        lenient().when(w.getEnvironment()).thenReturn(env);
        lenient().when(w.getBiome(any(Location.class))).thenReturn(biome);
        return w;
    }

    /** A player whose world (and therefore biome) can be swapped during a test. */
    static Player player(String name, World world) {
        Player p = mock(Player.class);
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        World[] current = {world};
        lenient().when(p.getName()).thenReturn(name);
        lenient().when(p.getUniqueId()).thenReturn(id);
        lenient().doAnswer(inv -> current[0]).when(p).getWorld();
        lenient().doAnswer(inv -> new Location(current[0], 0, 64, 0)).when(p).getLocation();
        lenient().doAnswer(inv -> { current[0] = ((Location) inv.getArgument(0)).getWorld(); return true; })
                .when(p).teleport(any(Location.class));
        return p;
    }

    static void moveTo(Player p, World w) {
        p.teleport(new Location(w, 0, 64, 0));
    }

    static BossBarMessages plugin(String configYaml, File dataFolder) {
        BossBarMessages plugin = mock(BossBarMessages.class);
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(configYaml);
            // Like JavaPlugin.reloadConfig(): the bundled config.yml becomes the defaults
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.loadFromString(bundledConfig());
            cfg.setDefaults(defaults);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        lenient().when(plugin.getConfig()).thenReturn(cfg);
        lenient().when(plugin.getLogger()).thenReturn(LOG);
        lenient().when(plugin.getDataFolder()).thenReturn(dataFolder);
        lenient().when(plugin.isEnabled()).thenReturn(true);
        lenient().doAnswer(inv -> TestEnv.class.getClassLoader().getResourceAsStream(inv.getArgument(0)))
                .when(plugin).getResource(anyString());
        return plugin;
    }

    static String bundledConfig() {
        try (InputStream in = TestEnv.class.getClassLoader().getResourceAsStream("config.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static File tempDir() {
        try {
            return Files.createTempDirectory("bbm-test").toFile();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Bars currently shown to the player, by title. */
    static List<String> visibleTitles(Player p) {
        List<String> out = new ArrayList<>();
        for (FakeBar b : bars) {
            if (b.visible && b.players.contains(p)) out.add(b.title);
        }
        return out;
    }

    // ---------------------------------------------------------------------
    //  Fake bossbar
    // ---------------------------------------------------------------------

    static final class FakeBar {
        final String title;
        final BarColor color;
        final BarStyle style;
        final Set<Player> players = new LinkedHashSet<>();
        boolean visible = true;
        double progress = 1.0;
        final BossBar mock = mock(BossBar.class);

        FakeBar(String title, BarColor color, BarStyle style) {
            this.title = title;
            this.color = color;
            this.style = style;
            lenient().doAnswer(inv -> players.add(inv.getArgument(0))).when(mock).addPlayer(any(Player.class));
            lenient().doAnswer(inv -> players.remove(inv.getArgument(0))).when(mock).removePlayer(any(Player.class));
            lenient().doAnswer(inv -> { players.clear(); return null; }).when(mock).removeAll();
            lenient().doAnswer(inv -> List.copyOf(players)).when(mock).getPlayers();
            lenient().doAnswer(inv -> { visible = inv.getArgument(0); return null; }).when(mock).setVisible(org.mockito.ArgumentMatchers.anyBoolean());
            lenient().doAnswer(inv -> visible).when(mock).isVisible();
            lenient().doAnswer(inv -> {
                double v = inv.getArgument(0);
                if (v < 0.0 || v > 1.0) throw new IllegalArgumentException("Progress must be between 0.0 and 1.0 (" + v + ")");
                progress = v;
                return null;
            }).when(mock).setProgress(org.mockito.ArgumentMatchers.anyDouble());
            lenient().doAnswer(inv -> progress).when(mock).getProgress();
            lenient().when(mock.getTitle()).thenReturn(title);
        }
    }

    // ---------------------------------------------------------------------
    //  Fake scheduler, advanced manually with tick()
    // ---------------------------------------------------------------------

    static final class FakeScheduler {
        final BukkitScheduler mock = mock(BukkitScheduler.class);
        private final Map<Integer, Task> tasks = new HashMap<>();
        private int nextId = 1;
        long now = 0;

        FakeScheduler() {
            lenient().doAnswer(inv -> schedule(inv.getArgument(1), 0, -1))
                    .when(mock).runTask(any(Plugin.class), any(Runnable.class));
            lenient().doAnswer(inv -> schedule(inv.getArgument(1), inv.getArgument(2), -1))
                    .when(mock).runTaskLater(any(Plugin.class), any(Runnable.class), anyLong());
            lenient().doAnswer(inv -> schedule(inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)))
                    .when(mock).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
            lenient().doAnswer(inv -> { Task t = tasks.get((int) inv.getArgument(0)); if (t != null) t.cancelled = true; return null; })
                    .when(mock).cancelTask(anyInt());
        }

        private BukkitTask schedule(Runnable r, long delay, long period) {
            Task t = new Task(nextId++, r, now + Math.max(1, delay), period);
            tasks.put(t.id, t);
            return t.handle;
        }

        void tick(long n) {
            for (long i = 0; i < n; i++) {
                now++;
                for (Task t : new ArrayList<>(tasks.values())) {
                    if (t.cancelled || t.nextRun != now) continue;
                    t.runnable.run();
                    if (t.period > 0 && !t.cancelled) t.nextRun = now + t.period;
                    else t.cancelled = true;
                }
                tasks.values().removeIf(t -> t.cancelled);
            }
        }

        int pending() {
            return (int) tasks.values().stream().filter(t -> !t.cancelled).count();
        }

        void clear() {
            tasks.clear();
            now = 0;
        }

        final class Task {
            final int id;
            final Runnable runnable;
            long nextRun;
            final long period;
            boolean cancelled;
            final BukkitTask handle = mock(BukkitTask.class);

            Task(int id, Runnable runnable, long nextRun, long period) {
                this.id = id;
                this.runnable = runnable;
                this.nextRun = nextRun;
                this.period = period;
                lenient().when(handle.getTaskId()).thenReturn(id);
                lenient().doAnswer(inv -> cancelled).when(handle).isCancelled();
                lenient().doAnswer(inv -> { cancelled = true; return null; }).when(handle).cancel();
            }
        }
    }
}
