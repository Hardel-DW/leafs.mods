package fr.hardel.leafs.metrics;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.ticking.RegionCrashReport;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;

import java.time.Instant;
import java.util.Optional;

/** One JSON line per region crash, for the shared database of mods that misbehave under multithreading: what failed, where, and which mod's code was on the stack. */
public final class CrashTelemetry {
    private static final Gson GSON = new Gson();
    private static final int STACK_FRAMES = 12;

    private final ModAttribution attribution;
    private final String leafsVersion;
    private final boolean enabled;

    public CrashTelemetry(ModAttribution attribution, String leafsVersion, boolean enabled) {
        this.attribution = attribution;
        this.leafsVersion = leafsVersion;
        this.enabled = enabled;
    }

    public static CrashTelemetry fromLoader(boolean enabled) {
        String version = FabricLoader.getInstance().getModContainer(Leafs.MOD_ID).map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        return new CrashTelemetry(ModAttribution.fromLoader(), version, enabled);
    }

    public static CrashTelemetry off() {
        return new CrashTelemetry(new ModAttribution(_ -> Optional.empty()), "dev", false);
    }

    public void record(RegionCrashReport report, Throwable cause) {
        if (enabled) {
            Leafs.LOGGER.error("Leafs telemetry {}", GSON.toJson(line(report, cause)));
        }
    }

    JsonObject line(RegionCrashReport report, Throwable cause) {
        Throwable root = rootOf(cause);
        JsonObject line = new JsonObject();
        line.addProperty("time", Instant.now().toString());
        line.addProperty("leafs", leafsVersion);
        line.addProperty("minecraft", SharedConstants.getCurrentVersion().name());
        line.addProperty("dimension", report.dimension());
        line.addProperty("region", report.regionId());
        line.addProperty("region_tick", report.regionTick());
        line.addProperty("thread", Thread.currentThread().getName());
        line.addProperty("exception", root.getClass().getName());
        line.addProperty("message", root.getMessage());
        line.addProperty("details", detailsOf(cause));
        line.add("suspect", attribution.suspect(cause).map(CrashTelemetry::suspectOf).orElse(null));
        line.add("stack", stackOf(root));
        return line;
    }

    /** Vanilla's crash report names the unit that was ticking, entity or block entity, with its type and position; null when nothing vanilla wrapped the failure. */
    private static String detailsOf(Throwable cause) {
        for (Throwable throwable = cause; throwable != null; throwable = throwable.getCause()) {
            if (throwable instanceof ReportedException reported) {
                return reported.getReport().getDetails().strip();
            }
        }

        return null;
    }

    private static JsonObject suspectOf(ModAttribution.Suspect suspect) {
        JsonObject json = new JsonObject();
        json.addProperty("mod", suspect.modId());
        json.addProperty("version", suspect.version());
        json.addProperty("frame", suspect.frame());
        return json;
    }

    private static JsonArray stackOf(Throwable root) {
        JsonArray stack = new JsonArray();
        StackTraceElement[] frames = root.getStackTrace();
        for (int index = 0; index < Math.min(STACK_FRAMES, frames.length); index++) {
            stack.add(frames[index].toString());
        }

        return stack;
    }

    private static Throwable rootOf(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        return root;
    }
}
