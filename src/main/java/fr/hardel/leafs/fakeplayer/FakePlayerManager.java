package fr.hardel.leafs.fakeplayer;

import com.mojang.authlib.GameProfile;
import fr.hardel.leafs.Leafs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Dev-only load bots: real ServerPlayers through the real login placement, ticked by their owning unit. */
public final class FakePlayerManager {
    private static final Map<UUID, BotState> bots = new ConcurrentHashMap<>();
    private static final AtomicInteger nextBotId = new AtomicInteger(1);
    private static final RandomSource random = RandomSource.create();

    private FakePlayerManager() {
    }

    public static int spawn(MinecraftServer server, int count, int spread, BotScenario forced) {
        ServerLevel overworld = server.overworld();
        for (int index = 0; index < count; index++) {
            String name = "Bot_" + nextBotId.getAndIncrement();
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("LeafsBot:" + name).getBytes(StandardCharsets.UTF_8)), name);
            ServerPlayer bot = new ServerPlayer(server, overworld, profile, ClientInformation.createDefault());
            BotScenario scenario = forced != null ? forced : BotScenario.values()[random.nextInt(BotScenario.values().length)];
            double x = random.nextInt(spread * 2 + 1) - spread + 0.5;
            double z = random.nextInt(spread * 2 + 1) - spread + 0.5;
            bots.put(profile.id(), new BotState(scenario, x, z));
            server.getPlayerList().placeNewPlayer(new FakeConnection(), bot, new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false));
            Leafs.LOGGER.info("Spawned {} at [{}, {}] running {}", name, (int) x, (int) z, scenario);
        }

        return count;
    }

    public static int clear(MinecraftServer server) {
        int removed = 0;
        for (UUID id : bots.keySet()) {
            ServerPlayer bot = server.getPlayerList().getPlayer(id);
            if (bot != null) {
                server.getPlayerList().remove(bot);
                removed++;
            }
        }
        bots.clear();

        return removed;
    }

    public static int count() {
        return bots.size();
    }

    /** Runs on the unit owning {@code level}, before its level tick. */
    public static void tickLevel(ServerLevel level) {
        for (Map.Entry<UUID, BotState> entry : bots.entrySet()) {
            ServerPlayer bot = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (bot == null || bot.level() != level) {
                continue;
            }

            BotState state = entry.getValue();
            if (!state.initialized) {
                initialize(bot, state);
                continue;
            }

            state.scenario.tick(bot, state, random);
        }
    }

    /** First tick after placement: move to the spread position and harden the bot. */
    private static void initialize(ServerPlayer bot, BotState state) {
        ServerLevel level = bot.level();
        if (level.getChunkSource().getChunkNow((int) state.spawnX >> 4, (int) state.spawnZ >> 4) == null) {
            level.getChunkSource().getChunk((int) state.spawnX >> 4, (int) state.spawnZ >> 4, true);
            return;
        }

        double floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(state.spawnX), (int) Math.floor(state.spawnZ));
        bot.snapTo(state.spawnX, floor, state.spawnZ, 0, 0);
        bot.setInvulnerable(true);
        AttributeInstance stepHeight = bot.getAttribute(Attributes.STEP_HEIGHT);
        if (stepHeight != null) {
            stepHeight.setBaseValue(20);
        }
        state.heading = random.nextDouble() * Math.PI * 2;
        state.initialized = true;
    }
}
