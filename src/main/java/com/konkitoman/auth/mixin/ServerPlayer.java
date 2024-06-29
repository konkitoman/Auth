package com.konkitoman.auth.mixin;

import com.konkitoman.auth.common.ServerPlayerSecret;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.server.level.ServerPlayer.class)
public class ServerPlayer implements ServerPlayerSecret {
    @Unique
    public String secret = null;

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    public void addAdditionalSaveData(CompoundTag compoundTag, CallbackInfo ci) {
        if (secret != null) {
            compoundTag.putString("secret", secret);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    public void readAdditionalSaveData(CompoundTag compoundTag, CallbackInfo ci) {
        if (compoundTag.contains("secret")) {
            secret = compoundTag.getString("secret");
        }
    }

    @Override
    public String login$getSecret() {
        return secret;
    }

    @Override
    public void login$setSecret(String value) {
        secret = value;
    }
}
