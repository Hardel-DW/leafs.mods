package fr.hardel.leafs.neoforge;

import fr.hardel.leafs.Leafs;
import fr.hardel.leafs.chunk.LeafsTicketTypes;
import fr.hardel.leafs.debug.LeafsCommand;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.util.thread.SidedThreadGroups;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(Leafs.MOD_ID)
public final class LeafsNeoForge {
    public LeafsNeoForge(IEventBus modBus) {
        // Workers join SidedThreadGroups.SERVER: EffectiveSide reads the thread group.
        Leafs.bootstrap(FMLPaths.CONFIGDIR.get(), FMLPaths.GAMEDIR.get(), SidedThreadGroups.SERVER);
        modBus.addListener((RegisterEvent event) -> event.register(Registries.TICKET_TYPE, LeafsTicketTypes.DEMAND, () -> LeafsTicketTypes.demand));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> event.getDispatcher().register(LeafsCommand.node()));
    }
}
