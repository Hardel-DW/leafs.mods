package fr.hardel.leafs.global;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import fr.hardel.leafs.Leafs;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;

/** Gamerules that cut per-tick barrier-window content. Both default to true (full vanilla behaviour). */
public final class LeafsGameRules {
    public static final GameRule<Boolean> tickFunctionsWork = createBoolean();
    public static final GameRule<Boolean> repeatingCommandBlocksWork = createBoolean();

    private LeafsGameRules() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.GAME_RULE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "tick_functions_work"), tickFunctionsWork);
        Registry.register(BuiltInRegistries.GAME_RULE, Identifier.fromNamespaceAndPath(Leafs.MOD_ID, "repeating_command_blocks_work"), repeatingCommandBlocksWork);
    }

    private static GameRule<Boolean> createBoolean() {
        return new GameRule<>(GameRuleCategory.MISC, GameRuleType.BOOL, BoolArgumentType.bool(), GameRuleTypeVisitor::visitBoolean,
            Codec.BOOL, value -> value ? 1 : 0, true, FeatureFlagSet.of());
    }
}
