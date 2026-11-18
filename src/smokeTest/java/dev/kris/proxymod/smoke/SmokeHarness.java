package dev.kris.proxymod.smoke;

import dev.kris.proxymod.ProxyMod;
import dev.kris.proxymod.config.ProxySettings;
import dev.kris.proxymod.config.ProxyType;
import dev.kris.proxymod.screen.ProxySettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.EventLoopGroupHolder;

import java.io.DataInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Only loaded by runSmokeTest; never included in the distributable mod. */
public final class SmokeHarness {
    public static boolean ready;
    private static int frame;
    private static CompletableFuture<Void> network;
    private static volatile boolean screenshotComplete;
    private static boolean finished;
    private static ProxySettingsScreen settingsScreen;

    public static void tick(Minecraft minecraft) {
        if (!ready || finished || minecraft.gui.overlay() != null) return;
        try {
            frame++;
            if (frame == 1) {
                Files.deleteIfExists(Path.of("smoke-test-passed.txt"));
                ProxyMod.config().save(ProxySettings.defaults());
                minecraft.gui.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
                click(minecraft.gui.screen(), "Proxy: OFF");
                check(minecraft.gui.screen() instanceof ProxySettingsScreen, "Proxy button did not open settings");
                settingsScreen = (ProxySettingsScreen) minecraft.gui.screen();
                settingsScreen.resize(320, 240);
                for (var child : settingsScreen.children()) {
                    if (child instanceof AbstractWidget widget) {
                        check(widget.getX() >= 0 && widget.getRight() <= 320, "Widget outside narrow screen");
                        check(widget.getY() >= 0 && widget.getBottom() <= 240, "Widget outside short screen");
                    }
                }
                field(settingsScreen, "Proxy host").setValue("127.0.0.1");
                settingsScreen.resize(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
                check(field(settingsScreen, "Proxy host").getValue().equals("127.0.0.1"), "Resize lost draft settings");
            } else if (frame == 4) {
                Screenshot.grab(minecraft.gameDirectory, "proxy-settings.png", minecraft.gameRenderer.mainRenderTarget(), 1,
                        message -> screenshotComplete = true);
                click(settingsScreen, "Proxy: OFF");
                field(settingsScreen, "Proxy host").setValue("");
                click(settingsScreen, "Save");
                check(minecraft.gui.screen() == settingsScreen, "Invalid configuration was accepted");
                check(!ProxyMod.config().settings().enabled(), "Invalid save changed live config");
                field(settingsScreen, "Proxy host").setValue("127.0.0.1");
                click(settingsScreen, "Save");
                check(ProxyMod.config().settings().enabled(), "Valid configuration was not saved");
                click(minecraft.gui.screen(), "Proxy: ON");
                Screen cancel = minecraft.gui.screen();
                click(cancel, "Proxy: ON");
                click(cancel, "Cancel");
                check(ProxyMod.config().settings().enabled(), "Cancel changed active settings");
                minecraft.gui.setScreen(new ProxySettingsScreen(new TitleScreen()));
                network = CompletableFuture.runAsync(() -> {
                    Thread.currentThread().setContextClassLoader(SmokeHarness.class.getClassLoader());
                    try {
                        verifyGameConnection(ProxyType.SOCKS5);
                        verifyGameConnection(ProxyType.HTTP);
                        verifyLegacyPingSuppressed();
                        ProxyMod.config().save(ProxySettings.defaults());
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
            } else if (network != null && network.isDone() && screenshotComplete) {
                network.join();
                Files.writeString(Path.of("smoke-test-passed.txt"),
                        "PASS: Minecraft 26.2 Fabric startup, all mixins, GUI open/save/cancel/resize, "
                                + "SOCKS5 and HTTP game connections, remote DNS, legacy ping suppression.\n");
                System.out.println("PROXY_MOD_SMOKE_TEST_PASSED");
                finished = true;
                minecraft.stop();
            }
            if (frame > 1800) throw new IllegalStateException("Smoke test timed out");
        } catch (Throwable error) {
            finished = true;
            error.printStackTrace();
            System.err.println("PROXY_MOD_SMOKE_TEST_FAILED");
            System.exit(1);
        }
    }

    private static void verifyGameConnection(ProxyType type) throws Exception {
        try (ServerSocket proxy = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            CompletableFuture<Void> handled = new CompletableFuture<>();
            Thread.ofVirtual().start(() -> {
                try (var socket = proxy.accept()) {
                    socket.setSoTimeout(5000);
                    DataInputStream input = new DataInputStream(socket.getInputStream());
                    var output = socket.getOutputStream();
                    if (type == ProxyType.SOCKS5) {
                        check(input.readUnsignedByte() == 5, "Not a SOCKS5 greeting");
                        input.readNBytes(input.readUnsignedByte());
                        output.write(new byte[]{5, 0});
                        check(input.readUnsignedByte() == 5, "Not a SOCKS5 request");
                        check(input.readUnsignedByte() == 1, "Not a CONNECT request");
                        input.readUnsignedByte();
                        check(input.readUnsignedByte() == 3, "Minecraft destination was resolved locally");
                        String host = new String(input.readNBytes(input.readUnsignedByte()), StandardCharsets.UTF_8);
                        check(host.equals("only-at-proxy.invalid"), "Incorrect SOCKS hostname");
                        check(input.readUnsignedShort() == 25565, "Incorrect destination port");
                        output.write(new byte[]{5, 0, 0, 1, 127, 0, 0, 1, 1, 2});
                    } else {
                        StringBuilder request = new StringBuilder();
                        while (!request.toString().endsWith("\r\n\r\n")) {
                            int value = input.read();
                            check(value >= 0 && request.length() < 8192, "Invalid HTTP CONNECT");
                            request.append((char) value);
                        }
                        check(request.toString().startsWith("CONNECT only-at-proxy.invalid:25565 HTTP/1.1"),
                                "Incorrect HTTP target");
                        output.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    }
                    output.flush();
                    while (input.read() != -1) { }
                    handled.complete(null);
                } catch (Throwable error) {
                    handled.completeExceptionally(error);
                }
            });
            ProxyMod.config().save(new ProxySettings(true, type, "127.0.0.1", proxy.getLocalPort(), "", "", 3));
            var resolved = ServerNameResolver.DEFAULT.resolveAddress(new ServerAddress("only-at-proxy.invalid", 25565));
            check(resolved.isPresent(), "Remote hostname rejected");
            InetSocketAddress destination = resolved.orElseThrow().asInetSocketAddress();
            check(destination.isUnresolved(), "Destination DNS did not remain remote");
            Connection connection = new Connection(PacketFlow.CLIENTBOUND);
            var future = Connection.connect(destination, EventLoopGroupHolder.remote(false), connection);
            try {
                check(future.await(6, TimeUnit.SECONDS), "Game connection timed out");
                check(future.isSuccess(), "Game connection failed: " + future.cause());
                check(connection.isConnected(), "Vanilla connection did not activate");
                check(((InetSocketAddress) future.channel().remoteAddress()).getPort() == proxy.getLocalPort(),
                        "Game socket did not connect to the proxy");
            } finally {
                future.channel().close().syncUninterruptibly();
            }
            handled.get(6, TimeUnit.SECONDS);
        }
    }

    private static void verifyLegacyPingSuppressed() throws Exception {
        try (ServerSocket target = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            target.setSoTimeout(250);
            var method = ServerStatusPinger.class.getDeclaredMethod("pingLegacyServer", InetSocketAddress.class,
                    ServerAddress.class, ServerData.class, EventLoopGroupHolder.class);
            method.setAccessible(true);
            method.invoke(new ServerStatusPinger(), new InetSocketAddress("127.0.0.1", target.getLocalPort()),
                    new ServerAddress("127.0.0.1", target.getLocalPort()),
                    new ServerData("Local smoke test", "127.0.0.1", ServerData.Type.OTHER),
                    EventLoopGroupHolder.remote(false));
            try (var unexpected = target.accept()) {
                throw new IllegalStateException("Legacy ping connected directly");
            } catch (java.net.SocketTimeoutException expected) { }
        }
    }

    private static EditBox field(Screen screen, String label) {
        return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(field -> field.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private static void click(Screen screen, String label) {
        Button button = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(candidate -> candidate.getMessage().getString().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing button: " + label));
        button.onPress(new KeyEvent(257, 0, 0));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
