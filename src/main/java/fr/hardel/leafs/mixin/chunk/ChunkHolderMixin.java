package fr.hardel.leafs.mixin.chunk;

import fr.hardel.leafs.chunk.SavedEpochAccess;
import net.minecraft.server.level.ChunkHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** The autosave epoch the holder was last walked at; written by its owning region, read by the same. */
@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin implements SavedEpochAccess {

    @Unique
    private long leafs$savedEpoch;

    @Override
    public long leafs$savedEpoch() {
        return leafs$savedEpoch;
    }

    @Override
    public void leafs$markSaved(long epoch) {
        leafs$savedEpoch = epoch;
    }
}
