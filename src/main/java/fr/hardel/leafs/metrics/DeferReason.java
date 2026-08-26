package fr.hardel.leafs.metrics;

/** Why work was deferred to another execution context; one counter per reason names what runs where. */
public enum DeferReason {
    TICK_FUNCTIONS,
    COMMAND_EXECUTION,
    COMMAND_BLOCK,
    REPEATING_COMMAND_BLOCK,
    MINECART_COMMAND_BLOCK,
    SCHEDULED_FUNCTIONS,
    CONSOLE_COMMAND,
    RESPAWN,
    PORTAL,
    TELEPORT,
    PLAYER_TELEPORT,
    PLAYER_PLACEMENT,
    PLAYER_TEARDOWN
}
