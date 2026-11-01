package dev.browserlink.client;

import com.google.gson.JsonObject;
import dev.browserlink.*;
import dev.browserlink.bridge.PcmQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.client.sounds.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/** One real Minecraft positional sound, attached to the last selected device. */
public final class BrowserAudio {
    private static final PcmQueue PCM=new PcmQueue(14400); // 150 ms, bounded even if the game stalls.
    private static volatile long lastPacket;
    private static Level sourceLevel;
    private static BlockPos block;
    private static InteractionHand hand;
    private static LiveSound sound;
    private static int ticks;
    private static boolean routed;
    private static boolean signalReported;
    public static void selectBlock(BlockPos pos){sourceLevel=Minecraft.getInstance().level;block=pos.immutable();hand=null;}
    public static void selectPhone(InteractionHand selected){sourceLevel=Minecraft.getInstance().level;block=null;hand=selected;}
    public static void receive(int tab,byte[] pcm){if(tab==ClientRuntime.tabs.activeId()){
        PCM.offer(pcm);lastPacket=System.currentTimeMillis();
        if(!signalReported){for(byte value:pcm)if(value!=0){signalReported=true;com.mojang.logging.LogUtils.getLogger().info("BrowserLink received non-silent 48 kHz mono audio");break;}}
    }}
    public static void clearFrames(){lastPacket=0;signalReported=false;PCM.clear();}
    public static void reset(){clearFrames();Minecraft.getInstance().execute(()->{stop();sourceLevel=null;block=null;hand=null;route(false);});}
    private static Vec3 position(){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.level!=sourceLevel||mc.player==null||!mc.player.isAlive())return null;
        if(block!=null){
            if(!mc.level.hasChunkAt(block))return null;
            var state=mc.level.getBlockState(block);
            if(!state.is(BrowserLink.TV.get())&&!state.is(BrowserLink.COMPUTER.get()))return null;
            float[] s=state.is(BrowserLink.TV.get())?TvGeometry.SCREEN:ComputerGeometry.SCREEN;
            return Vec3.atLowerCornerOf(block).add(.5,(s[1]+s[3])/2,.5);
        }
        if(hand!=null&&mc.player.getItemInHand(hand).is(BrowserLink.PHONE.get()))return mc.player.getEyePosition().add(mc.player.getLookAngle().scale(.4));
        return null;
    }
    private static void route(boolean active){
        if(active!=routed)com.mojang.logging.LogUtils.getLogger().info("BrowserLink positional audio {}",active?"active":"released to Chrome");
        routed=active;JsonObject msg=new JsonObject();msg.addProperty("type","audio-sink");msg.addProperty("enabled",active);ClientRuntime.send(msg);
    }
    private static void stop(){if(sound!=null){Minecraft.getInstance().getSoundManager().stop(sound);sound=null;}PCM.clear();}
    public static void tick(){
        var mc=Minecraft.getInstance();ticks++;
        boolean eligible=ClientRuntime.connected()&&!mc.isPaused()&&position()!=null&&System.currentTimeMillis()-lastPacket<1500;
        if(!eligible){if(sound!=null)stop();if(routed||ticks%10==0)route(false);return;}
        if(sound==null||(ticks%20==0&&!mc.getSoundManager().isActive(sound))){stop();sound=new LiveSound();mc.getSoundManager().play(sound);}
        boolean muted=mc.options.getSoundSourceVolume(SoundSource.MASTER)==0||mc.options.getSoundSourceVolume(SoundSource.RECORDS)==0;
        boolean active=muted||mc.getSoundManager().isActive(sound);
        if(active!=routed||ticks%10==0)route(active);
    }
    private static final class LiveSound extends AbstractTickableSoundInstance {
        private static final ResourceLocation ID=new ResourceLocation("browserlink","browser_audio");
        LiveSound(){
            super(SoundEvent.createVariableRangeEvent(ID),SoundSource.RECORDS,SoundInstance.createUnseededRandom());
            looping=true;relative=false;volume=1;pitch=1;attenuation=Attenuation.LINEAR;tick();
        }
        @Override public boolean canStartSilent(){return true;}
        @Override public WeighedSoundEvents resolve(SoundManager manager){
            sound=new Sound(ID.toString(),ConstantFloat.of(1),ConstantFloat.of(1),1,Sound.Type.FILE,true,false,24);
            WeighedSoundEvents events=new WeighedSoundEvents(ID,null);events.addSound(sound);return events;
        }
        @Override public void tick(){Vec3 p=position();if(p==null){stop();return;}x=p.x;y=p.y;z=p.z;}
        @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library,Sound selected,boolean loop){
            return CompletableFuture.completedFuture(new AudioStream(){
                private boolean closed;
                public AudioFormat getFormat(){return new AudioFormat(48000,16,1,true,false);}
                public ByteBuffer read(int requested){
                    if(closed)return null;
                    // Vanilla requests one-second buffers. Shorter valid reads avoid four seconds of latency.
                    byte[] bytes=PCM.read(Math.min(requested,4800)&~1);
                    return ByteBuffer.allocateDirect(bytes.length).put(bytes).flip();
                }
                public void close(){closed=true;}
            });
        }
    }
}
