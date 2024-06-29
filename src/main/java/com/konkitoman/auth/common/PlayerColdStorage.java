package com.konkitoman.auth.common;

import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelData;

import java.util.UUID;

import static com.konkitoman.auth.common.Auth.*;

public class PlayerColdStorage {
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
