package net.qiujuer.library.clink.core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class IoArgs {

    private int limit = 256;
    private byte[] byteBuffer = new byte[256];
    private ByteBuffer buffer = ByteBuffer.wrap(byteBuffer);

    /**
     * 从byte中读取数据
     * */
    public int readFrom(byte[] bytes, int offset) {
        int size = Math.min(bytes.length - offset, buffer.remaining());
        buffer.put(bytes, offset, size);
        return size;
    }

    /**
     * 从bute中写入数据
     * */
    public int writeTo(byte[] bytes, int offset) {
        int size = Math.min(bytes.length - offset, buffer.remaining());
        buffer.get(bytes,offset,size);
        return size;
    }

    /**
     * 从SocketChannel读取数据
     * */
    public int readFrom(SocketChannel channel) throws IOException {
        startWriting();
        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int len = channel.read(buffer);
            if(len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }
        finishWriting();
        return bytesProduced;
    }

    /**
     * 写入数据到SocketChannel
     * */
    public int writeTo(SocketChannel channel) throws IOException {
        int bytesProduced = 0;
        while (buffer.hasRemaining()) {
            int len = channel.write(buffer);
            if(len < 0) {
                throw new EOFException();
            }
            bytesProduced += len;
        }
        return bytesProduced;
    }

    /**
     * 开始写数据
     * */
    public void startWriting(){
        buffer.clear();
        buffer.limit(limit);
    }

    /**
     * 写完数据
     * */
    public void finishWriting(){
        buffer.flip();
    }

    public void limit(int limit) {
        this.limit = limit;
    }

    public String bufferString() {
        return new String(byteBuffer, 0, buffer.position() - 1);
    }

    public void writeLength(int total) {
        buffer.putInt(total);
    }

    public int readLength(){
        return buffer.getInt();
    }

    public int capacity() {
        return buffer.capacity();
    }

    public interface IoArgsEventListener {

        void onStarted(IoArgs args);

        void onCompleted(IoArgs args);

    }

}
