package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.PoiLockAccess;
import fr.hardel.leafs.chunk.PoiVillageLock;
import fr.hardel.leafs.entity.ConcurrentLongSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** Concurrent loadedChunks facade plus village lock: the distance tracker graph cannot be made concurrent by facades. */
@Mixin(PoiManager.class)
public abstract class PoiManagerMixin {

    @Mutable
    @Shadow
    @Final
    private LongSet loadedChunks;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacade(CallbackInfo callbackInfo) {
        this.loadedChunks = new ConcurrentLongSet();
    }

    @WrapMethod(method = "tick")
    private void leafs$tickUnderVillageLock(BooleanSupplier haveTime, Operation<Void> original) {
        leafs$lock().runLocked(() -> original.call(haveTime));
    }

    @WrapMethod(method = "setDirty")
    private void leafs$dirtyUnderVillageLock(long sectionPos, Operation<Void> original) {
        leafs$lock().runLocked(() -> original.call(sectionPos));
    }

    @WrapMethod(method = "onSectionLoad")
    private void leafs$sectionLoadUnderVillageLock(long sectionPos, Operation<Void> original) {
        leafs$lock().runLocked(() -> original.call(sectionPos));
    }

    @WrapMethod(method = "sectionsToVillage")
    private int leafs$villageQueryUnderLock(SectionPos sectionPos, Operation<Integer> original) {
        return leafs$lock().callLocked(() -> original.call(sectionPos));
    }

    @Unique
    private PoiVillageLock leafs$lock() {
        return ((PoiLockAccess) this).leafs$villageLock();
    }
}
