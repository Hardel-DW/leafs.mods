package fr.hardel.leafs.mixin.world;

import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import fr.hardel.leafs.world.AnchoredTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The fight ticks as a block entity of its origin, on the owner of that chunk; a replaced fight retires its ticker. */
@Mixin(EnderDragonFight.class)
public abstract class EnderDragonFightMixin {

    @Shadow
    public abstract void tick();

    @Inject(method = "init", at = @At("TAIL"))
    private void leafs$tickAsAnchored(ServerLevel level, long seed, BlockPos origin, CallbackInfo callbackInfo) {
        EnderDragonFight self = (EnderDragonFight) (Object) this;
        ((ServerLevelRegionAccess) level).leafs$anchors().add(new AnchoredTicker(origin, this::tick, () -> level.getDragonFight() != self)
);
    }
}
