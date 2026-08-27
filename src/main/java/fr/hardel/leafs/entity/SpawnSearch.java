package fr.hardel.leafs.entity;

import fr.hardel.leafs.chunk.AreaPreload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** A player's spawn search in flight. A region refuses instead of blocking on it, and the replayed move must find the same search, not start another. */
public final class SpawnSearch {
    private CompletableFuture<Vec3> search;
    private ServerLevel level;
    private BlockPos suggestion;

    public synchronized CompletableFuture<Vec3> resume(ServerLevel level, BlockPos suggestion, Supplier<CompletableFuture<Vec3>> start) {
        if (search == null || this.level != level || !this.suggestion.equals(suggestion)) {
            search = start.get();
            this.level = level;
            this.suggestion = suggestion.immutable();
        }

        return search;
    }

    /** Waits on the server thread, refuses on a region; a finished search is forgotten so the next portal starts fresh. */
    public void await(ServerLevel level, CompletableFuture<Vec3> search) {
        AreaPreload.awaitOrRefuse(level, search, "spawn search around " + suggestion);
        synchronized (this) {
            if (this.search == search) {
                this.search = null;
            }
        }
    }
}
