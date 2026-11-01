package dev.browserlink.bridge;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BridgeServerTest {
    private static final String TOKEN="12345678901234567890123456789012";
    private static final byte[] PNG=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLttAAAAABJRU5ErkJggg==");
    BridgeServer server;
    int port;
    AtomicInteger frames, connections;
    AtomicInteger audioFrames;
    @BeforeEach void start() throws Exception {
        frames=new AtomicInteger();connections=new AtomicInteger();
        audioFrames=new AtomicInteger();
        server=new BridgeServer(new BridgeServer.Listener(){
            public void connected(){connections.incrementAndGet();}
            public void disconnected(){connections.decrementAndGet();}
            public void frame(byte[] b){frames.incrementAndGet();}
            public void status(String s){}
            public void audio(int tabId,byte[] pcm){if(tabId==42&&pcm.length==1920)audioFrames.incrementAndGet();}
        });
        port=server.start(0);
    }
    @AfterEach void close(){server.close();}
    static class Client implements WebSocket.Listener {
        BlockingQueue<String> messages=new LinkedBlockingQueue<>();
        CompletableFuture<Integer> closed=new CompletableFuture<>();
        public void onOpen(WebSocket ws){ws.request(1);}
        public CompletionStage<?> onText(WebSocket ws,CharSequence text,boolean last){messages.add(text.toString());ws.request(1);return null;}
        public CompletionStage<?> onClose(WebSocket ws,int code,String reason){closed.complete(code);return null;}
    }
    WebSocket connect(Client listener,String origin) throws Exception {
        return HttpClient.newHttpClient().newWebSocketBuilder().header("Origin",origin)
            .buildAsync(URI.create("ws://127.0.0.1:"+port+"/browserlink"),listener).get(5,TimeUnit.SECONDS);
    }
    WebSocket auth(Client listener) throws Exception {
        WebSocket ws=connect(listener,BrowserIdentity.ORIGIN);
        ws.sendText("{\"type\":\"hello\",\"protocol\":2}",true).join();
        assertTrue(listener.messages.poll(5,TimeUnit.SECONDS).contains("ready"));return ws;
    }
    @Test void rejectsWebsiteOrigin() {
        assertThrows(ExecutionException.class,()->connect(new Client(),"https://example.com"));
        assertFalse(server.isConnected());
    }
    @Test void rejectsOtherExtensionAndMissingOrigin() {
        assertThrows(ExecutionException.class,()->connect(new Client(),"chrome-extension://aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertThrows(ExecutionException.class,()->connect(new Client(),"null"));
    }
    @Test void waitsForWorldAndAllowsAutomaticReconnect() throws Exception {
        server.setWorldActive(false);
        Client waiting=new Client(); WebSocket ws=connect(waiting,BrowserIdentity.ORIGIN);
        ws.sendText("{\"type\":\"hello\",\"protocol\":2}",true).join();
        assertEquals(1013,waiting.closed.get(5,TimeUnit.SECONDS));
        server.setWorldActive(true);
        Client active=new Client();auth(active);
        server.setWorldActive(false);
        assertEquals(1013,active.closed.get(5,TimeUnit.SECONDS));
    }
    @Test void manifestIdentityMatchesPinnedOrigin() throws Exception {
        var manifest=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("extension/manifest.json"))).getAsJsonObject();
        byte[] key=Base64.getDecoder().decode(manifest.get("key").getAsString());
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(key);
        StringBuilder id=new StringBuilder();
        for(int i=0;i<16;i++){id.append((char)('a'+((hash[i]&255)>>4)));id.append((char)('a'+(hash[i]&15)));}
        assertEquals("chrome-extension://"+id,BrowserIdentity.ORIGIN);
    }
    @Test void rejectsOldHandshake() throws Exception {
        Client client=new Client();WebSocket ws=connect(client,BrowserIdentity.ORIGIN);
        ws.sendText("{\"type\":\"hello\",\"token\":\"wrong\"}",true).join();
        assertEquals(1008,client.closed.get(5,TimeUnit.SECONDS));assertEquals(0,connections.get());
    }
    @Test void authenticatesStreamsAndRoutesInput() throws Exception {
        Client client=new Client();WebSocket ws=auth(client);
        ws.sendBinary(ByteBuffer.wrap(PNG),true).join();
        ws.sendText("{\"type\":\"ping\"}",true).join();
        assertTrue(client.messages.poll(5,TimeUnit.SECONDS).contains("pong"));assertEquals(1,frames.get());
        JsonObject input=new JsonObject();input.addProperty("type","text");input.addProperty("text","你好");server.send(input);
        assertTrue(client.messages.poll(5,TimeUnit.SECONDS).contains("你好"));
        server.disconnect();client.closed.get(5,TimeUnit.SECONDS);
    }
    @Test void rejectsSecondController() throws Exception {
        Client first=new Client();WebSocket ws=auth(first);
        Client second=new Client();WebSocket other=connect(second,BrowserIdentity.ORIGIN);
        other.sendText("{\"type\":\"hello\",\"protocol\":2}",true).join();
        assertEquals(1013,second.closed.get(5,TimeUnit.SECONDS));assertEquals(1,connections.get());
        ws.sendClose(1000,"done").join();
    }
    @Test void rejectsOversizedImageBeforeDecode() {
        byte[] bad=PNG.clone();ByteBuffer.wrap(bad).putInt(16,100000);
        var data=Unpooled.wrappedBuffer(bad);try{assertFalse(BridgeServer.validPng(data));}finally{data.release();}
    }
    @Test void acceptsJpegAndDropsBadImagesWithoutDisconnecting() throws Exception {
        var image=new java.awt.image.BufferedImage(32,24,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"jpeg",output);
        byte[] jpeg=output.toByteArray();var data=Unpooled.wrappedBuffer(jpeg);
        try{assertTrue(BridgeServer.validJpeg(data));}finally{data.release();}
        Client client=new Client();WebSocket ws=auth(client);
        ws.sendBinary(ByteBuffer.wrap(new byte[]{1,2,3}),true).join();
        ws.sendBinary(ByteBuffer.wrap(jpeg),true).join();ws.sendText("{\"type\":\"ping\"}",true).join();
        assertTrue(client.messages.poll(5,TimeUnit.SECONDS).contains("pong"));assertEquals(1,frames.get());assertTrue(server.isConnected());
        for(int n=0;n<30;n++){var truncated=Unpooled.wrappedBuffer(java.util.Arrays.copyOf(jpeg,n));try{assertFalse(BridgeServer.validJpeg(truncated));}finally{truncated.release();}}
    }
    @Test void accepts1440pButRejectsLargerPixelBudgets() {
        byte[] header=PNG.clone();ByteBuffer.wrap(header).putInt(16,2560).putInt(20,1440);
        var data=Unpooled.wrappedBuffer(header);
        try{assertTrue(BridgeServer.validPng(data));data.setInt(20,1441);assertFalse(BridgeServer.validPng(data));}
        finally{data.release();}
    }
    @Test void rejectsFrameBeforeAuthentication() throws Exception {
        Client client=new Client();WebSocket ws=connect(client,BrowserIdentity.ORIGIN);
        ws.sendBinary(ByteBuffer.wrap(PNG),true).join();client.closed.get(5,TimeUnit.SECONDS);assertEquals(0,frames.get());
    }
    @Test void routesPcmSeparatelyFromPngOnAuthenticatedSocket() throws Exception {
        Client client=new Client();WebSocket ws=auth(client);
        ByteBuffer packet=ByteBuffer.allocate(1936);packet.putInt(AudioPacket.MAGIC).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(48000).putInt(960).putInt(42);packet.position(0);
        var check=Unpooled.wrappedBuffer(packet.array());try{assertTrue(AudioPacket.valid(check));}finally{check.release();}
        ws.sendBinary(ByteBuffer.wrap(packet.array()),true).join();ws.sendText("{\"type\":\"ping\"}",true).join();
        assertTrue(client.messages.poll(5,TimeUnit.SECONDS).contains("pong"));assertEquals(1,audioFrames.get());assertEquals(0,frames.get());
    }
}
