package dev.kris.proxymod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private volatile ProxySettings settings = ProxySettings.defaults();
    private volatile String loadError;

    public ConfigStore(Path path) {
        this.path = path;
    }

    public void load() {
        if (!Files.exists(path)) {
            return;
        }
        try {
            StoredSettings stored = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), StoredSettings.class);
            if (stored == null) {
                throw new IllegalArgumentException("Empty configuration");
            }
            ProxySettings loaded = stored.toSettings();
            loaded.validate();
            settings = loaded;
            loadError = null;
        } catch (IOException | RuntimeException exception) {
            // Preserve the broken file and block multiplayer until the user fixes or disables it.
            // Exception messages may contain file content, so never log them or display them.
            settings = new ProxySettings(true, ProxyType.SOCKS5, "", 1080, "", "", 10);
            loadError = "Could not load proxy settings. Save valid settings or turn the proxy off.";
        }
    }

    public ProxySettings settings() {
        return settings;
    }

    public String loadError() {
        return loadError;
    }

    public synchronized void save(ProxySettings updated) throws IOException {
        updated.validate();
        Path directory = path.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "proxy-mod-", ".tmp");
        try {
            if (Files.getFileAttributeView(temporary, PosixFileAttributeView.class) != null) {
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
            }
            Files.writeString(temporary, GSON.toJson(new StoredSettings(updated)) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            settings = updated;
            loadError = null;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static final class StoredSettings {
        boolean enabled = false;
        ProxyType type = ProxyType.SOCKS5;
        String host = "";
        int port = 1080;
        String username = "";
        String password = "";
        int timeoutSeconds = 10;

        StoredSettings() { }

        StoredSettings(ProxySettings settings) {
            enabled = settings.enabled();
            type = settings.type();
            host = settings.host();
            port = settings.port();
            username = settings.username();
            password = settings.password();
            timeoutSeconds = settings.timeoutSeconds();
        }

        ProxySettings toSettings() {
            return new ProxySettings(enabled, type, host, port, username, password, timeoutSeconds);
        }
    }
}
