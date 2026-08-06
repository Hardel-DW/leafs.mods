package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fabricmc.fabric.impl.lookup.block.BlockApiCacheImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;

/** Serializes fabric-api-lookup's per-level cache map; priority 1100 so fabric's methods exist when this applies. */
@Mixin(value = ServerLevel.class, priority = 1100)
public abstract class FabricApiLookupCacheShim {

    @WrapMethod(method = "fabric_registerCache", remap = false)
    private void leafs$lockedRegister(BlockPos pos, BlockApiCacheImpl<?, ?> cache, Operation<Void> original) {
        synchronized (this) {
            original.call(pos, cache);
        }
    }

    @WrapMethod(method = "fabric_invalidateCache", remap = false)
    private void leafs$lockedInvalidate(BlockPos pos, Operation<Void> original) {
        synchronized (this) {
            original.call(pos);
        }
    }
}
