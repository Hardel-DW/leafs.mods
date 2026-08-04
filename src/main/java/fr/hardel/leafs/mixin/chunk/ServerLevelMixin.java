package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.PoiWriteReroute;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Save coordination and the #22b write hop. Saves snapshot chunk and entity state, so they take the
 * level's exclusion; POI writes belong to the level's single mutator, never the global queue. Fifth
 * mixin of this name: different package, different concern.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @WrapMethod(method = "save")
    private void leafs$saveUnderExclusion(@Nullable ProgressListener progressListener, boolean flush, boolean noSave, Operation<Void> original) {
        ((ServerLevelRegionAccess) this).leafs$regions().ownership().runExclusive(() -> original.call(progressListener, flush, noSave));
    }

    @Inject(method = "updatePOIOnBlockStateChange", at = @At("HEAD"), cancellable = true)
    private void leafs$poiWriteToLevelSerial(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo callbackInfo) {
        ServerLevel self = (ServerLevel) (Object) this;
        PoiWriteReroute.onBlockStateChange(self, pos, oldState, newState, task -> ((LeafsServerAccess) self.getServer()).leafs$ticking().submitToLevel(self, task));
        callbackInfo.cancel();
    }
}
