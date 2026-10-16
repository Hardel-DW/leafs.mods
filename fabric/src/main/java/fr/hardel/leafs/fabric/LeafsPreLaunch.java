package fr.hardel.leafs.fabric;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class LeafsPreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        KnotSharedTransformer.install();
    }
}
