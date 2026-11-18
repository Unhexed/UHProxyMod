package dev.kris.proxymod.mixin;

import dev.kris.proxymod.ProxyMod;
import dev.kris.proxymod.screen.ProxySettingsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    @Unique private Button proxyMod$button;

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void proxyMod$addButton(CallbackInfo callback) {
        proxyMod$button = addRenderableWidget(Button.builder(proxyMod$statusLabel(),
                button -> minecraft.gui.setScreen(new ProxySettingsScreen(this)))
                .bounds(width - 104, 5, 98, 20)
                .tooltip(Tooltip.create(Component.literal("Configure a SOCKS5 or HTTP CONNECT proxy")))
                .build());
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void proxyMod$moveButton(CallbackInfo callback) {
        if (proxyMod$button != null) {
            proxyMod$button.setMessage(proxyMod$statusLabel());
            proxyMod$button.setX(width - 104);
            proxyMod$button.setY(5);
        }
    }

    @Unique
    private Component proxyMod$statusLabel() {
        String status = ProxyMod.config().loadError() != null ? "ERROR"
                : ProxyMod.config().settings().enabled() ? "ON" : "OFF";
        return Component.literal("Proxy: " + status);
    }
}
