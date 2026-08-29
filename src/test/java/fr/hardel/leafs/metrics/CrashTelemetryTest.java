package fr.hardel.leafs.metrics;

import com.google.gson.JsonObject;
import fr.hardel.leafs.ticking.RegionCrashReport;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashTelemetryTest {

    @BeforeAll
    static void version() {
        SharedConstants.tryDetectVersion();
    }

    /** The class map of a fake install: one mod, everything else is nobody's. */
    private static ModAttribution attributionWith(String modClass) {
        return new ModAttribution(className -> className.equals(modClass) ? Optional.of(new ModAttribution.Suspect("somemod", "1.2", "")) : Optional.empty());
    }

    private static Throwable failureThrough(String modClass) {
        RuntimeException root = new RuntimeException("boom");
        root.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("net.minecraft.world.entity.Entity", "tick", "Entity.java", 10),
            new StackTraceElement(modClass, "onTick", "Thing.java", 42),
            new StackTraceElement("fr.hardel.leafs.world.RegionTickBody", "tick", "RegionTickBody.java", 90)
        });
        CrashReport report = CrashReport.forThrowable(root, "Ticking entity");
        report.addCategory("Entity being ticked").setDetail("Entity Type", "somemod:thing");
        return new ReportedException(report);
    }

    @Test
    void theLineNamesTheModOnTheRootStackAndVanillasUnitDetails() {
        CrashTelemetry telemetry = new CrashTelemetry(attributionWith("com.example.somemod.Thing"), "1.0.0", true);
        RegionCrashReport region = new RegionCrashReport(7, "minecraft:overworld", 1234, 50, 3);

        JsonObject line = telemetry.line(region, failureThrough("com.example.somemod.Thing"));

        assertEquals("somemod", line.getAsJsonObject("suspect").get("mod").getAsString());
        assertEquals("1.2", line.getAsJsonObject("suspect").get("version").getAsString());
        assertTrue(line.getAsJsonObject("suspect").get("frame").getAsString().startsWith("com.example.somemod.Thing.onTick"));
        assertEquals("java.lang.RuntimeException", line.get("exception").getAsString());
        assertEquals("boom", line.get("message").getAsString());
        assertEquals(7, line.get("region").getAsLong());
        assertTrue(line.get("details").getAsString().contains("Entity Type: somemod:thing"));
    }

    @Test
    void noModOnTheStackMeansNoSuspect() {
        CrashTelemetry telemetry = new CrashTelemetry(attributionWith("com.example.other.Thing"), "1.0.0", true);

        JsonObject line = telemetry.line(new RegionCrashReport(1, "minecraft:the_end", 1, 0, 0), failureThrough("com.example.somemod.Thing"));

        assertTrue(line.get("suspect").isJsonNull());
    }
}
