package com.konkitoman.auth.mixin;

import com.google.common.hash.Hashing;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.Charset;

import static com.konkitoman.auth.common.Auth.*;

@Mixin(net.minecraft.network.protocol.game.ServerboundChatPacket.class)
public class ServerboundChatPacket {
    @Shadow
    @Final
    private String message;

    @Inject(method = "handle(Lnet/minecraft/network/protocol/game/ServerGamePacketListener;)V", at = @At("HEAD"), cancellable = true)
    public void handle(ServerGamePacketListener serverGamePacketListener, CallbackInfo ci) {
        ServerGamePacketListenerImpl impl = (ServerGamePacketListenerImpl) serverGamePacketListener;
        String playerName = impl.getPlayer().getName().getString();
        if (AUTHORIZED.contains(impl.getPlayer().getName().getString())) {
            return;
        }

        String hash = Hashing.sha256().hashString(message, Charset.defaultCharset()).toString();

        if (SECRETS.containsKey(playerName)) {
            if (SECRETS.get(playerName).equals(hash)) {
                TO_AUTHORIZE.add(playerName);
                impl.getPlayer().sendSystemMessage(Component.literal("Auth Successful!"));
            } else {
                impl.getPlayer().sendSystemMessage(Component.literal("Wrong Password!"));
            }
            ci.cancel();
            return;
        }

        SECRETS.put(playerName, hash);
        TO_AUTHORIZE.add(playerName);

        impl.getPlayer().sendSystemMessage(Component.literal("Registered Successful!"));

        ci.cancel();

    }
}
