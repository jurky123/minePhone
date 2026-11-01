package dev.browserlink.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** One latest frame at every stage: a slow game never accumulates a frame backlog. */
public final class BrowserTexture {
    private final ExecutorService decoder = Executors.newSingleThreadExecutor(task -> { Thread t = new Thread(task, "browserlink-decode"); t.setDaemon(true); return t; });
    private final AtomicReference<Encoded> compressed = new AtomicReference<>();
    private final AtomicReference<Decoded> decoded = new AtomicReference<>();
    private final AtomicBoolean decoding = new AtomicBoolean();
    private final AtomicInteger generation = new AtomicInteger();
    private int shownGeneration = -1;
    private DynamicTexture texture;
    private ResourceLocation location;
    private long lastFrame;
    // Owned exclusively by the decoder thread; reuse encoded storage across frames.
    private java.nio.ByteBuffer decodeBuffer;
    private final AtomicLong received=new AtomicLong(),bytes=new AtomicLong(),dropped=new AtomicLong();
    private volatile double decodeMs;
    private double uploadMs;
    private long uploaded,sampleAt=System.nanoTime(),sampleReceived,sampleBytes,sampleUploaded;
    private String metrics="Waiting for frames";
    private record Decoded(int generation, NativeImage image) {}
    private record Encoded(int generation, byte[] bytes) {}
    public void reset() {
        generation.incrementAndGet(); compressed.set(null);
        Decoded old = decoded.getAndSet(null); if (old != null) old.image.close();
    }
    public void receive(byte[] png) { received.incrementAndGet();bytes.addAndGet(png.length);if(compressed.getAndSet(new Encoded(generation.get(), png))!=null)dropped.incrementAndGet();schedule(); }
    private void schedule() {
        if (decoding.compareAndSet(false, true)) decoder.execute(() -> {
            try {
                Encoded next;
                while ((next = compressed.getAndSet(null)) != null) {
                    int gen = next.generation;
                    try {
                        long started=System.nanoTime();
                        if(decodeBuffer==null||decodeBuffer.capacity()<next.bytes.length){
                            if(decodeBuffer!=null)org.lwjgl.system.MemoryUtil.memFree(decodeBuffer);
                            decodeBuffer=org.lwjgl.system.MemoryUtil.memAlloc(((next.bytes.length+65535)/65536)*65536);
                        }
                        decodeBuffer.clear();decodeBuffer.put(next.bytes).flip();
                        NativeImage image = NativeImage.read(decodeBuffer);
                        decodeMs=(System.nanoTime()-started)/1_000_000.0;
                        if (gen != generation.get()) { image.close(); continue; }
                        Decoded previous = decoded.getAndSet(new Decoded(gen, image));
                        if (previous != null) {previous.image.close();dropped.incrementAndGet();}
                    } catch (Exception ignored) { ClientRuntime.status = "Could not decode browser frame"; }
                }
            } finally {
                decoding.set(false);
                if (compressed.get() != null) schedule();
            }
        });
    }
    public void upload() {
        if (shownGeneration != generation.get()) {
            if (location != null) Minecraft.getInstance().getTextureManager().release(location);
            texture = null; location = null; shownGeneration = generation.get();
        }
        Decoded next = decoded.getAndSet(null);
        if (next == null) return;
        if (next.generation != generation.get()) { next.image.close(); return; }
        long started=System.nanoTime();
        if (texture == null) {
            texture = new DynamicTexture(next.image);
            location = Minecraft.getInstance().getTextureManager().register("browserlink", texture);
        } else if (texture.getPixels().getWidth() != next.image.getWidth() || texture.getPixels().getHeight() != next.image.getHeight()) {
            Minecraft.getInstance().getTextureManager().release(location);
            texture = new DynamicTexture(next.image);
            location = Minecraft.getInstance().getTextureManager().register("browserlink", texture);
        } else {
            texture.setPixels(next.image);
            texture.upload();
        }
        texture.setFilter(true, false);
        lastFrame = System.currentTimeMillis();
        uploaded++;uploadMs=(System.nanoTime()-started)/1_000_000.0;
    }
    public String stats(){
        long now=System.nanoTime();double elapsed=(now-sampleAt)/1_000_000_000.0;
        if(elapsed>=1){long r=received.get(),b=bytes.get();
            metrics=String.format(java.util.Locale.ROOT,"%dx%d | RX %.1f / GPU %.1f fps | %.2f MiB/s | decode %.1f / upload %.1f ms | drop %d",width(),height(),(r-sampleReceived)/elapsed,(uploaded-sampleUploaded)/elapsed,(b-sampleBytes)/elapsed/1048576.0,decodeMs,uploadMs,dropped.get());
            if(Boolean.getBoolean("browserlink.benchmark"))com.mojang.logging.LogUtils.getLogger().info("BrowserLink benchmark: {} | mode {} | connected {} | age {} ms",metrics,DisplayDemand.mode,ClientRuntime.connected(),ageMillis());
            sampleAt=now;sampleReceived=r;sampleUploaded=uploaded;sampleBytes=b;
        }return metrics;
    }
    public ResourceLocation location() { return location; }
    public int width() { return texture == null ? 16 : texture.getPixels().getWidth(); }
    public int height() { return texture == null ? 9 : texture.getPixels().getHeight(); }
    public long ageMillis() { return lastFrame == 0 ? 0 : System.currentTimeMillis() - lastFrame; }
}
