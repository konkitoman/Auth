package com.konkitoman.auth.mixin;

import com.google.common.hash.Hashing;
import com.konkitoman.auth.Auth;
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

@Mixin(net.minecraft.network.protocol.game.ServerboundChatPacket.class)
public class ServerboundChatPacket {
    @Shadow @Final private String message;
//
//    @Shadow @Final private @Nullable MessageSignature signature;
//
//    @Shadow @Final private Instant timeStamp;
//
//    @Shadow @Final private long salt;
//
//    @Shadow @Final private LastSeenMessages.Update lastSeenMessages;
//z
    @Inject(method = "handle(Lnet/minecraft/network/protocol/game/ServerGamePacketListener;)V", at = @At("HEAD"), cancellable = true)
    public void handle(ServerGamePacketListener serverGamePacketListener, CallbackInfo ci) {

        ServerGamePacketListenerImpl impl = (ServerGamePacketListenerImpl) serverGamePacketListener;
        Auth.LOGGER.info("Message: {}", message);
        String playerName = impl.getPlayer().getName().getString();
        Auth.LOGGER.info("Player: {}", impl.getPlayer().getName().getString());
        if (Auth.AUTHORIZED.contains(impl.getPlayer().getName().getString())){
           return;
        }

        String hash = Hashing.sha256().hashString(message, Charset.defaultCharset()).toString();

        if (Auth.SECRETS.containsKey(playerName)){
            if (Auth.SECRETS.get(playerName).equals(hash)){
                Auth.TO_AUTHORIZE.add(playerName);
                impl.getPlayer().sendSystemMessage(Component.literal("Auth Successful!"));
            }else{
                impl.getPlayer().sendSystemMessage(Component.literal("Wrong Password!"));
            }
            ci.cancel();
            return;
        }

        Auth.SECRETS.put(playerName, hash);
        Auth.TO_AUTHORIZE.add(playerName);

        impl.getPlayer().sendSystemMessage(Component.literal("Registered Successful!"));

        ci.cancel();

    }
}
