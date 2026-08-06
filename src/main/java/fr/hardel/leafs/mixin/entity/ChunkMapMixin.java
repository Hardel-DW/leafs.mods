package fr.hardel.leafs.mixin.entity;

import fr.hardel.leafs.entity.ConcurrentInt2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Regionizer feed: chunk holder creation and destruction. Mutations alternate strictly per position. */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Mutable
    @Shadow
    @Final
    private Int2ObjectMap<?> entityMap;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacade(CallbackInfo callbackInfo) {
        this.entityMap = new ConcurrentInt2ObjectMap<>();
    }
}
