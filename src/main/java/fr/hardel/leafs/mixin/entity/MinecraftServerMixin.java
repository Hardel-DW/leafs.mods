package fr.hardel.leafs.mixin.entity;

import fr.hardel.leafs.entity.EntitySchedulerRegistry;
import fr.hardel.leafs.entity.ServerEntityAccess;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the server's entity-scheduler registry. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements ServerEntityAccess {

    @Unique
    private final EntitySchedulerRegistry leafs$entitySchedulers = new EntitySchedulerRegistry();

    @Override
    public EntitySchedulerRegistry leafs$entitySchedulers() {
        return leafs$entitySchedulers;
    }
}
