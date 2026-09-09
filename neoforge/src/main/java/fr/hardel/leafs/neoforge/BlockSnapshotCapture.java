package fr.hardel.leafs.neoforge;

import net.neoforged.neoforge.common.util.BlockSnapshot;

import java.util.ArrayList;

/** NeoForge records a placement's block changes on the level, one capture at a time on the server thread; regions place in parallel, so the capture in flight is the thread's. */
public final class BlockSnapshotCapture {
    private static final ThreadLocal<BlockSnapshotCapture> CURRENT = ThreadLocal.withInitial(BlockSnapshotCapture::new);

    public boolean capturing;
    public boolean restoring;
    public final ArrayList<BlockSnapshot> snapshots = new ArrayList<>();

    private BlockSnapshotCapture() {
    }

    public static BlockSnapshotCapture current() {
        return CURRENT.get();
    }
}
