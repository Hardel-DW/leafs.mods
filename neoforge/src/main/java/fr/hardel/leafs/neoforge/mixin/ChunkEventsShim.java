package fr.hardel.leafs.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.thread.SerialEvents;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** ChunkEvent.Load and Unload, ChunkDataEvent.Load and Save. */
@Mixin({ChunkMap.class, ChunkStatusTasks.class})
public abstract class ChunkEventsShim {

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
    private static Event leafs$oneAtATime(IEventBus bus, Event event, Operation<Event> original) {
        return SerialEvents.deliver(() -> original.call(bus, event));
    }
}
