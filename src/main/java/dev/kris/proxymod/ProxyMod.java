package dev.kris.proxymod;

import dev.kris.proxymod.config.ConfigStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProxyMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("proxy_mod");
    private static final ConfigStore CONFIG = new ConfigStore(
            FabricLoader.getInstance().getConfigDir().resolve("proxy-mod.json"));

    @Override
    public void onInitializeClient() {
        CONFIG.load();
        if (CONFIG.loadError() != null) {
            LOGGER.error("Proxy configuration could not be read. Multiplayer is blocked until settings are saved.");
        }
        LOGGER.info("Proxy Mod ready. Configure it with the Proxy button in Multiplayer.");
    }

    public static ConfigStore config() {
        return CONFIG;
    }
}
