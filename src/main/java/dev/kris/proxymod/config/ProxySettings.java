package dev.kris.proxymod.config;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record ProxySettings(boolean enabled, ProxyType type, String host, int port,
                            String username, String password, int timeoutSeconds) {
    public static ProxySettings defaults() {
        return new ProxySettings(false, ProxyType.SOCKS5, "", 1080, "", "", 10);
    }

    public ProxySettings {
        Objects.requireNonNull(type, "Proxy type is required.");
        host = Objects.requireNonNull(host, "Proxy host is required.").strip();
        username = Objects.requireNonNull(username, "Username cannot be null.");
        password = Objects.requireNonNull(password, "Password cannot be null.");
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
    }

    public void validate() {
        if (enabled && host.isBlank()) {
            throw new IllegalArgumentException("Enter a proxy host before enabling the proxy.");
        }
        if (host.contains("://") || host.contains("/") || host.contains("@")
                || host.chars().anyMatch(Character::isWhitespace)
                || host.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Use a hostname or IP only, without a URL, login, or port.");
        }
        if (host.contains(":") && !io.netty.util.NetUtil.isValidIpV6Address(host)) {
            throw new IllegalArgumentException("Enter the port separately from the proxy host.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }
        if (timeoutSeconds < 1 || timeoutSeconds > 60) {
            throw new IllegalArgumentException("Timeout must be between 1 and 60 seconds.");
        }
        if (username.isEmpty() && !password.isEmpty()) {
            throw new IllegalArgumentException("Enter a username or clear the password.");
        }
        if (type == ProxyType.SOCKS5 && (utf8Length(username) > 255 || utf8Length(password) > 255)) {
            throw new IllegalArgumentException("SOCKS5 login fields must each fit in 255 UTF-8 bytes.");
        }
        if (type == ProxyType.SOCKS5 && !username.isEmpty() && password.isEmpty()) {
            throw new IllegalArgumentException("SOCKS5 authentication needs both a username and password.");
        }
        if (type == ProxyType.HTTP && username.contains(":")) {
            throw new IllegalArgumentException("An HTTP proxy username cannot contain a colon.");
        }
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    // Records normally print every field, which would expose proxy credentials.
    @Override
    public String toString() {
        return "ProxySettings[enabled=" + enabled + ", type=" + type + ", credentials=<redacted>]";
    }
}
