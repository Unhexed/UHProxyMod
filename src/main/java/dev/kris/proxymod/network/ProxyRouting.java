package dev.kris.proxymod.network;

import dev.kris.proxymod.config.ProxySettings;
import dev.kris.proxymod.config.ProxyType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.resolver.NoopAddressResolverGroup;

import java.net.InetSocketAddress;

public final class ProxyRouting {
    private ProxyRouting() { }

    public static ProxyHandler newHandler(ProxySettings settings) {
        settings.validate();
        InetSocketAddress proxy = new InetSocketAddress(settings.host(), settings.port());
        if (proxy.isUnresolved()) {
            throw new IllegalArgumentException("Could not resolve the proxy host. Check Proxy settings.");
        }
        boolean authenticated = !settings.username().isEmpty();
        ProxyHandler handler;
        if (settings.type() == ProxyType.SOCKS5) {
            handler = authenticated
                    ? new Socks5ProxyHandler(proxy, settings.username(), settings.password())
                    : new Socks5ProxyHandler(proxy);
        } else {
            handler = authenticated
                    ? new HttpProxyHandler(proxy, settings.username(), settings.password())
                    : new HttpProxyHandler(proxy);
        }
        handler.setConnectTimeoutMillis(settings.timeoutSeconds() * 1000L);
        return handler;
    }

    public static ChannelFuture connect(Bootstrap bootstrap, InetSocketAddress destination,
                                        ProxySettings settings) {
        // Capture one immutable settings snapshot for the entire connection attempt.
        ProxyHandler proxy = newHandler(settings);
        ChannelHandler original = bootstrap.config().handler();
        bootstrap.resolver(NoopAddressResolverGroup.INSTANCE);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, settings.timeoutSeconds() * 1000);
        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                channel.pipeline().addLast("proxy_mod", proxy);
                if (original != null) {
                    channel.pipeline().addLast("proxy_mod_vanilla_initializer", original);
                }
            }
        });
        ChannelFuture socket = bootstrap.connect(destination);
        // Bootstrap's future only waits for TCP. Minecraft must wait for the tunnel as well.
        ChannelPromise tunnel = socket.channel().newPromise();
        socket.addListener(result -> {
            if (!result.isSuccess()) {
                tunnel.tryFailure(result.cause());
            }
        });
        proxy.connectFuture().addListener(result -> {
            if (result.isSuccess()) {
                tunnel.trySuccess();
            } else {
                tunnel.tryFailure(result.cause());
            }
        });
        tunnel.addListener(result -> {
            if (result.isCancelled()) {
                socket.cancel(false);
                socket.channel().close();
            }
        });
        return tunnel;
    }
}
