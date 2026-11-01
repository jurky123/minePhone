package dev.browserlink.bridge;

import com.google.gson.*;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Client-local transport. No packets or credentials are sent to a Minecraft server. */
public final class BridgeServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 19285;
    public static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;
    public interface Listener {
        void connected();
        void disconnected();
        void frame(byte[] png);
        void status(String text);
        default void browserState(JsonObject state) {}
        default void clipboard(String text) {}
        default void audio(int tabId,byte[] pcm) {}
    }
    private volatile boolean worldActive = true;
    private final Listener listener;
    private final EventLoopGroup boss = new NioEventLoopGroup(1, daemonFactory("browserlink-accept"));
    private final EventLoopGroup worker = new NioEventLoopGroup(1, daemonFactory("browserlink-io"));
    private final AtomicReference<Channel> peer = new AtomicReference<>();
    private Channel server;
    public BridgeServer(Listener listener) { this.listener = listener; }
    private static java.util.concurrent.ThreadFactory daemonFactory(String name) {
        return task -> { Thread t = new Thread(task, name); t.setDaemon(true); return t; };
    }
    public int start(int port) throws InterruptedException {
        server = new ServerBootstrap().group(boss, worker).channel(NioServerSocketChannel.class)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override protected void initChannel(SocketChannel channel) {
                    channel.pipeline().addLast(new IdleStateHandler(45, 0, 0));
                    channel.pipeline().addLast(new HttpServerCodec());
                    channel.pipeline().addLast(new HttpObjectAggregator(8192));
                    channel.pipeline().addLast(new OriginGuard());
                    channel.pipeline().addLast(new WebSocketServerProtocolHandler("/browserlink", null, false, MAX_FRAME_BYTES));
                    channel.pipeline().addLast(new WebSocketFrameAggregator(MAX_FRAME_BYTES));
                    channel.pipeline().addLast(new Connection());
                }
            }).bind(new InetSocketAddress("127.0.0.1", port)).sync().channel();
        return ((InetSocketAddress) server.localAddress()).getPort();
    }
    public boolean isConnected() { Channel c = peer.get(); return c != null && c.isActive(); }
    public void send(JsonObject message) {
        Channel c = peer.get();
        if (c != null && c.isActive() && c.isWritable()) c.writeAndFlush(new TextWebSocketFrame(message.toString()));
    }
    public void disconnect() { closePeer(1000,"Disconnected by user"); }
    public void setWorldActive(boolean active) {
        boolean changed=worldActive!=active; worldActive=active;
        if (changed && !active) closePeer(1013,"Waiting for a Minecraft world");
    }
    private void closePeer(int code,String reason) {
        Channel c=peer.get();
        if(c!=null) c.writeAndFlush(new CloseWebSocketFrame(code,reason)).addListener(ChannelFutureListener.CLOSE);
    }
    @Override public void close() {
        closePeer(1001,"Game shutting down");
        if (server != null) server.close();
        boss.shutdownGracefully(0, 2, TimeUnit.SECONDS);
        worker.shutdownGracefully(0, 2, TimeUnit.SECONDS);
    }
    private static final class OriginGuard extends ChannelInboundHandlerAdapter {
        @Override public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpRequest request) {
                String origin = request.headers().get(HttpHeaderNames.ORIGIN, "");
                if (!origin.equals(BrowserIdentity.ORIGIN) || !request.uri().equals("/browserlink")) {
                    io.netty.util.ReferenceCountUtil.release(msg);
                    ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN)).addListener(ChannelFutureListener.CLOSE);
                    return;
                }
            }
            ctx.fireChannelRead(msg);
        }
    }
    private final class Connection extends SimpleChannelInboundHandler<WebSocketFrame> {
        private boolean authenticated;
        @Override public void channelActive(ChannelHandlerContext ctx) {
            ctx.executor().schedule(() -> { if (!authenticated) ctx.close(); }, 5, TimeUnit.SECONDS);
            ctx.fireChannelActive();
        }
        @Override protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
            try {
                if (frame instanceof TextWebSocketFrame text) {
                    if (text.content().readableBytes() > 512 * 1024) { ctx.close(); return; }
                    JsonObject msg = JsonParser.parseString(text.text()).getAsJsonObject();
                    String type = msg.get("type").getAsString();
                    if (!authenticated) {
                        if (!type.equals("hello") || !msg.has("protocol") || msg.get("protocol").getAsInt()!=2) {
                            ctx.writeAndFlush(new CloseWebSocketFrame(1008, "Update BrowserLink extension to 0.2.0")).addListener(ChannelFutureListener.CLOSE);
                            return;
                        }
                        if (!worldActive) {
                            ctx.writeAndFlush(new CloseWebSocketFrame(1013,"Waiting for a Minecraft world")).addListener(ChannelFutureListener.CLOSE);
                            return;
                        }
                        if (!peer.compareAndSet(null, ctx.channel())) {
                            ctx.writeAndFlush(new CloseWebSocketFrame(1013, "Another browser is connected; retry shortly")).addListener(ChannelFutureListener.CLOSE);
                            return;
                        }
                        authenticated = true;
                        listener.connected();
                        ctx.writeAndFlush(new TextWebSocketFrame("{\"type\":\"ready\",\"protocol\":2,\"jpeg\":true}"));
                    } else if (type.equals("status") && msg.has("message")) {
                        String status = msg.get("message").getAsString();
                        listener.status(status.substring(0, Math.min(status.length(), 180)));
                    } else if (type.equals("ping")) {
                        ctx.writeAndFlush(new TextWebSocketFrame("{\"type\":\"pong\"}"));
                    } else if (type.equals("capture-stats") && Boolean.getBoolean("browserlink.benchmark")) {
                        var clean=new JsonObject();
                        for(String key:new String[]{"consumeFps","outputFps","drawMs","encodeMs","frameMs","timestampGapMs","workerDispatchMs","buffered"}){
                            if(msg.has(key)&&msg.get(key).isJsonPrimitive()&&msg.get(key).getAsJsonPrimitive().isNumber()){
                                double v=msg.get(key).getAsDouble();if(Double.isFinite(v))clean.addProperty(key,v);
                            }
                        }
                        com.mojang.logging.LogUtils.getLogger().info("BrowserLink capture: {}",clean);
                    } else if (type.equals("clipboard") && msg.has("text")) {
                        String value=msg.get("text").getAsString();listener.clipboard(value.substring(0,Math.min(value.length(),65536)));
                    } else if (type.equals("browser-state")) {
                        listener.browserState(msg);
                    }
                } else if (frame instanceof BinaryWebSocketFrame binary) {
                    if (!authenticated) { ctx.close(); return; }
                    if(AudioPacket.valid(binary.content())){
                        ByteBuf b=binary.content();int start=b.readerIndex();byte[] pcm=new byte[b.readableBytes()-16];b.getBytes(start+16,pcm);listener.audio(b.getIntLE(start+12),pcm);return;
                    }
                    if(!validPng(binary.content())&&!validJpeg(binary.content())){
                        com.mojang.logging.LogUtils.getLogger().warn("BrowserLink dropped invalid/oversized image ({} bytes)",binary.content().readableBytes());
                        return;
                    }
                    byte[] bytes = new byte[binary.content().readableBytes()];
                    binary.content().getBytes(binary.content().readerIndex(), bytes);
                    listener.frame(bytes);
                }
            } catch (RuntimeException invalid) { com.mojang.logging.LogUtils.getLogger().warn("BrowserLink protocol error: {}",invalid.toString());ctx.close(); }
        }
        @Override public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
            if (event instanceof IdleStateEvent) {com.mojang.logging.LogUtils.getLogger().info("BrowserLink connection idle timeout");ctx.close();} else ctx.fireUserEventTriggered(event);
        }
        @Override public void channelInactive(ChannelHandlerContext ctx) {
            if (peer.compareAndSet(ctx.channel(), null)) listener.disconnected();
        }
        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {com.mojang.logging.LogUtils.getLogger().warn("BrowserLink transport error: {}",cause.toString());ctx.close(); }
    }
    /** Bounds-check JPEG SOF before native decoding; reject unsupported frame headers. */
    static boolean validJpeg(ByteBuf b){
        int start=b.readerIndex(),end=start+b.readableBytes();
        if(b.readableBytes()<12||b.readableBytes()>MAX_FRAME_BYTES||b.getUnsignedShort(start)!=0xffd8)return false;
        int p=start+2;
        while(p+4<=end){
            if(b.getUnsignedByte(p++)!=255)return false;
            while(p<end&&b.getUnsignedByte(p)==255)p++;
            if(p>=end)return false;
            int marker=b.getUnsignedByte(p++);
            if(marker==0xda||marker==0xd9||marker==0)return false;
            if(marker==0x01||marker>=0xd0&&marker<=0xd7)continue;
            if(p+2>end)return false;
            int size=b.getUnsignedShort(p);if(size<2||size>end-p)return false;
            if(marker>=0xc0&&marker<=0xcf&&marker!=0xc4&&marker!=0xc8&&marker!=0xcc){
                if((marker!=0xc0&&marker!=0xc2)||size<11||b.getUnsignedByte(p+2)!=8)return false;
                int h=b.getUnsignedShort(p+3),w=b.getUnsignedShort(p+5),channels=b.getUnsignedByte(p+7);
                return (channels==1||channels==3)&&size==8+channels*3&&w>0&&h>0&&w<=2560&&h<=2560&&(long)w*h<=2560L*1440;
            }
            p+=size;
        }return false;
    }
    static boolean validPng(ByteBuf b) {
        int n = b.readableBytes(), i = b.readerIndex();
        if (n < 33 || n > MAX_FRAME_BYTES || b.getLong(i) != 0x89504E470D0A1A0AL
            || b.getInt(i + 8) != 13 || b.getInt(i + 12) != 0x49484452) return false;
        int w = b.getInt(i + 16), h = b.getInt(i + 20);
        return w > 0 && h > 0 && w <= 2560 && h <= 2560 && (long) w * h <= 2560L * 1440;
    }
}
