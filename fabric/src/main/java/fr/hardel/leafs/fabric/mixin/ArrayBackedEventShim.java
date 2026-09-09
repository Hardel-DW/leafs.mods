package fr.hardel.leafs.fabric.mixin;

import fr.hardel.leafs.fabric.FabricEventAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes whether a Fabric event has any subscriber, state its public API hides. */
@Mixin(targets = "net.fabricmc.fabric.impl.base.event.ArrayBackedEvent", remap = false)
public abstract class ArrayBackedEventShim implements FabricEventAccess {

    @Shadow(remap = false)
    private Object[] handlers;

    @Override
    public boolean leafs$hasSubscribers() {
        return handlers.length > 0;
    }
}
