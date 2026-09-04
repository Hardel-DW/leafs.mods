package fr.hardel.leafs.mixin.light;

import ca.spottedleaf.starlight.common.integration.v0.ChunkSystemHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** ScalableLux's questions to the chunk system: the ticket storage takes writers from any thread, its tickets stay vanilla's. */
@Mixin(value = ChunkSystemHooks.class, remap = false)
public abstract class ChunkSystemHooksMixin {
    /**
     * @author Leafs
     * @reason The ticket storage takes writers from any thread.
     */
    @Overwrite
    public static boolean isTicketThreadSafe() {
        return true;
    }
}
