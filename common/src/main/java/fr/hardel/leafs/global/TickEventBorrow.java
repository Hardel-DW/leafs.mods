package fr.hardel.leafs.global;

import fr.hardel.leafs.metrics.MinuteCounter;
import fr.hardel.leafs.ticking.RegionBorrow;

/** A loader's server tick event runs with the server thread borrowing: a mod that touches nothing stops nobody, one that touches a chunk or an entity takes its region at contact. Server thread only. */
public final class TickEventBorrow {
    private final MinuteCounter borrows;
    private boolean open;

    public TickEventBorrow(MinuteCounter borrows) {
        this.borrows = borrows;
    }

    /** Right before the emission point; a second open before the close borrows once. */
    public void open() {
        if (open) {
            return;
        }

        borrows.increment();
        RegionBorrow.enter();
        open = true;
    }

    /** After the emission point; without a matching open this is a no-op, so skipped-tick branches close safely. */
    public void close() {
        if (open) {
            open = false;
            RegionBorrow.current().releaseAll();
            RegionBorrow.exit();
        }
    }
}
