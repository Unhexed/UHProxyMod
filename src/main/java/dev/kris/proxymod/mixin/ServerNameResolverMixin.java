package dev.kris.proxymod.mixin;

import dev.kris.proxymod.ProxyMod;
import net.minecraft.client.multiplayer.resolver.AddressCheck;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.util.Optional;

@Mixin(ServerNameResolver.class)
public abstract class ServerNameResolverMixin {
    @Shadow @Final private AddressCheck addressCheck;

    @Inject(method = "resolveAddress", at = @At("HEAD"), cancellable = true)
    private void proxyMod$remoteDns(ServerAddress address,
                                    CallbackInfoReturnable<Optional<ResolvedServerAddress>> callback) {
        if (!ProxyMod.config().settings().enabled()) {
            return;
        }
        if (!addressCheck.isAllowed(address)) {
            callback.setReturnValue(Optional.empty());
            return;
        }
        // Do not perform local A/AAAA or SRV lookups for the destination.
        InetSocketAddress unresolved = InetSocketAddress.createUnresolved(address.getHost(), address.getPort());
        callback.setReturnValue(Optional.of(new ResolvedServerAddress() {
            @Override public String getHostName() { return unresolved.getHostString(); }
            @Override public String getHostIp() { return unresolved.getHostString(); }
            @Override public int getPort() { return unresolved.getPort(); }
            @Override public InetSocketAddress asInetSocketAddress() { return unresolved; }
        }));
    }
}
