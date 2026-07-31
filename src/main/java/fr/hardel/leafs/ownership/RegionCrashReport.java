package fr.hardel.leafs.ownership;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Region-scoped crash text: a region crash must be understandable without digging in a global log. */
public record RegionCrashReport(long regionId, String dimension, long regionTick, int chunkCount, int entityCount) {

    public String format(Throwable cause) {
        StringWriter stackTrace = new StringWriter();
        cause.printStackTrace(new PrintWriter(stackTrace));
        return """
            ---- Leafs region crash ----
            Region: #%d
            Dimension: %s
            Region tick: %d
            Chunks owned: %d
            Entities owned: %d
            Thread: %s

            %s""".formatted(regionId, dimension, regionTick, chunkCount, entityCount, Thread.currentThread().getName(), stackTrace);
    }
}
