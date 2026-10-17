package dev.b410.BossBarMessages;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs one independent rotation per region (plus "default"). Each player sees
 * only the rotation of the region they are currently in.
 */
public final class BossBarScheduler implements Listener {

    private final BossBarMessages plugin;
    private final BossBarService service;

    private BossBarConfig config;
    private RegionResolver resolver;

    private final Map<String, RotationState> states = new HashMap<>();
    private final Map<UUID, String> playerRegion = new HashMap<>();

    public BossBarScheduler(BossBarMessages plugin, BossBarService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public int runningCount() {
        int n = 0;
        for (RotationState s : states.values()) if (s.running) n++;
        return n;
    }

    public void start(BossBarConfig config) {
        stop();
        if (!config.isAutoEnabled()) return;
        this.config = config;
        this.resolver = new RegionResolver(config.knownRegionKeys());

        registerStateIfEnabled(config.defaultRotation());
        for (RegionRotation r : config.regionRotations().values()) {
            registerStateIfEnabled(r);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            playerRegion.put(p.getUniqueId(), resolver.resolve(p));
        }

        for (RotationState s : states.values()) {
            scheduleNext(s, 0L);
        }
    }

    public void stop() {
        for (RotationState s : states.values()) {
            if (s.pendingTask != null) s.pendingTask.cancel();
            s.running = false;
        }
        states.clear();
        playerRegion.clear();
        resolver = null;
    }

    private void registerStateIfEnabled(RegionRotation rot) {
        if (!rot.enabled() || rot.bossbarIds().isEmpty()) return;
        states.put(rot.regionKey(), new RotationState(rot));
    }

    private void scheduleNext(RotationState state, long delayTicks) {
        state.running = true;
        state.pendingTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> showNext(state), Math.max(1L, delayTicks));
    }

    private void showNext(RotationState state) {
        if (!state.running) return;

        state.currentIndex++;
        if (state.currentIndex >= state.rotation.bossbarIds().size()) {
            if (!state.rotation.loop()) {
                state.running = false;
                return;
            }
            state.currentIndex = 0;
        }

        String id = state.rotation.bossbarIds().get(state.currentIndex);
        BossBarDefinition def = config.findById(id);
        if (def == null) {
            scheduleNext(state, 20L);
            return;
        }

        Set<Player> audience = playersInRegion(state.rotation.regionKey());
        service.displayForRotation(state.rotation.regionKey(), def, audience);

        scheduleNext(state, def.durationTicks() + state.rotation.gapTicks());
    }

    private Set<Player> playersInRegion(String regionKey) {
        Set<Player> out = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String r = playerRegion.computeIfAbsent(p.getUniqueId(), id -> resolver.resolve(p));
            if (r.equals(regionKey)) out.add(p);
        }
        return out;
    }

    // ---------------------------------------------------------------------
    //  Keep playerRegion in sync
    // ---------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (resolver == null) return;
        Player p = event.getPlayer();
        String region = resolver.resolve(p);
        playerRegion.put(p.getUniqueId(), region);
        service.attachToActiveRotation(p, region);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerRegion.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        recomputeRegion(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // The player is not at the destination yet, so check on the next tick
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> recomputeRegion(p));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> recomputeRegion(p));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (resolver == null) return;
        var from = event.getFrom();
        var to = event.getTo();
        if (to == null) return;
        // Ignore pure camera rotation
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        recomputeRegion(event.getPlayer());
    }

    private void recomputeRegion(Player p) {
        if (resolver == null) return;
        String newRegion = resolver.resolve(p);
        String old = playerRegion.put(p.getUniqueId(), newRegion);
        if (old == null || !old.equals(newRegion)) {
            service.movePlayerBetweenRotations(p, old, newRegion);
        }
    }

    private static final class RotationState {
        final RegionRotation rotation;
        int currentIndex = -1;
        boolean running = false;
        BukkitTask pendingTask;

        RotationState(RegionRotation rotation) {
            this.rotation = rotation;
        }
    }
}
