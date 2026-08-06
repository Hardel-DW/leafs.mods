package fr.hardel.leafs.mixin.ticking;

import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carrier only. Field initialiser runs after {@code super(...)}, so the regionizer exists before the first chunk holder. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements ServerLevelRegionAccess {

    @Unique
    private final LevelRegions leafs$regions = new LevelRegions(LeafsConfig.get());

    @Override
    public LevelRegions leafs$regions() {
        return leafs$regions;
    }
}
