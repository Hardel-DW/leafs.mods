package fr.hardel.leafs.metrics;

import java.util.Locale;

/** Common face of the three stage enums: the ordinal indexes a {@link StageTimings} row. */
public interface TickStage {

    String name();

    int ordinal();

    default String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
