package dev.b410.BossBarMessages;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.b410.BossBarMessages.TestEnv.bars;
import static dev.b410.BossBarMessages.TestEnv.online;
import static dev.b410.BossBarMessages.TestEnv.scheduler;
import static dev.b410.BossBarMessages.TestEnv.visibleTitles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scheduler + service against the fake server, driven tick by tick. */
class RotationTest {

    @BeforeAll
    static void env() {
        TestEnv.install();
    }

    private static final String A = "§aA";
    private static final String B = "§bB";

    private static final String CONFIG = """
            auto-broadcast:
              enabled: true
              default:
                gap: 1s
                bossbars: [a, b]
              regions:
                plains:
                  enabled: true
                  bossbars: []
                desert:
                  enabled: false
                  bossbars: [b]
                nether:
                  gap: 0s
                  loop: false
                  bossbars: [b]
            bossbars:
              - {id: a, message: "&aA", color: green, style: solid, duration: 1s}
              - {id: b, message: "&bB", color: blue, style: segmented_6, duration: 2s}
            """;

    private final World plainsWorld = TestEnv.world("world", World.Environment.NORMAL, TestEnv.biome("plains"));
    private final World desertWorld = TestEnv.world("desert_world", World.Environment.NORMAL, TestEnv.biome("desert"));
    private final World forestWorld = TestEnv.world("forest_world", World.Environment.NORMAL, TestEnv.biome("forest"));
    private final World netherWorld = TestEnv.world("world_nether", World.Environment.NETHER, TestEnv.biome("nether_wastes"));

    private BossBarMessages plugin;
    private BossBarService service;
    private BossBarScheduler rotation;

    @BeforeEach
    void setUp() {
        TestEnv.install();
        plugin = TestEnv.plugin(CONFIG, TestEnv.tempDir());
        service = new BossBarService(plugin);
        rotation = new BossBarScheduler(plugin, service);
    }

    private void start() {
        rotation.start(BossBarConfig.load(plugin));
    }

    private Player join(String name, World world) {
        Player p = TestEnv.player(name, world);
        online.add(p);
        rotation.onJoin(new PlayerJoinEvent(p, (String) null));
        service.onJoin(new PlayerJoinEvent(p, (String) null));
        return p;
    }

    private void teleport(Player p, World to) {
        Location from = p.getLocation();
        TestEnv.moveTo(p, to);
        rotation.onTeleport(new PlayerTeleportEvent(p, from, p.getLocation()));
        scheduler.tick(1);
    }

    @Test
    void defaultRotationCyclesWithGapAndLoops() {
        Player p = TestEnv.player("steve", forestWorld);
        online.add(p);
        start();

        scheduler.tick(1);                       // t=1   a shows (20 ticks)
        assertEquals(List.of(A), visibleTitles(p));
        scheduler.tick(19);                      // t=20  still on screen
        assertEquals(List.of(A), visibleTitles(p));
        scheduler.tick(1);                       // t=21  a finished
        assertEquals(List.of(), visibleTitles(p));
        scheduler.tick(20);                      // t=41  after 1s gap, b shows
        assertEquals(List.of(B), visibleTitles(p));
        scheduler.tick(40);                      // t=81  b finished
        assertEquals(List.of(), visibleTitles(p));
        scheduler.tick(20);                      // t=101 loops back to a
        assertEquals(List.of(A), visibleTitles(p));
    }

    @Test
    void barFillsUpWithinValidProgressRange() {
        online.add(TestEnv.player("steve", forestWorld));
        start();
        scheduler.tick(11);
        TestEnv.FakeBar bar = bars.getFirst();
        assertTrue(bar.progress > 0.4 && bar.progress < 0.6, "progress " + bar.progress);
        scheduler.tick(10);
        assertEquals(1.0, bar.progress);
        assertTrue(!bar.visible && bar.players.isEmpty());
    }

    @Test
    void enabledRegionWithoutBossbarsShowsNothing() {
        Player inPlains = TestEnv.player("alex", plainsWorld);
        Player inForest = TestEnv.player("steve", forestWorld);
        online.add(inPlains);
        online.add(inForest);
        start();

        for (int i = 0; i < 200; i++) {
            scheduler.tick(1);
            assertEquals(List.of(), visibleTitles(inPlains), "tick " + scheduler.now);
        }
        assertTrue(bars.stream().anyMatch(b -> b.title.equals(A)), "forest player still got default");
    }

    @Test
    void disabledRegionFallsThroughToDefault() {
        Player p = TestEnv.player("alex", desertWorld);
        online.add(p);
        start();
        scheduler.tick(1);
        assertEquals(List.of(A), visibleTitles(p));
    }

    @Test
    void environmentRegionWithLoopFalseStopsAfterList() {
        Player p = TestEnv.player("alex", netherWorld);
        online.add(p);
        start();

        scheduler.tick(1);
        assertEquals(List.of(B), visibleTitles(p));
        scheduler.tick(40);
        assertEquals(List.of(), visibleTitles(p));
        scheduler.tick(400);
        assertEquals(List.of(), visibleTitles(p));
        long nether = bars.stream().filter(b -> b.title.equals(B) && b.players.isEmpty()).count();
        assertTrue(nether >= 1);
    }

    @Test
    void playerJoiningMidBarSeesItAndQuitIsHandled() {
        start();
        scheduler.tick(5);
        Player p = join("late", forestWorld);
        assertEquals(List.of(A), visibleTitles(p));
        online.remove(p);
        rotation.onQuit(new PlayerQuitEvent(p, (String) null));
        scheduler.tick(200);
    }

    @Test
    void movingIntoSilentZoneHidesBarAndBackShowsIt() {
        Player p = TestEnv.player("steve", forestWorld);
        online.add(p);
        start();
        scheduler.tick(1);
        assertEquals(List.of(A), visibleTitles(p));

        teleport(p, plainsWorld);
        assertEquals(List.of(), visibleTitles(p));

        teleport(p, forestWorld);
        assertEquals(List.of(A), visibleTitles(p));
    }

    @Test
    void movementWithinSameBlockIsIgnoredAndCrossingBlocksRecomputes() {
        Player p = TestEnv.player("steve", forestWorld);
        online.add(p);
        start();
        scheduler.tick(1);

        Location here = p.getLocation();
        rotation.onMove(new PlayerMoveEvent(p, here, here.clone().add(0.2, 0, 0.2)));
        assertEquals(List.of(A), visibleTitles(p));

        TestEnv.moveTo(p, plainsWorld);
        rotation.onMove(new PlayerMoveEvent(p, here, p.getLocation().add(1, 0, 0)));
        assertEquals(List.of(), visibleTitles(p));
    }

    @Test
    void stopCancelsEverything() {
        Player p = TestEnv.player("steve", forestWorld);
        online.add(p);
        start();
        scheduler.tick(1);

        rotation.stop();
        service.clearAll();
        assertEquals(List.of(), visibleTitles(p));
        scheduler.tick(1);
        assertEquals(0, scheduler.pending(), "animations and rotations must be cancelled");
        scheduler.tick(500);
        assertEquals(List.of(), visibleTitles(p));
    }

    @Test
    void restartAfterStopWorks() {
        Player p = TestEnv.player("steve", forestWorld);
        online.add(p);
        start();
        scheduler.tick(1);
        rotation.stop();
        service.clearAll();

        start();
        scheduler.tick(1);
        assertEquals(List.of(A), visibleTitles(p));
        assertEquals(2, rotation.runningCount()); // default + nether
    }

    @Test
    void autoBroadcastOffStartsNothing() {
        BossBarMessages off = TestEnv.plugin(CONFIG.replace("enabled: true\n  default", "enabled: false\n  default"), TestEnv.tempDir());
        online.add(TestEnv.player("steve", forestWorld));
        BossBarScheduler s = new BossBarScheduler(off, service);
        s.start(BossBarConfig.load(off));
        scheduler.tick(100);
        assertEquals(0, s.runningCount());
        assertTrue(bars.isEmpty());
    }

    @Test
    void oneShotBarIsShownToEveryoneIncludingLateJoiners() {
        Player inPlains = TestEnv.player("alex", plainsWorld);
        online.add(inPlains);
        BossBarDefinition def = new BossBarDefinition(null, "&cHi", org.bukkit.boss.BarColor.RED,
                org.bukkit.boss.BarStyle.SOLID, 40, "minecraft:block.note_block.pling");

        service.display(def);
        assertEquals(List.of("§cHi"), visibleTitles(inPlains));

        scheduler.tick(10);
        Player late = TestEnv.player("late", forestWorld);
        online.add(late);
        service.onJoin(new PlayerJoinEvent(late, (String) null));
        assertEquals(List.of("§cHi"), visibleTitles(late));

        scheduler.tick(40);
        assertEquals(List.of(), visibleTitles(inPlains));
        assertEquals(List.of(), visibleTitles(late));
    }
}
