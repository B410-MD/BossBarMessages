package dev.b410.BossBarMessages;

import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shows bossbars: one-shot bars for everyone online, or rotation bars
 * bound to a region and visible only to players in that region. Main thread only.
 */
public final class BossBarService implements Listener {

    private final BossBarMessages plugin;

    /** Currently visible bar per rotation (region key). */
    private final Map<String, BossBar> activeByRotation = new HashMap<>();

    /** One-shot bars from /bossbar play. */
    private final Set<BossBar> activeAdHoc = new LinkedHashSet<>();

    /** Progress animation of every visible bar. */
    private final Map<BossBar, BukkitTask> animations = new HashMap<>();

    public BossBarService(BossBarMessages plugin) {
        this.plugin = plugin;
    }

    public void display(BossBarDefinition def) {
        BossBar bar = createBar(def);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(p);
            playSound(p, def.sound());
        }
        activeAdHoc.add(bar);
        animate(bar, def.durationTicks(), () -> activeAdHoc.remove(bar));
    }

    public void displayForRotation(String regionKey, BossBarDefinition def, Set<Player> audience) {
        BossBar previous = activeByRotation.remove(regionKey);
        if (previous != null) dispose(previous);

        BossBar bar = createBar(def);
        for (Player p : audience) {
            bar.addPlayer(p);
            playSound(p, def.sound());
        }
        activeByRotation.put(regionKey, bar);

        animate(bar, def.durationTicks(), () -> activeByRotation.remove(regionKey, bar));
    }

    public void attachToActiveRotation(Player player, String regionKey) {
        BossBar bar = activeByRotation.get(regionKey);
        if (bar != null) bar.addPlayer(player);
    }

    public void movePlayerBetweenRotations(Player player, String oldRegion, String newRegion) {
        if (oldRegion != null) {
            BossBar oldBar = activeByRotation.get(oldRegion);
            if (oldBar != null) oldBar.removePlayer(player);
        }
        if (newRegion != null) {
            BossBar newBar = activeByRotation.get(newRegion);
            if (newBar != null) newBar.addPlayer(player);
        }
    }

    public void clearAll() {
        for (BossBar bar : new ArrayList<>(activeByRotation.values())) dispose(bar);
        for (BossBar bar : new ArrayList<>(activeAdHoc)) dispose(bar);
        activeByRotation.clear();
        activeAdHoc.clear();
    }

    public void shutdown() {
        clearAll();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Rotation bars are attached by the scheduler; one-shot bars are for everyone
        Player p = event.getPlayer();
        for (BossBar bar : activeAdHoc) bar.addPlayer(p);
    }

    private BossBar createBar(BossBarDefinition def) {
        String title = ColorUtil.translate(def.message());
        BossBar bar = Bukkit.createBossBar(title, def.color(), def.style());
        bar.setProgress(0.0);
        bar.setVisible(true);
        return bar;
    }

    private void dispose(BossBar bar) {
        BukkitTask task = animations.remove(bar);
        if (task != null) task.cancel();
        bar.removeAll();
        bar.setVisible(false);
    }

    private void playSound(Player p, String sound) {
        if (sound == null) return;
        try {
            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception ignored) {
            // invalid sound key, the bar is still shown
        }
    }

    /** Fills the bar from 0 to 1 over {@code totalTicks}, then removes it. */
    private void animate(BossBar bar, long totalTicks, Runnable onFinish) {
        BukkitTask task = new BukkitRunnable() {
            long elapsed = 0L;

            @Override
            public void run() {
                elapsed++;
                if (elapsed >= totalTicks) {
                    bar.setProgress(1.0);
                    dispose(bar);
                    onFinish.run();
                    return;
                }
                bar.setProgress(Math.min(1.0, (double) elapsed / (double) totalTicks));
            }
        }.runTaskTimer(plugin, 1L, 1L);
        animations.put(bar, task);
    }
}
