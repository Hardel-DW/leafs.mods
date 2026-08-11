package fr.hardel.leafs.mixin.entity;

import fr.hardel.leafs.entity.TrackedPairingRefresh;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * The tracker captures its position base at construction, but a projectile spawned on a region pairs
 * its first watcher only once the serial completion makes it visible, one tick later. The add packet
 * built from the stale base starts the client one tick behind and the divergence only corrects at
 * the projectile's periodic absolute sync (roadmap 9). Refreshing on a first pairing is a no-op when
 * construction and pairing share the tick, which is the vanilla timing.
 */
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
