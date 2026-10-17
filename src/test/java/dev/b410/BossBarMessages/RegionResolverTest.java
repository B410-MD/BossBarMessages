package dev.b410.BossBarMessages;

import org.bukkit.World;
import org.bukkit.block.Biome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionResolverTest {

    @BeforeAll
    static void env() {
        TestEnv.install();
    }

    private final Biome plains = TestEnv.biome("plains");
    private final World overworld = TestEnv.world("World", World.Environment.NORMAL, plains);
    private final World nether = TestEnv.world("world_nether", World.Environment.NETHER, TestEnv.biome("nether_wastes"));
    private final World end = TestEnv.world("world_the_end", World.Environment.THE_END, TestEnv.biome("the_end"));

    @Test
    void biomeWinsOverWorldAndEnvironment() {
        RegionResolver r = new RegionResolver(Set.of("plains", "world", "overworld"));
        assertEquals("plains", r.resolve(overworld, plains));
    }

    @Test
    void worldNameWinsOverEnvironmentAndIsCaseInsensitive() {
        RegionResolver r = new RegionResolver(Set.of("world", "overworld"));
        assertEquals("world", r.resolve(overworld, plains));
    }

    @Test
    void fallsBackToEnvironment() {
        RegionResolver r = new RegionResolver(Set.of("overworld", "nether", "the_end"));
        assertEquals("overworld", r.resolve(overworld, plains));
        assertEquals("nether", r.resolve(nether, TestEnv.biome("nether_wastes")));
        assertEquals("the_end", r.resolve(end, TestEnv.biome("small_end_islands")));
    }

    @Test
    void fallsBackToDefault() {
        RegionResolver r = new RegionResolver(Set.of());
        assertEquals(RegionResolver.DEFAULT_KEY, r.resolve(overworld, plains));
    }

    @Test
    void nullBiomeIsIgnored() {
        RegionResolver r = new RegionResolver(Set.of("overworld"));
        assertEquals("overworld", r.resolve(overworld, null));
    }

    @Test
    void customEnvironmentFallsBackToDefault() {
        World custom = TestEnv.world("custom", World.Environment.CUSTOM, plains);
        RegionResolver r = new RegionResolver(Set.of("overworld"));
        assertEquals(RegionResolver.DEFAULT_KEY, r.resolve(custom, null));
    }
}
