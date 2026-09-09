package fr.hardel.leafs.fabric;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.chunk.LeafsTicketTypes;
import fr.hardel.leafs.debug.LeafsCommand;
import fr.hardel.leafs.metrics.ModAttribution;
import fr.hardel.leafs.metrics.ModAttribution.Suspect;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class LeafsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        Leafs.bootstrap(new Leafs.Platform(loader.getConfigDir(), loader.getGameDir(), ModAttribution.fromOrigins(origins(loader)), Thread.currentThread().getThreadGroup()));
        Registry.register(BuiltInRegistries.TICKET_TYPE, LeafsTicketTypes.DEMAND, LeafsTicketTypes.demand);
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> dispatcher.register(LeafsCommand.node()));
    }

    /** Every mod with a file of its own; the loader and the Fabric API modules are the platform, never suspects. */
    private static Map<Path, Suspect> origins(FabricLoader loader) {
        Map<Path, Suspect> byOrigin = new HashMap<>();
        for (ModContainer mod : loader.getAllMods()) {
            String id = mod.getMetadata().getId();
            if (mod.getOrigin().getKind() != ModOrigin.Kind.PATH || id.equals("fabricloader") || id.startsWith("fabric-")) {
                continue;
            }

            for (Path path : mod.getOrigin().getPaths()) {
                byOrigin.put(path.toAbsolutePath().normalize(), new Suspect(id, mod.getMetadata().getVersion().getFriendlyString(), ""));
            }
        }

        return byOrigin;
    }
}
