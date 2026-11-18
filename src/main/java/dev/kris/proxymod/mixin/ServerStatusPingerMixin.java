package dev.kris.proxymod.mixin;

import dev.kris.proxymod.ProxyMod;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;

@Mixin(ServerStatusPinger.class)
public abstract class ServerStatusPingerMixin {
    @Inject(method = "pingLegacyServer", at = @At("HEAD"), cancellable = true)
    private void proxyMod$preventLegacyFallback(InetSocketAddress resolved, ServerAddress address,
                                                ServerData server, EventLoopGroupHolder eventLoops,
                                                CallbackInfo callback) {
        // Vanilla's pre-1.7 ping uses its own direct socket. It is unnecessary for 26.2 servers.
        if (ProxyMod.config().settings().enabled() || resolved.isUnresolved()) {
            callback.cancel();
        }
    }
}
