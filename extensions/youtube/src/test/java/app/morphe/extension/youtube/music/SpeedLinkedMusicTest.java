package app.morphe.extension.youtube.music;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import org.junit.Test;

public class SpeedLinkedMusicTest {
    private static final List<SpeedLinkedMusic.SpeedBand> BANDS = SpeedLinkedMusic.defaultBands();

    @Test public void boundariesAreNonOverlapping() {
        assertEquals(SpeedLinkedMusic.Band.STOPPED, SpeedLinkedMusic.classify(0f, BANDS));
        assertEquals(SpeedLinkedMusic.Band.WALKING, SpeedLinkedMusic.classify(2f / 3.6f, BANDS));
        assertEquals(SpeedLinkedMusic.Band.RUNNING, SpeedLinkedMusic.classify(7f / 3.6f, BANDS));
        assertEquals(SpeedLinkedMusic.Band.MEDIUM, SpeedLinkedMusic.classify(15f / 3.6f, BANDS));
        assertEquals(SpeedLinkedMusic.Band.FAST, SpeedLinkedMusic.classify(30f / 3.6f, BANDS));
        assertEquals(SpeedLinkedMusic.Band.VERY_FAST, SpeedLinkedMusic.classify(80f / 3.6f, BANDS));
    }

    @Test public void invalidSpeedIsUnknown() {
        assertNull(SpeedLinkedMusic.classify(Float.NaN, BANDS));
        assertNull(SpeedLinkedMusic.classify(-1f, BANDS));
    }

    @Test public void smootherTrimsGpsSpike() {
        SpeedLinkedMusic.Smoother smoother = new SpeedLinkedMusic.Smoother(15_000L);
        smoother.add(1f, 1_000L); smoother.add(1f, 4_000L); smoother.add(20f, 7_000L); smoother.add(1f, 10_000L); smoother.add(1f, 13_000L);
        assertEquals(1f, smoother.value(), 0.001f);
    }

    @Test public void stabilizerRequiresDwellAndCooldown() {
        SpeedLinkedMusic.Stabilizer state = new SpeedLinkedMusic.Stabilizer(BANDS, 10_000L, 60_000L);
        assertNull(state.update(0f, 0L));
        state.force(SpeedLinkedMusic.Band.STOPPED, 0L);
        assertEquals(SpeedLinkedMusic.Band.STOPPED, state.update(3f, 1_000L));
        assertEquals(SpeedLinkedMusic.Band.STOPPED, state.update(3f, 11_000L));
        assertEquals(SpeedLinkedMusic.Band.STOPPED, state.update(3f, 59_000L));
        assertEquals(SpeedLinkedMusic.Band.STOPPED, state.update(3f, 69_000L));
        assertEquals(SpeedLinkedMusic.Band.RUNNING, state.update(3f, 79_000L));
    }

    @Test public void stabilizerAppliesBoundaryHysteresis() {
        SpeedLinkedMusic.Stabilizer state = new SpeedLinkedMusic.Stabilizer(BANDS, 0L, 0L, 0.5f);
        state.force(SpeedLinkedMusic.Band.STOPPED, 0L);
        assertEquals(SpeedLinkedMusic.Band.STOPPED, state.update(2.2f / 3.6f, 1_000L));
        assertEquals(SpeedLinkedMusic.Band.WALKING, state.update(2.6f / 3.6f, 2_000L));
    }

    @Test public void environmentPriorityCanBeChangedAndCompoundWins() {
        EnvironmentConditionResolver resolver = new EnvironmentConditionResolver();
        Map<EnvironmentConditionResolver.Source, String> candidates = new EnumMap<>(EnvironmentConditionResolver.Source.class);
        candidates.put(EnvironmentConditionResolver.Source.SPEED, "speed123456");
        candidates.put(EnvironmentConditionResolver.Source.WEATHER, "rain1234567");
        assertEquals(EnvironmentConditionResolver.Source.SPEED, resolver.resolve(null, candidates).source);
        resolver.setPriority(List.of(EnvironmentConditionResolver.Source.WEATHER, EnvironmentConditionResolver.Source.SPEED,
                EnvironmentConditionResolver.Source.MANUAL, EnvironmentConditionResolver.Source.TIME,
                EnvironmentConditionResolver.Source.TERRAIN, EnvironmentConditionResolver.Source.DEFAULT));
        assertEquals(EnvironmentConditionResolver.Source.WEATHER, resolver.resolve(null, candidates).source);
        resolver.setCompoundRule("WALKING+RAIN", "compound001");
        assertEquals("compound001", resolver.resolve("WALKING+RAIN", candidates).videoId);
    }
}
