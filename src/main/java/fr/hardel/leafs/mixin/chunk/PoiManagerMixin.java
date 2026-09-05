package fr.hardel.leafs.mixin.chunk;

import net.minecraft.world.entity.ai.village.poi.PoiManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.SectionStorageAccess;
import fr.hardel.leafs.chunk.SectionStorageLock;
import fr.hardel.excess.ConcurrentLongSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** Concurrent loadedChunks facade; the village distance graph and the section consistency pass take the storage's lock, no facade can carry a graph. */
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
    private void leafs$tickUnderTheLock(BooleanSupplier haveTime, Operation<Void> original) {
        leafs$lock().runLocked(() -> original.call(haveTime));
    }

    @WrapMethod(method = "setDirty")
    private void leafs$dirtyUnderTheLock(long sectionPos, Operation<Void> original) {
        leafs$lock().runLocked(() -> original.call(sectionPos));
    }

    @WrapMethod(method = "onSectionLoad")
    private void leafs$sectionLoadUnderTheLock(long sectionPos, Operation<Void> original) {
        leafs$lock().runLocked(() -> original.call(sectionPos));
    }

    /** Chunk deserialization runs on the chunk workers and rewrites a section's records, so it takes the same lock as the save that packs them. */
    @WrapMethod(method = "checkConsistencyWithBlocks")
    private void leafs$consistencyUnderTheLock(SectionPos sectionPos, LevelChunkSection blockSection, Operation<Void> original) {
        leafs$lock().runLocked(() -> original.call(sectionPos, blockSection));
    }

    @WrapMethod(method = "sectionsToVillage")
    private int leafs$villageQueryUnderTheLock(SectionPos sectionPos, Operation<Integer> original) {
        return leafs$lock().callLocked(() -> original.call(sectionPos));
    }

    @Unique
    private SectionStorageLock leafs$lock() {
        return ((SectionStorageAccess) this).leafs$lock();
    }
}
