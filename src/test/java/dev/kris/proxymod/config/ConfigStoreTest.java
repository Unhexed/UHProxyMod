package dev.kris.proxymod.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {
    @TempDir Path directory;

    @Test
    void missingConfigStartsDisabled() {
        ConfigStore store = new ConfigStore(directory.resolve("proxy-mod.json"));
        store.load();
        assertEquals(ProxySettings.defaults(), store.settings());
        assertNull(store.loadError());
    }

    @Test
    void credentialsAndSettingsSurviveRestart() throws IOException {
        Path file = directory.resolve("config/proxy-mod.json");
        ProxySettings expected = new ProxySettings(true, ProxyType.SOCKS5,
                " [::1] ", 1080, "alice", "secret", 15);
        ConfigStore first = new ConfigStore(file);
        first.save(expected);
        ConfigStore restarted = new ConfigStore(file);
        restarted.load();
        assertEquals(expected, restarted.settings());
        assertEquals("::1", restarted.settings().host());
        assertFalse(restarted.settings().toString().contains("secret"));
        assertFalse(restarted.settings().toString().contains("alice"));
        assertNull(restarted.loadError());
        try (var files = Files.list(file.getParent())) {
            assertEquals(1, files.count(), "No temporary credential files should remain");
        }
    }

    @Test
    void partialConfigUsesDocumentedDefaults() throws IOException {
        Path file = directory.resolve("proxy-mod.json");
        Files.writeString(file, "{\"enabled\":true,\"host\":\"127.0.0.1\"}");
        ConfigStore store = new ConfigStore(file);
        store.load();
        assertTrue(store.settings().enabled());
        assertEquals(1080, store.settings().port());
        assertEquals(10, store.settings().timeoutSeconds());
        assertNull(store.loadError());
    }

    @Test
    void corruptConfigIsPreservedAndBlocksConnectionsUntilSaved() throws IOException {
        Path file = directory.resolve("proxy-mod.json");
        String broken = "{\"password\":\"do-not-print-this\", bad json";
        Files.writeString(file, broken);
        ConfigStore store = new ConfigStore(file);
        store.load();
        assertTrue(store.settings().enabled(), "Corrupt config must never fall back to direct connections");
        assertNotNull(store.loadError());
        assertFalse(store.loadError().contains("do-not-print-this"));
        assertEquals(broken, Files.readString(file));
        store.save(ProxySettings.defaults());
        assertFalse(store.settings().enabled());
        assertNull(store.loadError());
    }

    @Test
    void invalidConfigIsAlsoBlocked() throws IOException {
        Path file = directory.resolve("proxy-mod.json");
        Files.writeString(file, "{\"enabled\":true,\"host\":\"proxy.invalid\",\"port\":0}");
        ConfigStore store = new ConfigStore(file);
        store.load();
        assertTrue(store.settings().enabled());
        assertNotNull(store.loadError());
    }

    @Test
    void failedSaveDoesNotChangeActiveSettings() throws IOException {
        Path occupied = directory.resolve("occupied");
        Files.writeString(occupied, "file, not a directory");
        ConfigStore store = new ConfigStore(occupied.resolve("proxy-mod.json"));
        ProxySettings updated = new ProxySettings(true, ProxyType.HTTP, "localhost", 3128, "", "", 5);
        assertThrows(IOException.class, () -> store.save(updated));
        assertEquals(ProxySettings.defaults(), store.settings());
    }

    @Test
    void validatesHostsPortsTimeoutsAndProtocolCredentials() {
        assertInvalid("http://localhost", 1080, "", "", 10);
        assertInvalid("localhost:1080", 1080, "", "", 10);
        assertInvalid("user@localhost", 1080, "", "", 10);
        assertInvalid("", 1080, "", "", 10);
        assertInvalid("localhost", 0, "", "", 10);
        assertInvalid("localhost", 65536, "", "", 10);
        assertInvalid("localhost", 1080, "", "", 0);
        assertInvalid("localhost", 1080, "", "", 61);
        assertInvalid("localhost", 1080, "", "secret", 10);
        assertInvalid("localhost", 1080, "alice", "", 10);
        assertInvalid("localhost", 1080, "é".repeat(128), "secret", 10);
        assertThrows(IllegalArgumentException.class, () -> new ProxySettings(true, ProxyType.HTTP,
                "localhost", 3128, "alice:bob", "secret", 10).validate());
        assertDoesNotThrow(() -> new ProxySettings(true, ProxyType.SOCKS5,
                "[2001:db8::1]", 1080, "", "", 10).validate());
    }

    private void assertInvalid(String host, int port, String username, String password, int timeout) {
        assertThrows(IllegalArgumentException.class, () -> new ProxySettings(true, ProxyType.SOCKS5,
                host, port, username, password, timeout).validate());
    }
}
