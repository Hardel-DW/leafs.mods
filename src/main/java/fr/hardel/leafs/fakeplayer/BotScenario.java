package fr.hardel.leafs.fakeplayer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Dumb-on-purpose load behaviors; each ticks with the bot's owning unit, like a real player would. */
enum BotScenario {
    WANDER {
        @Override
        void tick(ServerPlayer player, BotState state, RandomSource random) {
            if (random.nextInt(60) == 0) {
                state.heading = random.nextDouble() * Math.PI * 2;
            }
            walk(player, state.heading, 0.2);
        }
    },
    ELYTRA {
        @Override
        void tick(ServerPlayer player, BotState state, RandomSource random) {
            if (random.nextInt(200) == 0) {
                state.heading = random.nextDouble() * Math.PI * 2;
            }
            double targetX = player.getX() + Math.cos(state.heading) * 1.8;
            double targetZ = player.getZ() + Math.sin(state.heading) * 1.8;
            ServerLevel level = player.level();
            if (level.getChunkSource().getChunkNow((int) targetX >> 4, (int) targetZ >> 4) == null) {
                return;
            }

            double floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) targetX, (int) targetZ);
            player.snapTo(targetX, Math.max(floor + 40, player.getY() - 0.5), targetZ, (float) Math.toDegrees(state.heading) - 90, 0);
        }
    },
    MINE {
        @Override
        void tick(ServerPlayer player, BotState state, RandomSource random) {
            walk(player, state.heading, 0.15);
            if (--state.cooldown > 0) {
                return;
            }

            state.cooldown = 8;
            state.heading += (random.nextDouble() - 0.5) * 0.6;
            ServerLevel level = player.level();
            BlockPos eye = player.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(eye.offset(-3, -1, -3), eye.offset(3, 2, 3))) {
                if (!level.getBlockState(pos).isAir() && level.getBlockState(pos).getDestroySpeed(level, pos) >= 0) {
                    level.destroyBlock(pos.immutable(), true, player);
                    return;
                }
            }
        }
    },
    FIGHT {
        @Override
        void tick(ServerPlayer player, BotState state, RandomSource random) {
            if (--state.cooldown > 0) {
                return;
            }

            state.cooldown = 10;
            Mob target = player.level().getEntitiesOfClass(Mob.class, AABB.ofSize(player.position(), 16, 8, 16)).stream().filter(Mob::isAlive).findFirst().orElse(null);
            if (target == null) {
                walk(player, random.nextDouble() * Math.PI * 2, 0.4);
                return;
            }

            if (player.distanceTo(target) > 3) {
                walk(player, Math.atan2(target.getZ() - player.getZ(), target.getX() - player.getX()), 0.3);
            } else {
                player.attack(target);
            }
        }
    },
    SPAWNER {
        @Override
        void tick(ServerPlayer player, BotState state, RandomSource random) {
            if (--state.cooldown > 0) {
                return;
            }

            state.cooldown = 20;
            ServerLevel level = player.level();
            int nearby = level.getEntitiesOfClass(Mob.class, AABB.ofSize(player.position(), 96, 64, 96)).size();
            for (int spawned = 0; spawned < 10 && nearby + spawned < 300; spawned++) {
                BlockPos pos = player.blockPosition().offset(random.nextInt(17) - 8, 0, random.nextInt(17) - 8);
                EntityTypes.ZOMBIE.spawn(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos), EntitySpawnReason.COMMAND);
            }
        }
    },
    DIMENSIONS {
        @Override
        void tick(ServerPlayer player, BotState state, RandomSource random) {
            walk(player, state.heading, 0.2);
            if (--state.cooldown > 0) {
                return;
            }

            state.cooldown = 300;
            state.heading = random.nextDouble() * Math.PI * 2;
            List<ServerLevel> levels = new ArrayList<>();
            player.level().getServer().getAllLevels().forEach(levels::add);
            ServerLevel target = levels.get(++state.dimensionIndex % levels.size());
            if (target == player.level()) {
                return;
            }

            player.teleport(new TeleportTransition(target, new Vec3(state.spawnX, 128, state.spawnZ), Vec3.ZERO, player.getYRot(), 0, TeleportTransition.DO_NOTHING));
        }
    };

    abstract void tick(ServerPlayer player, BotState state, RandomSource random);

    static void walk(ServerPlayer player, double heading, double speed) {
        double targetX = player.getX() + Math.cos(heading) * speed;
        double targetZ = player.getZ() + Math.sin(heading) * speed;
        ServerLevel level = player.level();
        if (level.getChunkSource().getChunkNow((int) targetX >> 4, (int) targetZ >> 4) == null) {
            return;
        }

        double floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(targetX), (int) Math.floor(targetZ));
        player.snapTo(targetX, floor, targetZ, (float) Math.toDegrees(heading) - 90, 0);
    }
}
