package fr.hardel.leafs.mixin.light;

import ca.spottedleaf.starlight.common.thread.SchedulingUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** Managed by Leafs: ScalableLux keeps its parallel light queue and never sizes a pool of its own. */
@Mixin(value = SchedulingUtil.class, remap = false)
public abstract class SchedulingUtilMixin {

    /**
     * @author Leafs
     * @reason The chunk pool schedules the light updates.
     */
    @Overwrite
    public static boolean isExternallyManaged() {
        return true;
    }
}
