package fr.hardel.leafs.fabric;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.chunk.LeafsTicketTypes;
import fr.hardel.leafs.debug.LeafsCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class LeafsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        Leafs.bootstrap(loader.getConfigDir(), loader.getGameDir(), Thread.currentThread().getThreadGroup());
        Registry.register(BuiltInRegistries.TICKET_TYPE, LeafsTicketTypes.DEMAND, LeafsTicketTypes.demand);
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> dispatcher.register(LeafsCommand.node()));
    }
}
