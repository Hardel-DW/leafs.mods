package fr.hardel.leafs.ticking;

public record TickState(float tickRate, long nanosPerTick, boolean frozen, boolean paused, Order step, Order sprint) {
    public static final TickState INITIAL = new TickState(20.0F, 50_000_000L, false, false, Order.NONE, Order.NONE);

    public record Order(long id, long ticks, long serverLeft) {
        public static final Order NONE = new Order(0L, 0L, 0L);
    }
}
