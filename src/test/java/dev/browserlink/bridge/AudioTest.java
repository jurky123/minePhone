package dev.browserlink.bridge;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AudioTest {
    @Test void silenceFillsUnderflowAndDoesNotRepeatOldSamples(){
        PcmQueue q=new PcmQueue(8);q.offer(new byte[]{1,2,3,4});assertArrayEquals(new byte[]{1,2,3,4,0,0},q.read(6));assertArrayEquals(new byte[4],q.read(4));
    }
    @Test void backlogDropsOldestCompleteSamples(){
        PcmQueue q=new PcmQueue(6);q.offer(new byte[]{1,2,3,4});q.offer(new byte[]{5,6,7,8});assertArrayEquals(new byte[]{3,4,5,6,7,8},q.read(6));
    }
    @Test void hugeChunkRetainsOnlyNewestAudioAndResetDropsAll(){
        PcmQueue q=new PcmQueue(4);q.offer(new byte[]{1,2,3,4,5,6});assertArrayEquals(new byte[]{3,4,5,6},q.read(4));q.offer(new byte[]{1,2});q.clear();assertEquals(0,q.size());
    }
    @Test void rejectsUnalignedSamples(){assertThrows(IllegalArgumentException.class,()->new PcmQueue(3));assertThrows(IllegalArgumentException.class,()->new PcmQueue(4).offer(new byte[3]));}
    @Test void validAudioRequiresExactLengthRateAndTab(){
        var b=Unpooled.buffer();try{
            b.writeInt(AudioPacket.MAGIC).writeIntLE(48000).writeIntLE(960).writeIntLE(42).writeZero(1920);
            assertTrue(AudioPacket.valid(b));b.setIntLE(8,961);assertFalse(AudioPacket.valid(b));b.setIntLE(8,960);
            b.setIntLE(4,44100);assertFalse(AudioPacket.valid(b));b.setIntLE(4,48000);
            b.setIntLE(12,-1);assertFalse(AudioPacket.valid(b));
        }finally{b.release();}
    }
    @Test void frameLimitsRejectTruncatedAndOversizedAudio(){
        var b=Unpooled.buffer();try{b.writeInt(AudioPacket.MAGIC);assertFalse(AudioPacket.valid(b));b.writeIntLE(48000).writeIntLE(5000).writeIntLE(42).writeZero(10000);assertFalse(AudioPacket.valid(b));}finally{b.release();}
    }
}
