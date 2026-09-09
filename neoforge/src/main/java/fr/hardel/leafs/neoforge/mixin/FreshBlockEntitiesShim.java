package fr.hardel.leafs.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.world.ChunkTickAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.List;

/** NeoForge defers a block entity's load callback to the level's next pass, in one level-wide list; on a server level the chunk's own pass runs it, on the owner, before the first tick. */
@Mixin(LevelChunk.class)
public abstract class FreshBlockEntitiesShim {

    @WrapOperation(method = {"addAndRegisterBlockEntity", "registerAllBlockEntitiesAfterLevelLoad"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshBlockEntities(Ljava/util/Collection;)V"))
    private void leafs$loadOnTheChunkPass(Level level, Collection<BlockEntity> blockEntities, Operation<Void> original) {
        if (!(level instanceof ServerLevel)) {
            original.call(level, blockEntities);
            return;
        }

        List<BlockEntity> fresh = List.copyOf(blockEntities);
        ((ChunkTickAccess) this).leafs$tickers().beforePass(() -> {
            for (BlockEntity blockEntity : fresh) {
                if (!blockEntity.isRemoved() && blockEntity.hasLevel()) {
                    blockEntity.onLoad();
                }
            }
        });
    }
}
