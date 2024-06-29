package com.konkitoman.auth.common;

import com.google.common.hash.Hashing;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class Auth {
    public static final Logger LOGGER = LoggerFactory.getLogger("Auth");

    public static final ResourceKey<Level> AUTH = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("auth:auth"));
    public static final ResourceKey<DimensionType> AUTH_KEY = ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.parse("auth:auth"));

    public static MinecraftServer SERVER;
    public static HashSet<String> AUTHORIZED = new HashSet<>();
    public static ArrayList<String> TO_AUTHORIZE = new ArrayList<>();
    public static ArrayList<String> TO_FREEZE = new ArrayList<>();
    public static HashMap<String, String> SECRETS = new HashMap<>();
    public static HashMap<String, PlayerColdStorage> COLD_STORAGE = new HashMap<>();

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("auth")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reset").then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("old_password", StringArgumentType.word()).then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("new_password", StringArgumentType.word())
                        .executes(context -> {
                            String old_password = context.getArgument("old_password", String.class);
                            String new_password = context.getArgument("new_password", String.class);
                            ServerPlayer player = context.getSource().getPlayer();
                            ServerPlayerSecret pl = (ServerPlayerSecret) player;
                            if (pl.login$getSecret().equals(Hashing.sha256().hashString(old_password, Charset.defaultCharset()).toString())) {
                                pl.login$setSecret(Hashing.sha256().hashString(new_password, Charset.defaultCharset()).toString());
                                player.sendSystemMessage(Component.literal("Password was changed!"));
                            } else {
                                player.sendSystemMessage(Component.literal("Wrong password!"));
                            }
                            return 0;
                        })))));
    }

    public static void onServerStarted(MinecraftServer server) {
        SERVER = server;
        server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).get(AUTH_KEY);
        ServerLevel auth_level = server.getLevel(AUTH);
    }

    public static void onServerTick() {
        for (String playerName : TO_AUTHORIZE) {
            for (ServerPlayer player : SERVER.getPlayerList().getPlayers()) {
                if (player.getName().getString().equals(playerName)) {
                    AuthorizePlayer(player);
                    break;
                }
            }
        }
        TO_AUTHORIZE.clear();

        ServerLevel auth_level = SERVER.getLevel(AUTH);

        for (String playerName : TO_FREEZE) {
            for (ServerPlayer player : SERVER.getPlayerList().getPlayers()) {
                if (!player.getName().getString().equals(playerName)) {
                    continue;
                }

                auth_level.setBlock(new BlockPos(0, 80, 0), Blocks.AIR.defaultBlockState(), 0);
                auth_level.setBlock(new BlockPos(0, 81, 0), Blocks.AIR.defaultBlockState(), 0);
                auth_level.setBlock(new BlockPos(0, 79, 0), Blocks.BLACK_CONCRETE.defaultBlockState(), 0);
                auth_level.setBlock(new BlockPos(1, 80, 0), Blocks.BLACK_CONCRETE.defaultBlockState(), 0);
                auth_level.setBlock(new BlockPos(-1, 80, 0), Blocks.BLACK_CONCRETE.defaultBlockState(), 0);
                auth_level.setBlock(new BlockPos(0, 80, 1), Blocks.BLACK_CONCRETE.defaultBlockState(), 0);
                auth_level.setBlock(new BlockPos(0, 80, -1), Blocks.BLACK_CONCRETE.defaultBlockState(), 0);
                auth_level.setBlock(new BlockPos(1, 81, 0), Blocks.BLACK_CONCRETE.defaultBlockState(), 0);
                auth_level.setBlock(new BlockPos(-1, 81, 0), Blocks.BLACK_CONCRETE.defaultBlockState(), 0);
                auth_level.setBlock(new BlockPos(0, 81, 1), Blocks.BLACK_CONCRETE.defaultBlockState(), 0);
                auth_level.setBlock(new BlockPos(0, 81, -1), Blocks.BLACK_CONCRETE.defaultBlockState(), 0);
                auth_level.setBlock(new BlockPos(0, 82, 0), Blocks.BLACK_CONCRETE.defaultBlockState(), 0);

                player.setGameMode(GameType.ADVENTURE);
                player.setNoGravity(true);
                player.setInvulnerable(true);
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, true, false, false), player);
                player.getFoodData().setFoodLevel(20);
                player.setHealth(20);
                player.setAirSupply(player.getMaxAirSupply());
                player.teleportTo(auth_level, 0.5, 80.0, 0.5, 0.0f, 0.0f);

                break;
            }
        }
        TO_FREEZE.clear();
    }

    public static void AuthorizePlayer(ServerPlayer player) {
        String playerName = player.getName().getString();
        AUTHORIZED.add(playerName);
        if (COLD_STORAGE.containsKey(playerName)) {
            COLD_STORAGE.remove(playerName).Restore(player);
        }
    }

    public static void onPlayerReady(ServerPlayer player) {
        LOGGER.info("Player joined: {} with {}", player.getName().getString(), player.getUUID());

        String playerName = player.getName().getString();
        TO_FREEZE.add(playerName);

        if (!COLD_STORAGE.containsKey(player.getName().getString())) {
            COLD_STORAGE.put(playerName, new PlayerColdStorage(player));
            String secret = ((ServerPlayerSecret) player).login$getSecret();
            if (secret != null) {
                SECRETS.put(playerName, secret);
            }
            player.getInventory().clearContent();
        }

        if (SECRETS.containsKey(player.getName().getString())) {
            player.sendSystemMessage(Component.literal("You need to login, enter your password in chat!"));
        } else {
            player.sendSystemMessage(Component.literal("You need to register, enter you password in chat!"));
        }
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        String playerName = player.getName().getString();
        if (COLD_STORAGE.containsKey(playerName)) {
            COLD_STORAGE.remove(playerName).Restore(player);
        }
        AUTHORIZED.remove(playerName);
    }
}
