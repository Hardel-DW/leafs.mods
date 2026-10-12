package fr.hardel.leafs.gametest;

/** A test waits for a state, never for a duration: this budget of server ticks, at 20 TPS, only stops a test that hangs. */
public final class HangGuard {
    public static final int TICKS = 6000;

    private HangGuard() {
    }
}
