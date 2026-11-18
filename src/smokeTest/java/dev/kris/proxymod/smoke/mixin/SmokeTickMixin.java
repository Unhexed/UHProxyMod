package dev.kris.proxymod.smoke.mixin;

import dev.kris.proxymod.smoke.SmokeHarness;

import net.minecraft.client.GameLoadCookie;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class SmokeTickMixin {
    @Inject(method = "onGameLoadFinished", at = @At("TAIL"))
    private void smoke$ready(GameLoadCookie cookie, CallbackInfo callback) {
        SmokeHarness.ready = true;
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void smoke$tick(boolean renderLevel, CallbackInfo callback) {
        SmokeHarness.tick((Minecraft) (Object) this);
    }
}
