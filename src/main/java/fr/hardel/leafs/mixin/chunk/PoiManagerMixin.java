package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.AreaPreload;
import fr.hardel.leafs.chunk.DegradedChunkReads;
import fr.hardel.leafs.chunk.PoiLockAccess;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.chunk.PoiVillageLock;
import fr.hardel.leafs.chunk.SectionStorageAccess;
import fr.hardel.excess.ConcurrentLongSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

// Concurrent loadedChunks facade plus village lock: the distance tracker graph cannot be made concurrent by facades.
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

    /** Chunk deserialization runs on the chunk workers and rewrites a section's records, so it takes the same lock as the save that packs them. */
    @WrapMethod(method = "checkConsistencyWithBlocks")
    private void leafs$consistencyUnderVillageLock(SectionPos sectionPos, LevelChunkSection blockSection, Operation<Void> original) {
        leafs$lock().runLocked(() -> original.call(sectionPos, blockSection));
    }

    @WrapMethod(method = "sectionsToVillage")
    private int leafs$villageQueryUnderLock(SectionPos sectionPos, Operation<Integer> original) {
        return leafs$lock().callLocked(() -> original.call(sectionPos));
    }

    // The one place POI forces chunks into existence; off the universal owner the square's absent chunks are demanded in one pass, so an exit-portal search converges in one round trip.
    @WrapMethod(method = "ensureLoadedAndValid")
    private void leafs$forceLoadsOnlyAsUniversalOwner(LevelReader reader, BlockPos center, int radius, Operation<Void> original) {
        ServerLevel level = ((SectionStorageAccess) this).leafs$level();
        if (level == null || (!DegradedChunkReads.active() && RegionChunkAccess.scheduling(level.getChunkSource().chunkMap).mayLoadSynchronously())) {
            original.call(reader, center, radius);
            return;
        }

        AreaPreload.ensurePoiSquare((PoiManager) (Object) this, level, center, radius);
    }

    @Unique
    private PoiVillageLock leafs$lock() {
        return ((PoiLockAccess) this).leafs$villageLock();
    }
}
