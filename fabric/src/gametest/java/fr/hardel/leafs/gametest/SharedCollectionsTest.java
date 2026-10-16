package fr.hardel.leafs.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class SharedCollectionsTest {

    @GameTest
    public void theStaticCollectionOfAModIsShared(GameTestHelper helper) {
        SharedCollections.theStaticCollectionOfAModIsShared(helper);
    }
}
