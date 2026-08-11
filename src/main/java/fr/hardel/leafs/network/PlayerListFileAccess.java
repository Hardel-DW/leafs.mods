package fr.hardel.leafs.network;

import com.mojang.authlib.GameProfile;

import java.nio.file.Path;

/** Reaches {@code PlayerList.locateStatsFile}, whose legacy-rename side effect must not be duplicated. */
public interface PlayerListFileAccess {
    Path leafs$statsFile(GameProfile profile);
}
