package fr.hardel.leafs.neoforge;

import net.neoforged.neoforge.common.util.BlockSnapshot;

import java.util.ArrayList;

public final class BlockSnapshotCapture {
    private static final ThreadLocal<BlockSnapshotCapture> CURRENT = ThreadLocal.withInitial(BlockSnapshotCapture::new);
    private final ArrayList<BlockSnapshot> snapshots = new ArrayList<>();
    private boolean capturing;
    private boolean restoring;

    private BlockSnapshotCapture() {
    }

    public static BlockSnapshotCapture current() {
        return CURRENT.get();
    }

    public ArrayList<BlockSnapshot> snapshots() {
        return snapshots;
    }

    public boolean capturing() {
        return capturing;
    }

    public void setCapturing(boolean capturing) {
        this.capturing = capturing;
    }

    public boolean restoring() {
        return restoring;
    }

    public void setRestoring(boolean restoring) {
        this.restoring = restoring;
    }
}
