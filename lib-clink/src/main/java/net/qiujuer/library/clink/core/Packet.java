package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class Packet<Stream extends Closeable> implements Closeable {

    // BYTES 类型
    public static final byte TYPE_MEMORY_BYTES = 1;
    // String 类型
    public static final byte TYPE_MEMORY_STRING = 2;
    // 文件 类型
    public static final byte TYPE_STREAM_FILE = 3;
    // 长链接流 类型
    public static final byte TYPE_STREAM_DIRECT = 4;

    private Stream stream;
    protected long length;


    public long length(){
        return length;
    }

    public final Stream open() {
        if (stream == null) {
            stream = createStream();
        }
        return stream;
    }

    @Override
    public final void close() throws IOException {
        if(stream != null) {
            closeStream(stream);
            stream = null;
        }
    }

    public abstract byte type();

    protected abstract Stream createStream();

    protected void closeStream(Stream stream) throws IOException {
        stream.close();
    }

    public byte[] headerInfo() {
        return null;
    }

}
