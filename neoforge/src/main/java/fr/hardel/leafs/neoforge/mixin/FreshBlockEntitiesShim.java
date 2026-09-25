package fr.hardel.leafs.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.owner.Work;
import fr.hardel.leafs.world.ChunkTickAccess;
import fr.hardel.leafs.world.ChunkTickers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.List;

@Mixin(LevelChunk.class)
public abstract class FreshBlockEntitiesShim {

    @WrapOperation(method = {"addAndRegisterBlockEntity", "registerAllBlockEntitiesAfterLevelLoad"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshBlockEntities(Ljava/util/Collection;)V"))
    private void leafs$loadOnTheChunkPass(Level level, Collection<BlockEntity> blockEntities, Operation<Void> original) {
        if (!(level instanceof ServerLevel serverLevel)) {
            original.call(level, blockEntities);
            return;
        }

        List<BlockEntity> fresh = List.copyOf(blockEntities);
        ChunkTickers tickers = ((ChunkTickAccess) this).leafs$tickers();
        if (tickers.beforePass(() -> fresh.stream().filter(blockEntity -> !blockEntity.isRemoved() && blockEntity.hasLevel()).forEach(BlockEntity::onLoad))) {
            ChunkPos pos = ((LevelChunk) (Object) this).getPos();
            LevelChunks.of(serverLevel).owners().later(pos.x(), pos.z(), Work.GAME, tickers::open);
        }
    }
}
