package fr.hardel.leafs.metrics;

/** Why a task entered the barrier window; one counter per reason names what keeps the window busy. */
public enum WindowReason {
    TICK_FUNCTIONS,
    COMMAND_BLOCK,
    MINECART_COMMAND_BLOCK,
    SCHEDULED_FUNCTIONS,
    CONSOLE_COMMAND,
    RESPAWN,
    PORTAL
}
