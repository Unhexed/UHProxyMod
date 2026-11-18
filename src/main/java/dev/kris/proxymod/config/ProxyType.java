package dev.kris.proxymod.config;

public enum ProxyType {
    SOCKS5("SOCKS5"),
    HTTP("HTTP CONNECT");

    private final String displayName;

    ProxyType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
