package fr.hardel.leafs.mixin.chunk;

import fr.hardel.leafs.chunk.EntityManagerLevelAccess;
import fr.hardel.leafs.ownership.RegionContext;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Full-status transitions arrive from the chunk thread; entity sections are game-thread state. */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin implements EntityManagerLevelAccess {

    @Unique
    private ServerLevel leafs$level;

    /** A stopping event loop runs submissions inline on the caller — the guard breaks the resulting recursion. */
    @Unique
    private final ThreadLocal<Boolean> leafs$applying = ThreadLocal.withInitial(() -> false);

    @Override
    public void leafs$bindLevel(ServerLevel level) {
        this.leafs$level = level;
    }

    @Inject(method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/server/level/FullChunkStatus;)V", at = @At("HEAD"), cancellable = true)
    private void leafs$updateStatusOnOwner(ChunkPos pos, FullChunkStatus status, CallbackInfo callbackInfo) {
        if (leafs$level != null && !leafs$applying.get() && RegionContext.current() instanceof RegionContext.Chunk) {
            PersistentEntitySectionManager<?> self = (PersistentEntitySectionManager<?>) (Object) this;
            leafs$level.getServer().execute(() -> {
                leafs$applying.set(true);
                try {
                    self.updateChunkStatus(pos, status);
                } finally {
                    leafs$applying.set(false);
                }
            });
            callbackInfo.cancel();
        }
    }
}
