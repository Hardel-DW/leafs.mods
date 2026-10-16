package fr.hardel.leafs.gametest;

import fixture.gametest.ModTickState;
import fr.hardel.shared.SharedCollection;
import net.minecraft.gametest.framework.GameTestHelper;

public final class SharedCollections {

    private SharedCollections() {
    }

    public static void theStaticCollectionOfAModIsShared(GameTestHelper helper) {
        helper.assertTrue(ModTickState.SEEN instanceof SharedCollection, "the static map of a mod is a %s".formatted(ModTickState.SEEN.getClass().getName()));
        helper.succeed();
    }
}
