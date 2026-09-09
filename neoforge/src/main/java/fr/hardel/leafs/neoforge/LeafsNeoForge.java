package fr.hardel.leafs.neoforge;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.chunk.LeafsTicketTypes;
import fr.hardel.leafs.debug.LeafsCommand;
import fr.hardel.leafs.metrics.ModAttribution;
import fr.hardel.leafs.metrics.ModAttribution.Suspect;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.util.thread.SidedThreadGroups;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Mod(Leafs.MOD_ID)
public final class LeafsNeoForge {

    public LeafsNeoForge(IEventBus modBus) {
        Leafs.bootstrap(new Leafs.Platform(FMLPaths.CONFIGDIR.get(), FMLPaths.GAMEDIR.get(), ModAttribution.fromOrigins(origins()), SidedThreadGroups.SERVER));
        modBus.addListener((RegisterEvent event) -> event.register(Registries.TICKET_TYPE, LeafsTicketTypes.DEMAND, () -> LeafsTicketTypes.demand));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> event.getDispatcher().register(LeafsCommand.node()));
    }

    /** Every mod by the file that carries it; NeoForge itself is the platform, never a suspect. */
    private static Map<Path, Suspect> origins() {
        Map<Path, Suspect> byOrigin = new HashMap<>();
        for (IModInfo mod : ModList.get().getMods()) {
            String id = mod.getModId();
            if (id.equals("neoforge")) {
                continue;
            }

            byOrigin.put(mod.getOwningFile().getFile().getFilePath().toAbsolutePath().normalize(), new Suspect(id, mod.getVersion().toString(), ""));
        }

        return byOrigin;
    }
}
