package fr.hardel.leafs.gametest;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.function.Consumer;

@Mod(LeafsGameTests.MOD_ID)
public final class LeafsGameTests {
    static final String MOD_ID = "leafs_gametest";
    private static final List<Test> TESTS = List.of(
            new Test("the_test_chunk_is_owned_by_a_region", 20, RegionOwnershipTest::theTestChunkIsOwnedByARegion),
            new Test("mod_work_runs_on_the_player_queue", 40, PayloadWorkTest::modWorkRunsOnThePlayerQueue),
            new Test("a_cancelled_placement_primes_no_tnt", 20, CancelledPlacementTest::aCancelledPlacementPrimesNoTnt),
            new Test("every_listener_hears_its_invalidation", 20, CapabilityListenerTest::everyListenerHearsItsInvalidation),
            new Test("a_block_entity_of_a_full_chunk_loads_before_it_unloads", 20000, FreshBlockEntityTest::aBlockEntityOfAFullChunkLoadsBeforeItUnloads)
    );

    public LeafsGameTests(IEventBus modBus) {
        modBus.addListener((RegisterEvent event) -> {
            TESTS.forEach(test -> event.register(Registries.TEST_FUNCTION, test.id(), test::function));
            event.register(Registries.BLOCK, FreshBlockEntityTest.LIFECYCLE, FreshBlockEntityTest.LifecycleBlock::new);
            event.register(Registries.BLOCK_ENTITY_TYPE, FreshBlockEntityTest.LIFECYCLE,
                    () -> new BlockEntityType<>(FreshBlockEntityTest.LifecycleEntity::new, BuiltInRegistries.BLOCK.getValue(FreshBlockEntityTest.LIFECYCLE)));
        });
        modBus.addListener((RegisterGameTestsEvent event) -> {
            Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(MOD_ID, "default"));
            TESTS.forEach(test -> event.registerTest(test.id(), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, test.id()),
                    new TestData<>(environment, Identifier.withDefaultNamespace("empty"), test.maxTicks(), 0, true))));
        });
    }

    private record Test(String name, int maxTicks, Consumer<GameTestHelper> function) {
        Identifier id() {
            return Identifier.fromNamespaceAndPath(MOD_ID, name);
        }
    }
}
