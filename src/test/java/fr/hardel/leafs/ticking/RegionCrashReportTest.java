package fr.hardel.leafs.ticking;

import fr.hardel.leafs.metrics.ModAttribution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionCrashReportTest {

    @Test
    void reportIsScopedToTheRegion() {
        RegionCrashReport report = new RegionCrashReport(42, "minecraft:the_end", 12345, 96, 210);

        String text = report.format(ModAttribution.none(), new IllegalStateException("boom"));

        assertTrue(text.contains("Region: #42"));
        assertTrue(text.contains("Dimension: minecraft:the_end"));
        assertTrue(text.contains("Region tick: 12345"));
        assertTrue(text.contains("Chunks owned: 96"));
        assertTrue(text.contains("Entities owned: 210"));
        assertTrue(text.contains("Thread: " + Thread.currentThread().getName()));
        assertTrue(text.contains("IllegalStateException: boom"));
        assertTrue(text.contains("reportIsScopedToTheRegion"));
    }
}
