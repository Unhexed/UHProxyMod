package dev.kris.proxymod.mixin;

import dev.kris.proxymod.ProxyMod;
import dev.kris.proxymod.config.ProxySettings;
import dev.kris.proxymod.network.ProxyRouting;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.InetAddress;
import java.net.InetSocketAddress;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Redirect(
            method = "connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;",
            at = @At(value = "INVOKE", target = "Lio/netty/bootstrap/Bootstrap;connect(Ljava/net/InetAddress;I)Lio/netty/channel/ChannelFuture;")
    )
    private static ChannelFuture proxyMod$connect(Bootstrap bootstrap, InetAddress address, int port,
                                                  InetSocketAddress destination, EventLoopGroupHolder eventLoops,
                                                  Connection connection) {
        ProxySettings settings = ProxyMod.config().settings();
        if (!settings.enabled()) {
            return destination.isUnresolved()
                    ? bootstrap.connect(destination.getHostString(), destination.getPort())
                    : bootstrap.connect(address, port);
        }
        if (ProxyMod.config().loadError() != null) {
            throw new IllegalStateException("Proxy configuration is invalid. Open Multiplayer > Proxy and save settings.");
        }
        return ProxyRouting.connect(bootstrap, destination, settings);
    }
}
