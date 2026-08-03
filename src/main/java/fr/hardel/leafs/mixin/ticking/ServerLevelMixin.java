package fr.hardel.leafs.mixin.ticking;

import fr.hardel.leafs.config.LeafsConfig;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Carrier only - the logic lives in ticking/LevelRegions. The field initialiser runs immediately
 * after {@code super(...)}, so the regionizer exists before {@code ServerLevel} builds its chunk
 * source and the very first chunk holder can already be fed to it.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements ServerLevelRegionAccess {

    @Unique
    private final LevelRegions leafs$regions = new LevelRegions(LeafsConfig.get());

    @Override
    public LevelRegions leafs$regions() {
        return leafs$regions;
    }
}
