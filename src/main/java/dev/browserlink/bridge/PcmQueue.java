package dev.browserlink.bridge;

/** Bounded PCM queue: prefer current audio over replaying an old backlog after a stall. */
public final class PcmQueue {
    private final byte[] data;
    private int read,size;
    public PcmQueue(int capacity){if(capacity<2||capacity%2!=0)throw new IllegalArgumentException();data=new byte[capacity];}
    public synchronized void clear(){read=0;size=0;}
    public synchronized int size(){return size;}
    public synchronized void offer(byte[] pcm){
        if(pcm.length%2!=0)throw new IllegalArgumentException();
        int start=Math.max(0,pcm.length-data.length);
        int count=pcm.length-start,drop=Math.max(0,size+count-data.length);
        read=(read+drop)%data.length;size-=drop;
        for(int i=start;i<pcm.length;i++){data[(read+size)%data.length]=pcm[i];size++;}
    }
    public synchronized byte[] read(int bytes){
        if(bytes<0||bytes%2!=0)throw new IllegalArgumentException();
        byte[] out=new byte[bytes];int count=Math.min(size,bytes);
        for(int i=0;i<count;i++)out[i]=data[(read+i)%data.length];
        read=(read+count)%data.length;size-=count;return out;
    }
}
