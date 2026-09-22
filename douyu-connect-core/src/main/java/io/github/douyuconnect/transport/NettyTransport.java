package io.github.douyuconnect.transport;
import java.net.URI;
import java.time.Duration;
import io.github.douyuconnect.protocol.PacketCodec;
import io.github.douyuconnect.config.TlsMode;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.*;
import javax.net.ssl.SSLParameters;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One shared NIO group per client; TLS uses system trust and verifies the endpoint hostname. */
public final class NettyTransport implements Transport {
    private final NioEventLoopGroup group;
    private final ChannelGroup channels;
    private final SslContext sslContext;
    private boolean closed;

    public NettyTransport() { this(2,TlsMode.SYSTEM_DEFAULT); }
    public NettyTransport(int threads) { this(threads,TlsMode.SYSTEM_DEFAULT); }
    public NettyTransport(TlsMode mode) { this(2,mode); }
    /** Select DOUYU_COMPATIBLE before any other JSSE/TLS initialization in this JVM. */
    public NettyTransport(int threads, TlsMode mode) {
        if (threads < 1) throw new IllegalArgumentException("threads must be positive");
        try { sslContext = TlsSupport.createContext(mode); }
        catch (javax.net.ssl.SSLException e) { throw new IllegalStateException("Cannot initialize TLS",e); }
        group = new NioEventLoopGroup(threads, (java.util.concurrent.ThreadFactory) task -> {
            Thread thread = new Thread(task,"douyu-io"); thread.setDaemon(true); return thread;
        });
        channels = new DefaultChannelGroup(group.next());
    }
    public synchronized Connection open(URI uri, Duration timeout, int max, Listener listener) {
        if (closed) throw new IllegalStateException("Transport closed");
        boolean tls = "wss".equals(uri.getScheme());
        int port = uri.getPort() >= 0 ? uri.getPort() : tls ? 443 : 80;
        Wire wire = new Wire(listener, max);
        Bootstrap bootstrap = new Bootstrap().group(group).channel(NioSocketChannel.class)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,(int)Math.min(Integer.MAX_VALUE,timeout.toMillis()))
            .option(ChannelOption.TCP_NODELAY,true)
            .handler(new ChannelInitializer<SocketChannel>() {
                protected void initChannel(SocketChannel channel) {
                    channels.add(channel);
                    if (tls) {
                        SslHandler ssl = sslContext.newHandler(channel.alloc(),uri.getHost(),port);
                        SSLParameters parameters = ssl.engine().getSSLParameters();
                        parameters.setEndpointIdentificationAlgorithm("HTTPS"); ssl.engine().setSSLParameters(parameters);
                        ssl.setHandshakeTimeoutMillis(timeout.toMillis());
                        channel.pipeline().addLast(ssl);
                    }
                    WebSocketClientProtocolConfig configuration = WebSocketClientProtocolConfig.newBuilder()
                        .webSocketUri(uri).handshakeTimeoutMillis(timeout.toMillis())
                        .maxFramePayloadLength(max + 4).build();
                    channel.pipeline().addLast(new HttpClientCodec(),new HttpObjectAggregator(65536),
                        new WebSocketClientProtocolHandler(configuration),new WebSocketFrameAggregator(max + 4),wire.handler());
                }
            });
        ChannelFuture connected = bootstrap.connect(uri.getHost(),port);
        wire.channel = connected.channel();
        connected.addListener(future -> { if (!future.isSuccess()) wire.notifyClosed(future.cause()); });
        return wire;
    }
    public void close() {
        synchronized (this) { if (closed) return; closed = true; }
        channels.close().awaitUninterruptibly();
        group.shutdownGracefully(0, 3, TimeUnit.SECONDS).syncUninterruptibly();
    }
    private static CompletableFuture<Void> adapt(ChannelFuture future) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        future.addListener(done -> { if (done.isSuccess()) result.complete(null); else result.completeExceptionally(done.cause()); });
        return result;
    }
    private static final class Wire implements Connection {
        private final Listener listener;
        private final PacketCodec.Decoder decoder;
        private final AtomicBoolean notified = new AtomicBoolean();
        private volatile Channel channel;
        Wire(Listener listener, int max) { this.listener = listener; decoder = new PacketCodec.Decoder(max); }
        public CompletionStage<Void> send(String text) {
            Channel current = channel;
            if (current == null || !current.isActive()) return CompletableFuture.failedFuture(new IllegalStateException("Channel unavailable"));
            return adapt(current.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(PacketCodec.encode(text,689)))));
        }
        public CompletionStage<Void> close() {
            Channel current = channel;
            return current == null ? CompletableFuture.completedFuture(null) : adapt(current.close());
        }
        void notifyClosed(Throwable error) { if (notified.compareAndSet(false,true)) listener.onClosed(error); }
        ChannelHandler handler() {
            return new SimpleChannelInboundHandler<WebSocketFrame>() {
                @Override public void userEventTriggered(ChannelHandlerContext context,Object event) throws Exception {
                    if (event == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) listener.onOpen();
                    else if (event == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT) {
                        notifyClosed(new TimeoutException("WebSocket handshake timeout")); context.close();
                    }
                    context.fireUserEventTriggered(event);
                }
                @Override protected void channelRead0(ChannelHandlerContext context,WebSocketFrame frame) {
                    if (frame instanceof BinaryWebSocketFrame) {
                        for (String text : decoder.feed(frame.content().nioBuffer())) listener.onMessage(text);
                    }
                }
                @Override public void channelInactive(ChannelHandlerContext context) throws Exception {
                    notifyClosed(null); context.fireChannelInactive();
                }
                @Override public void exceptionCaught(ChannelHandlerContext context,Throwable error) {
                    notifyClosed(error); context.close();
                }
            };
        }
    }
}
