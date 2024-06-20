package com.konkitoman.auth;

import com.google.common.hash.Hashing;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.serialization.Dynamic;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.*;

public class Auth implements DedicatedServerModInitializer, ServerPlayConnectionEvents.Join, ServerPlayConnectionEvents.Disconnect, ServerPlayConnectionEvents.Init {
    public static final Logger LOGGER = LoggerFactory.getLogger("com.konkito.auth");

    public static final ResourceKey<Level> AUTH = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("auth:auth"));
    public static final ResourceKey<DimensionType> AUTH_KEY = ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.parse("auth:auth"));

    public static MinecraftServer SERVER;
    public static HashSet<String> AUTHORIZED = new HashSet<>();
    public static ArrayList<String> TO_AUTHORIZE = new ArrayList<>();
    public static ArrayList<String> TO_FREEZE = new ArrayList<>();
    public static HashMap<String, String> SECRETS = new HashMap<>();
    public static HashMap<String, PlayerColdStorage> COLD_STORAGE = new HashMap<>();


    public static class PlayerColdStorage {
        CompoundTag data;

        public PlayerColdStorage(ServerPlayer player) {
            data = player.saveWithoutId(new CompoundTag());
        }

        public void Restore(ServerPlayer player) {
            PlayerList playerList = SERVER.getPlayerList();

            player.removeAllEffects();

            player.load(data);
            ResourceKey<Level> resourceKey = DimensionType.parseLegacy(new Dynamic<Tag>(NbtOps.INSTANCE, data.get("Dimension"))).resultOrPartial(LOGGER::error).orElse(Level.OVERWORLD);
            ServerLevel serverLevel = SERVER.getLevel(resourceKey);
            ServerLevel level;
            if (serverLevel == null) {
                LOGGER.warn("Unknown respawn dimension {}, defaulting to overworld", (Object) resourceKey);
                level = SERVER.overworld();
            } else {
                level = serverLevel;
            }

            player.teleportTo(level, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            LevelData levelData = level.getLevelData();
            player.loadGameTypes(data);
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, player.gameMode.getGameModeForPlayer().getId()));
            player.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
            player.connection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
            player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
            player.connection.send(new ClientboundUpdateRecipesPacket(SERVER.getRecipeManager().getOrderedRecipes()));
            playerList.sendActivePlayerEffects(player);
            playerList.sendAllPlayerInfo(player);

            CompoundTag compoundTag2;
            Entity entity2;
            if (data.contains("RootVehicle", 10) && (entity2 = EntityType.loadEntityRecursive((compoundTag2 = data.getCompound("RootVehicle")).getCompound("Entity"), level, entity -> {
                if (!level.addWithUUID((Entity) entity)) {
                    return null;
                }
                return entity;
            })) != null) {
                UUID uUID = compoundTag2.hasUUID("Attach") ? compoundTag2.getUUID("Attach") : null;
                if (entity2.getUUID().equals(uUID)) {
                    player.startRiding(entity2, true);
                } else {
                    for (Entity entity22 : entity2.getIndirectPassengers()) {
                        if (!entity22.getUUID().equals(uUID)) continue;
                        player.startRiding(entity22, true);
                        break;
                    }
                }
                if (!player.isPassenger()) {
                    LOGGER.warn("Couldn't reattach entity to player");
                    entity2.discard();
                    for (Entity entity22 : entity2.getIndirectPassengers()) {
                        entity22.discard();
                    }
                }
            }
            player.initInventoryMenu();
            ((ServerPlayerSecret) player).login$setSecret(SECRETS.get(player.getName().getString()));
        }

    }

    @Override
    public void onInitializeServer() {
        LOGGER.info("Konkito Man auth system was registered!");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("auth")
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reset")
                            .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("old_password", StringArgumentType.word())
                                    .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("new_password", StringArgumentType.word())
                                            .executes(context -> {
                                                String old_password = context.getArgument("old_password", String.class);
                                                String new_password = context.getArgument("new_password", String.class);
                                                ServerPlayer player = context.getSource().getPlayer();
                                                ServerPlayerSecret pl = (ServerPlayerSecret) player;
                                                if (pl.login$getSecret().equals( Hashing.sha256().hashString(old_password, Charset.defaultCharset()).toString())){
                                                    pl.login$setSecret(Hashing.sha256().hashString(new_password, Charset.defaultCharset()).toString());
                                                    player.sendSystemMessage(Component.literal("Password was changed!"));
                                                }else{
                                                    player.sendSystemMessage(Component.literal("Wrong password!"));
                                                }
                                                return 0;
                                            })))));


        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SERVER = server;
            server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).get(AUTH_KEY);
            ServerLevel auth_level = server.getLevel(AUTH);
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
        });

        ServerTickEvents.START_SERVER_TICK.register((server) -> {
            for (String playerName : TO_AUTHORIZE) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.getName().getString().equals(playerName)) {
                        Auth.AuthorizePlayer(player);
                        break;
                    }
                }
            }
            TO_AUTHORIZE.clear();

            ServerLevel auth_level = server.getLevel(AUTH);

            for (String playerName : TO_FREEZE) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (!player.getName().getString().equals(playerName)) {
                        continue;
                    }

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
        });

        ServerPlayConnectionEvents.JOIN.register(this);
        ServerPlayConnectionEvents.DISCONNECT.register(this);
        ServerPlayConnectionEvents.INIT.register(this);

    }

    public static void AuthorizePlayer(ServerPlayer player) {
        String playerName = player.getName().getString();
        AUTHORIZED.add(playerName);
        if (COLD_STORAGE.containsKey(playerName)) {
            COLD_STORAGE.remove(playerName).Restore(player);
        }
    }

    @Override
    public void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
        ServerPlayer player = handler.getPlayer();
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

    @Override
    public void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        String playerName = handler.getPlayer().getName().getString();
        if (COLD_STORAGE.containsKey(playerName)) {
            COLD_STORAGE.remove(playerName).Restore(handler.getPlayer());
        }
        AUTHORIZED.remove(playerName);
    }

    @Override
    public void onPlayInit(ServerGamePacketListenerImpl handler, MinecraftServer server) {

    }
}
