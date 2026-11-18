package dev.kris.proxymod.network;

import dev.kris.proxymod.config.ProxySettings;
import dev.kris.proxymod.config.ProxyType;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class ProxyRoutingTest {
    private static final InetSocketAddress DESTINATION =
            InetSocketAddress.createUnresolved("only-at-proxy.invalid", 25565);
    private MultiThreadIoEventLoopGroup eventLoops;

    @BeforeEach
    void setUp() {
        eventLoops = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    }

    @AfterEach
    void tearDown() {
        eventLoops.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly();
    }

    @Test
    void socks5SendsHostnameToProxyAndPreservesApplicationPipeline() throws Exception {
        exerciseSocks(false);
    }

    @Test
    void socks5AuthenticatesAndTransfersData() throws Exception {
        exerciseSocks(true);
    }

    private void exerciseSocks(boolean authenticated) throws Exception {
        try (Fixture proxy = new Fixture(socket -> {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            OutputStream output = socket.getOutputStream();
            assertEquals(5, input.readUnsignedByte());
            byte[] methods = input.readNBytes(input.readUnsignedByte());
            int selectedMethod = authenticated ? 2 : 0;
            boolean offered = false;
            for (byte method : methods) offered |= method == selectedMethod;
            assertTrue(offered);
            output.write(new byte[]{5, (byte) selectedMethod});
            output.flush();
            if (authenticated) {
                assertEquals(1, input.readUnsignedByte());
                assertEquals("alice", readString(input));
                assertEquals("secret", readString(input));
                output.write(new byte[]{1, 0});
                output.flush();
            }
            assertEquals(5, input.readUnsignedByte());
            assertEquals(1, input.readUnsignedByte(), "Use SOCKS CONNECT");
            assertEquals(0, input.readUnsignedByte());
            assertEquals(3, input.readUnsignedByte(), "Proxy must receive the unresolved hostname");
            assertEquals(DESTINATION.getHostString(), readString(input));
            assertEquals(25565, input.readUnsignedShort());
            output.write(new byte[]{5, 0, 0, 1, 127, 0, 0, 1, 1, 2});
            output.flush();
            echoApplicationByte(input, output);
        })) {
            ProxySettings settings = settings(proxy.port(), ProxyType.SOCKS5, authenticated, 2);
            roundTrip(proxy, settings, DESTINATION);
        }
    }

    @Test
    void httpConnectUsesRemoteDns() throws Exception {
        exerciseHttp(false, DESTINATION);
    }

    @Test
    void httpConnectSendsBasicAuthentication() throws Exception {
        exerciseHttp(true, DESTINATION);
    }

    @Test
    void httpConnectFormatsIpv6Destination() throws Exception {
        exerciseHttp(false, InetSocketAddress.createUnresolved("2001:db8::42", 25565));
    }

    private void exerciseHttp(boolean authenticated, InetSocketAddress destination) throws Exception {
        try (Fixture proxy = new Fixture(socket -> {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            String request = readHeaders(input);
            String authority = destination.getHostString().contains(":")
                    ? "[" + destination.getHostString() + "]:25565"
                    : destination.getHostString() + ":25565";
            assertTrue(request.startsWith("CONNECT " + authority + " HTTP/1.1\r\n"), request);
            String expected = "Proxy-Authorization: Basic " + Base64.getEncoder().encodeToString(
                    "alice:secret".getBytes(StandardCharsets.UTF_8));
            if (authenticated) {
                assertTrue(request.toLowerCase().contains(expected.toLowerCase()));
            } else {
                assertFalse(request.toLowerCase().contains("proxy-authorization"));
            }
            OutputStream output = socket.getOutputStream();
            output.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            echoApplicationByte(input, output);
        })) {
            roundTrip(proxy, settings(proxy.port(), ProxyType.HTTP, authenticated, 2), destination);
        }
    }

    @Test
    void socksAuthenticationRejectionFailsConnection() throws Exception {
        try (Fixture proxy = new Fixture(socket -> {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            assertEquals(5, input.readUnsignedByte());
            input.readNBytes(input.readUnsignedByte());
            socket.getOutputStream().write(new byte[]{5, 2});
            assertEquals(1, input.readUnsignedByte());
            readString(input);
            readString(input);
            socket.getOutputStream().write(new byte[]{1, 1});
        })) {
            ChannelFuture connection = ProxyRouting.connect(bootstrap(new CompletableFuture<>()), DESTINATION,
                    settings(proxy.port(), ProxyType.SOCKS5, true, 2));
            try {
                assertTrue(connection.await(4, TimeUnit.SECONDS));
                assertFalse(connection.isSuccess());
                assertNotNull(connection.cause());
                proxy.completed.get(4, TimeUnit.SECONDS);
            } finally {
                connection.channel().close().syncUninterruptibly();
            }
        }
    }

    @Test
    void http407FailsConnection() throws Exception {
        try (Fixture proxy = new Fixture(socket -> {
            readHeaders(new DataInputStream(socket.getInputStream()));
            socket.getOutputStream().write(("HTTP/1.1 407 Proxy Authentication Required\r\n"
                    + "Content-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        })) {
            ChannelFuture connection = ProxyRouting.connect(bootstrap(new CompletableFuture<>()), DESTINATION,
                    settings(proxy.port(), ProxyType.HTTP, false, 2));
            try {
                assertTrue(connection.await(4, TimeUnit.SECONDS));
                assertFalse(connection.isSuccess());
                proxy.completed.get(4, TimeUnit.SECONDS);
            } finally {
                connection.channel().close().syncUninterruptibly();
            }
        }
    }

    @Test
    void unresponsiveProxyTimesOut() throws Exception {
        try (Fixture proxy = new Fixture(socket -> {
            // Read until the client closes on its handshake timeout; send no response.
            while (socket.getInputStream().read() != -1) { }
        })) {
            ChannelFuture connection = ProxyRouting.connect(bootstrap(new CompletableFuture<>()), DESTINATION,
                    settings(proxy.port(), ProxyType.SOCKS5, false, 1));
            try {
                assertTrue(connection.await(3, TimeUnit.SECONDS), "Handshake must respect the configured timeout");
                assertFalse(connection.isSuccess());
                assertTrue(connection.cause().getMessage().contains("timeout"));
                proxy.completed.get(4, TimeUnit.SECONDS);
            } finally {
                connection.channel().close().syncUninterruptibly();
            }
        }
    }

    @Test
    void cancellingPendingTunnelClosesProxySocket() throws Exception {
        CompletableFuture<Void> greetingReceived = new CompletableFuture<>();
        try (Fixture proxy = new Fixture(socket -> {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            assertEquals(5, input.readUnsignedByte());
            input.readNBytes(input.readUnsignedByte());
            greetingReceived.complete(null);
            while (input.read() != -1) { }
        })) {
            ChannelFuture connection = ProxyRouting.connect(bootstrap(new CompletableFuture<>()), DESTINATION,
                    settings(proxy.port(), ProxyType.SOCKS5, false, 5));
            try {
                greetingReceived.get(3, TimeUnit.SECONDS);
                assertFalse(connection.isDone(), "TCP alone must not complete the connection");
                assertTrue(connection.cancel(false));
                proxy.completed.get(3, TimeUnit.SECONDS);
                assertTrue(connection.channel().closeFuture().await(3, TimeUnit.SECONDS));
            } finally {
                connection.channel().close().syncUninterruptibly();
            }
        }
    }

    @Test
    void deadProxyNeverFallsBackToDirectDestination() throws Exception {
        try (ServerSocket destination = loopbackServer()) {
            int deadPort;
            try (ServerSocket reserved = loopbackServer()) {
                deadPort = reserved.getLocalPort();
            }
            ChannelFuture connection = ProxyRouting.connect(bootstrap(new CompletableFuture<>()),
                    new InetSocketAddress("127.0.0.1", destination.getLocalPort()),
                    settings(deadPort, ProxyType.SOCKS5, false, 1));
            try {
                assertTrue(connection.await(3, TimeUnit.SECONDS));
                assertFalse(connection.isSuccess());
                destination.setSoTimeout(250);
                assertThrows(SocketTimeoutException.class, destination::accept,
                        "No connection may reach the destination directly");
            } finally {
                connection.channel().close().syncUninterruptibly();
            }
        }
    }

    private void roundTrip(Fixture fixture, ProxySettings settings, InetSocketAddress destination) throws Exception {
        CompletableFuture<Integer> received = new CompletableFuture<>();
        ChannelFuture connection = ProxyRouting.connect(bootstrap(received), destination, settings);
        Channel channel = connection.channel();
        try {
            assertTrue(connection.await(4, TimeUnit.SECONDS));
            assertTrue(connection.isSuccess(), () -> "Connection failed: " + connection.cause());
            channel.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{42})).sync();
            assertEquals(43, received.get(4, TimeUnit.SECONDS));
            fixture.completed.get(4, TimeUnit.SECONDS);
        } finally {
            channel.close().syncUninterruptibly();
        }
    }

    private Bootstrap bootstrap(CompletableFuture<Integer> received) {
        return new Bootstrap().group(eventLoops).channel(NioSocketChannel.class)
                .handler(new SimpleChannelInboundHandler<ByteBuf>() {
                    @Override protected void channelRead0(ChannelHandlerContext context, ByteBuf bytes) {
                        received.complete((int) bytes.readUnsignedByte());
                    }
                    @Override public void exceptionCaught(ChannelHandlerContext context, Throwable error) {
                        received.completeExceptionally(error);
                        context.close();
                    }
                });
    }

    private static ProxySettings settings(int port, ProxyType type, boolean authenticated, int timeout) {
        return new ProxySettings(true, type, "127.0.0.1", port,
                authenticated ? "alice" : "", authenticated ? "secret" : "", timeout);
    }

    private static String readString(DataInputStream input) throws Exception {
        return new String(input.readNBytes(input.readUnsignedByte()), StandardCharsets.UTF_8);
    }

    private static void echoApplicationByte(DataInputStream input, OutputStream output) throws Exception {
        assertEquals(42, input.readUnsignedByte());
        output.write(43);
        output.flush();
    }

    private static String readHeaders(DataInputStream input) throws Exception {
        StringBuilder headers = new StringBuilder();
        while (headers.length() < 8192) {
            int value = input.read();
            if (value == -1) throw new EOFException("Incomplete proxy request");
            headers.append((char) value);
            if (headers.toString().endsWith("\r\n\r\n")) return headers.toString();
        }
        throw new IllegalArgumentException("Proxy request too large");
    }

    private static ServerSocket loopbackServer() throws Exception {
        return new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
    }

    @FunctionalInterface
    private interface Session { void handle(Socket socket) throws Exception; }

    private static final class Fixture implements AutoCloseable {
        private final ServerSocket server = loopbackServer();
        private final CompletableFuture<Void> completed = new CompletableFuture<>();
        private volatile Socket accepted;

        Fixture(Session session) throws Exception {
            Thread.ofVirtual().name("local-test-proxy").start(() -> {
                try (Socket socket = server.accept()) {
                    accepted = socket;
                    socket.setSoTimeout(4000);
                    session.handle(socket);
                    completed.complete(null);
                } catch (Throwable error) {
                    completed.completeExceptionally(error);
                }
            });
        }

        int port() { return server.getLocalPort(); }

        @Override public void close() throws Exception {
            server.close();
            if (accepted != null) accepted.close();
        }
    }
}
