package com.konkitoman.auth.mixin;

import com.konkitoman.auth.Auth;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.network.protocol.game.ServerboundChatCommandPacket.class)
public abstract class ServerboundChatCommandPacket {

    @Inject(method = "handle(Lnet/minecraft/network/protocol/game/ServerGamePacketListener;)V", at = @At("HEAD"), cancellable = true)
    public void handle(ServerGamePacketListener serverGamePacketListener, CallbackInfo ci) {
        ServerGamePacketListenerImpl impl = (ServerGamePacketListenerImpl) serverGamePacketListener;
        String playerName = impl.getPlayer().getName().getString();
        if (Auth.AUTHORIZED.contains(playerName)) {
            return;
        }

        impl.getPlayer().sendSystemMessage(Component.literal("You are not Authorized!"));
        ci.cancel();
    }
}
