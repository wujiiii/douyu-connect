package io.github.douyuconnect;

import io.github.douyuconnect.config.*;
import io.github.douyuconnect.message.*;
import io.github.douyuconnect.protocol.*;
import io.github.douyuconnect.transport.NettyTransport;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class NettyIntegrationTest {
    @Test void realWebSocketLoginFragmentedReceiveSendAndCredentialReplacement() throws Exception {
        try (Server server = new Server(); DouyuClient client = new DouyuClient(ClientTest.options(), new NettyTransport(), event -> {})) {
            BlockingQueue<DouyuMessage> gifts = new LinkedBlockingQueue<>();
            client.subscribe(MessageFilter.categories(Category.GIFT,Category.PANDORA_BROADCAST), gifts::add);
            SenderConfig sender = new SenderConfig(ClientTest.sender("first").credentials(), List.of(server.uri()));
            ClientTest.await(client.connect("123", new ConnectionConfig(List.of(server.uri()), sender)));
            assertEquals(ConnectionState.READY, client.status("123").receive());
            assertEquals(ConnectionState.READY, client.status("123").send());
            DouyuMessage first = gifts.poll(2,TimeUnit.SECONDS), second = gifts.poll(2,TimeUnit.SECONDS);
            assertInstanceOf(GiftMessage.class, first); assertInstanceOf(PandoraBroadcastMessage.class, second);
            assertTrue(second.context().sequence() > first.context().sequence());
            assertEquals(SendResult.Status.ACKNOWLEDGED, ClientTest.await(client.sendChat("123", "测试😀/@S")).status());
            assertEquals("测试😀/@S", server.chats.poll(2,TimeUnit.SECONDS));
            String instance = client.status("123").roomInstanceId();
            ClientTest.await(client.updateSender("123", new SenderConfig(ClientTest.sender("second").credentials(), List.of(server.uri()))));
            assertEquals(instance,client.status("123").roomInstanceId());
            assertEquals(SendResult.Status.ACKNOWLEDGED, ClientTest.await(client.sendChat("123", "updated")).status());
            assertEquals(1,server.receiveLogins.get()); assertEquals(2,server.sendLogins.get());
            ClientTest.await(client.disconnect("123"));
            assertEquals(ConnectionState.DISCONNECTED,client.status("123").receive());
        }
    }

    static final class Server implements AutoCloseable {
        final EventLoopGroup group = new NioEventLoopGroup(1);
        final ChannelGroup channels = new DefaultChannelGroup(group.next());
        final BlockingQueue<String> chats = new LinkedBlockingQueue<>();
        final java.util.concurrent.atomic.AtomicInteger receiveLogins = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger sendLogins = new java.util.concurrent.atomic.AtomicInteger();
        final Channel server;
        Server() throws Exception {
            server = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
                protected void initChannel(SocketChannel channel) {
                    channels.add(channel);
                    channel.pipeline().addLast(new HttpServerCodec(),new HttpObjectAggregator(65536),new WebSocketServerProtocolHandler("/"),new Handler());
                }
            }).bind("127.0.0.1",0).sync().channel();
        }
        URI uri() { return URI.create("ws://127.0.0.1:" + ((InetSocketAddress)server.localAddress()).getPort() + "/"); }
        class Handler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {
            final PacketCodec.Decoder decoder = new PacketCodec.Decoder(4096);
            protected void channelRead0(ChannelHandlerContext context, BinaryWebSocketFrame frame) {
                for (String text : decoder.feed(frame.content().nioBuffer())) {
                    Map<String,String> fields = Stt.decode(text);
                    switch (fields.get("type")) {
                        case "loginreq" -> {
                            if (fields.containsKey("stk")) sendLogins.incrementAndGet(); else receiveLogins.incrementAndGet();
                            reply(context,"type@=loginres/userid@=123/");
                        }
                        case "joingroup" -> {
                            byte[] packet = PacketCodec.encode("type@=dgb/gfid@=0/pid@=123/hits@=3/",690);
                            context.write(new BinaryWebSocketFrame(false,0,Unpooled.wrappedBuffer(Arrays.copyOfRange(packet,0,8))));
                            context.writeAndFlush(new ContinuationWebSocketFrame(true,0,Unpooled.wrappedBuffer(Arrays.copyOfRange(packet,8,packet.length))));
                            reply(context,"type@=gbroadcast/btype@=pandora/txt5@=2个/");
                        }
                        case "chatmessage" -> { chats.add(fields.get("content")); reply(context,"type@=chatres/res@=0/"); }
                        case "mrkl", "keeplive" -> reply(context,"type@=mrkl/");
                        default -> { }
                    }
                }
            }
            void reply(ChannelHandlerContext context,String text) { context.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(PacketCodec.encode(text,690)))); }
        }
        public void close() {
            server.close().syncUninterruptibly(); channels.close().awaitUninterruptibly();
            group.shutdownGracefully(0,2,TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
}
