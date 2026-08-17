package fr.hardel.leafs.metrics;

/** The stages of one global server tick, in the execution order of {@code tickServer}. */
public enum GlobalStage implements TickStage {
    LEVELS,
    GLOBAL_DRAIN,
    WINDOW,
    CONNECTIONS,
    PLAYERS,
    SEND_CHUNKS,
    QUIESCE,
    AUTOSAVE
}
