# Proxy Mod — Minecraft 26.2 / Fabric

A client mod that routes Minecraft multiplayer connections and modern server-list pings through a SOCKS5 or HTTP CONNECT proxy. Includes optional username/password authentication, remote destination DNS, an adjustable timeout, and an in-game settings screen. Fabric API and Mod Menu are not required.

## Install

1. Install **Minecraft Java Edition 26.2** with **Fabric Loader 0.20.1 or newer** and **Java 25**.
2. Copy `build/libs/proxy-mod-1.0.0+mc26.2.jar` into that instance's `mods` folder. Use the normal JAR, not the `-sources.jar`.
3. Launch Minecraft and choose **Multiplayer → Proxy: OFF** in the top right.
4. Select **SOCKS5** or **HTTP CONNECT**, enter your proxy host and port, and optionally enter credentials. The proxy host field takes a hostname or IP, without a URL scheme or port. IPv6 is supported.
5. Switch **Proxy: ON**, then **Save**. Join a server normally. Changes apply to new connections; use **Refresh** to update existing server-list ping results.

You need an existing proxy server. For example, an unauthenticated SOCKS5 tunnel listening locally would use host `127.0.0.1`, port `1080`, with both login fields empty. An HTTP proxy must permit CONNECT to your Minecraft server's port.

## Routing behavior

- Enabled proxy connections never retry directly. Authentication failures, rejected tunnels, and timeouts fail the connection.
- Minecraft passes the destination hostname to the proxy, without local A/AAAA or SRV lookups. **Servers that use DNS SRV records need their actual target hostname and port entered in Minecraft**, such as `play.example.net:25570`.
- The proxy server's own hostname is resolved locally. Use its IP address if you want to avoid that lookup too.
- The legacy pre-1.7 ping fallback is suppressed while enabled because it creates a separate direct socket.
- Applies to multiplayer TCP connections, including LAN servers joined by address, and modern server pings. Singleplayer's internal connection is unaffected.
- This is not a system-wide VPN. Microsoft/Mojang login, skins, capes, Realms HTTP requests, downloads, server resource packs, LAN discovery, and other mods' independent networking are outside its scope. A Realms gameplay connection using Minecraft's normal TCP connection is proxied.
- Supports SOCKS5 and plain HTTP CONNECT, with SOCKS username/password or HTTP Basic authentication. TLS connections to an HTTPS proxy, SOCKS4, UDP, proxy rotation, and per-server profiles are not implemented. The connection to the proxy itself is not encrypted by this mod.

## Configuration

Saved to the instance's `config/proxy-mod.json`:

```json
{
  "enabled": false,
  "type": "SOCKS5",
  "host": "127.0.0.1",
  "port": 1080,
  "username": "",
  "password": "",
  "timeoutSeconds": 10
}
```

`type` is `SOCKS5` or `HTTP`. Ports must be 1–65535 and timeouts 1–60 seconds. Edit the file while Minecraft is closed, or use the settings screen. Cancel and Escape discard unsaved changes. Draft fields survive window resizing.

Passwords are stored in plain text in this local file. Do not share it. The mod does not log credentials. Saves replace the file atomically where supported; POSIX systems restrict it to the owner, while Windows uses inherited directory permissions.

If an existing configuration is unreadable or invalid, the mod preserves the original file and blocks multiplayer connections. Open **Multiplayer → Proxy: ERROR** and save valid settings or switch the proxy off and save to recover. A missing configuration starts with the proxy disabled.

## Build and verify

Set `JAVA_HOME` to a **JDK 25**, then run:

```powershell
.\gradlew.bat build
```

On Linux/macOS, use `bash ./gradlew build`. The Gradle wrapper downloads Gradle 9.5.1 and checks its SHA-256. Dependencies are pinned in `gradle.properties`. The project uses unobfuscated Minecraft 26.2 names and Fabric Loom 1.17.21.

The build runs local integration tests for SOCKS5 and HTTP negotiation, authentication, IPv6 HTTP targets, remote DNS, byte transfer through the original pipeline, rejection, timeout, direct-fallback prevention, configuration persistence, corruption recovery, and validation. No public proxy or Minecraft server is needed for these tests.

```powershell
.\gradlew.bat runClient       # Development Minecraft client
.\gradlew.bat runSmokeTest    # Starts Minecraft, checks the GUI and actual mixins, then exits
```

The smoke test uses its own instance at `run/smoke-test`, synthetic local proxies, and disposable settings. It checks the Multiplayer button, opening the settings screen, minimum-size layout, resize preservation, invalid saves, save/cancel behavior, both actual game connection paths, remote DNS, and legacy ping suppression. A successful run writes `run/smoke-test/smoke-test-passed.txt` and `run/smoke-test/screenshots/proxy-settings.png`. The smoke-test mod is excluded from the distributable JAR. It requires a graphical environment and downloads Minecraft assets on its first run.

## Version references

- [Fabric's Minecraft 26.2 development notes](https://fabricmc.net/2026/06/15/262.html)
- [Fabric example mod, 26.2 branch](https://github.com/FabricMC/fabric-example-mod/tree/26.2)
- [Fabric Loader versions for 26.2](https://meta.fabricmc.net/v2/versions/loader/26.2)

MIT licensed. Not an official Minecraft product; not approved by or associated with Mojang or Microsoft.
