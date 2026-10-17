package dev.b410.BossBarMessages;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossBarConfigTest {

    private static BossBarConfig load(String yaml) {
        return BossBarConfig.load(TestEnv.plugin(yaml, TestEnv.tempDir()));
    }

    @Test
    void bundledConfigLoads() {
        BossBarConfig cfg = load(TestEnv.bundledConfig());

        assertTrue(cfg.isAutoEnabled());
        assertEquals("en", cfg.lang());
        assertEquals(5, cfg.definitions().size());
        assertEquals(List.of("welcome", "rules", "discord"), cfg.defaultRotation().bossbarIds());
        assertEquals(600L, cfg.defaultRotation().gapTicks());
        // all example regions ship disabled, so everyone gets 'default'
        assertEquals(Set.of(), cfg.knownRegionKeys());

        BossBarDefinition welcome = cfg.findById("WELCOME");
        assertNotNull(welcome);
        assertEquals(BarColor.GREEN, welcome.color());
        assertEquals(BarStyle.SOLID, welcome.style());
        assertEquals(200L, welcome.durationTicks());
        assertEquals("minecraft:block.note_block.pling", welcome.sound());
        assertNull(cfg.findById("rules").sound());
    }

    @Test
    void autoBroadcastDefaultsToOnWhenMissing() {
        assertTrue(load("bossbars: []").isAutoEnabled());
        assertFalse(load("auto-broadcast:\n  enabled: false").isAutoEnabled());
    }

    @Test
    void enabledRegionWithEmptyOrMissingListIsSilentZone() {
        BossBarConfig cfg = load("""
                auto-broadcast:
                  regions:
                    plains:
                      enabled: true
                      bossbars: []
                    desert:
                      enabled: true
                    forest:
                      enabled: false
                      bossbars: [a]
                bossbars:
                  - {id: a, message: A, color: red, style: solid, duration: 1s}
                """);

        assertEquals(Set.of("plains", "desert"), cfg.knownRegionKeys());
        assertTrue(cfg.regionRotations().get("plains").bossbarIds().isEmpty());
        assertTrue(cfg.regionRotations().get("desert").bossbarIds().isEmpty());
    }

    @Test
    void bundledDefaultsDoNotRefillDeletedLists() {
        BossBarConfig cfg = load("""
                auto-broadcast:
                  regions:
                    plains:
                      enabled: true
                """);
        assertEquals(Set.of("plains"), cfg.knownRegionKeys());
        assertTrue(cfg.regionRotations().get("plains").bossbarIds().isEmpty());
        assertTrue(cfg.definitions().isEmpty(), "deleted bossbars list must stay empty");
        assertFalse(cfg.defaultRotation().enabled(), "deleted default rotation must stay off");
    }

    @Test
    void regionKeysAreLowercasedAndDefaultIsReserved() {
        BossBarConfig cfg = load("""
                auto-broadcast:
                  regions:
                    Plains:
                      bossbars: [a]
                    default:
                      bossbars: [a]
                bossbars:
                  - {id: a, message: A, color: red, style: solid, duration: 1s}
                """);
        assertEquals(Set.of("plains"), cfg.knownRegionKeys());
    }

    @Test
    void invalidEntriesAreSkipped() {
        BossBarConfig cfg = load("""
                bossbars:
                  - {id: ok, message: A, color: red, style: solid, duration: 1s}
                  - {id: ok, message: dup, color: red, style: solid, duration: 1s}
                  - {id: badcolor, message: A, color: orange, style: solid, duration: 1s}
                  - {id: badstyle, message: A, color: red, style: dotted, duration: 1s}
                  - {id: badtime, message: A, color: red, style: solid, duration: 0s}
                  - {id: nomsg, color: red, style: solid, duration: 1s}
                  - {id: emptysound, message: A, color: red, style: segmented_20, duration: 2m, sound: ""}
                """);
        assertEquals(List.of("ok", "emptysound"), cfg.definitions().stream().map(BossBarDefinition::id).toList());
        assertEquals("A", cfg.findById("ok").message());
        assertNull(cfg.findById("emptysound").sound());
    }

    @Test
    void rotationDropsUnknownAndDuplicateIdsAndBadGap() {
        BossBarConfig cfg = load("""
                auto-broadcast:
                  default:
                    gap: forever
                    loop: false
                    bossbars: [a, missing, A, b]
                bossbars:
                  - {id: a, message: A, color: red, style: solid, duration: 1s}
                  - {id: b, message: B, color: red, style: solid, duration: 1s}
                """);
        RegionRotation def = cfg.defaultRotation();
        assertEquals(List.of("a", "b"), def.bossbarIds());
        assertEquals(0L, def.gapTicks());
        assertFalse(def.loop());
        assertTrue(def.enabled());
    }

    @Test
    void missingDefaultSectionMeansNoDefaultRotation() {
        BossBarConfig cfg = load("auto-broadcast:\n  enabled: true");
        assertFalse(cfg.defaultRotation().enabled());
        assertEquals(Map.of(), cfg.countsByRegion());
    }
}
