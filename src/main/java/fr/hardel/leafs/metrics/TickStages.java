package fr.hardel.leafs.metrics;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.LeafsBuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stage catalogue of the three tick families. Declaration order is execution order and slot; the only stage list, published to the registry at mod init. */
public final class TickStages {
    public enum TickFamily {
        GLOBAL,
        SERIAL,
        REGION
    }

    /** One named stage of a tick unit's body; the index is its slot in the family's timing rows, the id its registry name. */
    public record TickStage(TickFamily family, int index, Identifier id) {}

    private static final Map<TickFamily, List<TickStage>> byFamily = new EnumMap<>(TickFamily.class);
    public static final TickStage globalLevels = create(TickFamily.GLOBAL, "levels");
    public static final TickStage globalDrain = create(TickFamily.GLOBAL, "drain");
    public static final TickStage globalConnections = create(TickFamily.GLOBAL, "connections");
    public static final TickStage globalPlayers = create(TickFamily.GLOBAL, "players");
    public static final TickStage globalAutosave = create(TickFamily.GLOBAL, "autosave");

    public static final TickStage serialTasks = create(TickFamily.SERIAL, "tasks");
    public static final TickStage serialBorder = create(TickFamily.SERIAL, "border");
    public static final TickStage serialWeather = create(TickFamily.SERIAL, "weather");
    public static final TickStage serialTime = create(TickFamily.SERIAL, "time");
    public static final TickStage serialRaids = create(TickFamily.SERIAL, "raids");
    public static final TickStage serialPurge = create(TickFamily.SERIAL, "purge");
    public static final TickStage serialView = create(TickFamily.SERIAL, "view");
    public static final TickStage serialTracking = create(TickFamily.SERIAL, "tracking");
    public static final TickStage serialUnloads = create(TickFamily.SERIAL, "unloads");
    public static final TickStage serialDragon = create(TickFamily.SERIAL, "dragon");
    public static final TickStage serialManagement = create(TickFamily.SERIAL, "management");

    public static final TickStage regionTasks = create(TickFamily.REGION, "tasks");
    public static final TickStage regionUnloads = create(TickFamily.REGION, "unloads");
    public static final TickStage regionTickets = create(TickFamily.REGION, "tickets");
    public static final TickStage regionPackets = create(TickFamily.REGION, "packets");
    public static final TickStage regionBlockTicks = create(TickFamily.REGION, "block_ticks");
    public static final TickStage regionFluidTicks = create(TickFamily.REGION, "fluid_ticks");
    public static final TickStage regionSpawnCensus = create(TickFamily.REGION, "spawn_census");
    public static final TickStage regionChunkTick = create(TickFamily.REGION, "chunk_tick");
    public static final TickStage regionBroadcast = create(TickFamily.REGION, "broadcast");
    public static final TickStage regionTracking = create(TickFamily.REGION, "tracking");
    public static final TickStage regionBlockEvents = create(TickFamily.REGION, "block_events");
    public static final TickStage regionEntities = create(TickFamily.REGION, "entities");
    public static final TickStage regionBlockEntities = create(TickFamily.REGION, "block_entities");
    public static final TickStage regionPlayers = create(TickFamily.REGION, "players");
    public static final TickStage regionAutosave = create(TickFamily.REGION, "autosave");

    static {
        byFamily.replaceAll((_, stages) -> List.copyOf(stages));
    }

    private TickStages() {
    }

    /** Stages of one family, in execution order; index {@code i} of the list owns slot {@code i} of the timing rows. */
    public static List<TickStage> of(TickFamily family) {
        return byFamily.get(family);
    }

    public static int count(TickFamily family) {
        return byFamily.get(family).size();
    }

    public static void register() {
        for (List<TickStage> stages : byFamily.values()) {
            for (TickStage stage : stages) {
                Registry.register(LeafsBuiltInRegistries.TICK_STAGE, stage.id(), stage);
            }
        }
    }

    private static TickStage create(TickFamily family, String name) {
        List<TickStage> stages = byFamily.computeIfAbsent(family, _ -> new ArrayList<>());
        Identifier id = Identifier.fromNamespaceAndPath(Leafs.MOD_ID, family.name().toLowerCase(Locale.ROOT) + "/" + name);
        TickStage stage = new TickStage(family, stages.size(), id);
        stages.add(stage);
        return stage;
    }
}
