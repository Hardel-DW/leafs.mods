package fr.hardel.leafs.mixin.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The block entity map is read across region seams while its owner writes it, so it goes concurrent. */
@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin {

    @Mutable
    @Shadow
    @Final
    protected Map<BlockPos, BlockEntity> blockEntities;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentBlockEntities(CallbackInfo callbackInfo) {
        this.blockEntities = new ConcurrentHashMap<>();
    }
}
