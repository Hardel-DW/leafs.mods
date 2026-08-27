package fr.hardel.leafs.metrics;

/** Why work was deferred to another execution context; one counter per reason names what runs where. */
public enum DeferReason {
    RESPAWN,
    PORTAL,
    TELEPORT,
    PLAYER_TELEPORT,
    PLAYER_PLACEMENT,
    PLAYER_TEARDOWN
}
