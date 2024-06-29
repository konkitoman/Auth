package com.konkitoman.auth;

import com.konkitoman.auth.common.Auth;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public class FabricAuth implements DedicatedServerModInitializer, ServerPlayConnectionEvents.Join, ServerPlayConnectionEvents.Disconnect {

    @Override
    public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            Auth.registerCommands(dispatcher);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(Auth::onServerStarted);

        ServerTickEvents.START_SERVER_TICK.register((server) -> {
            Auth.onServerTick();
        });

        ServerPlayConnectionEvents.JOIN.register(this);
        ServerPlayConnectionEvents.DISCONNECT.register(this);
    }

    @Override
    public void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
        Auth.onPlayerReady(handler.getPlayer());
    }

    @Override
    public void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        Auth.onPlayerDisconnect(handler.getPlayer());
    }
}
