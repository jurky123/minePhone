package dev.browserlink.bridge;
import io.netty.buffer.ByteBuf;
public final class AudioPacket {
    public static final int MAGIC=0x424c4155; // BLAU; PCM mono, signed 16-bit little-endian, 48 kHz.
    public static boolean valid(ByteBuf b){
        int i=b.readerIndex(),n=b.readableBytes();
        if(n<18||n>9616||b.getInt(i)!=MAGIC||b.getIntLE(i+4)!=48000)return false;
        int count=b.getIntLE(i+8);
        return count>0&&count<=4800&&n==16+count*2&&b.getIntLE(i+12)>=0;
    }
}
