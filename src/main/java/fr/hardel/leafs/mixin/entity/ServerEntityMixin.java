package fr.hardel.leafs.mixin.entity;

import fr.hardel.leafs.entity.TrackedPairingRefresh;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** A projectile spawned on a region pairs its first watcher one tick after construction; the position base refreshes at pairing so the client does not start a tick behind. */
@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin implements TrackedPairingRefresh {

    @Shadow
    @Final
    private Entity entity;

    @Shadow
    @Final
    private VecDeltaCodec positionCodec;

    @Shadow
    private Vec3 lastSentMovement;

    @Override
    public void leafs$refreshPairingBase() {
        this.positionCodec.setBase(this.entity.trackingPosition());
        this.lastSentMovement = this.entity.getDeltaMovement();
    }
}
