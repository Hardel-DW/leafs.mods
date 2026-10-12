package fr.hardel.leafs.gametest.mixin;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SectionStorage.class)
public interface SectionStorageAccessor {

    @Accessor("dirtyChunks")
    LongLinkedOpenHashSet leafs$dirtyChunks();
}
