package fr.hardel.leafs.neoforge;

import net.neoforged.neoforge.common.util.BlockSnapshot;

import java.util.ArrayList;

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
