package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class Packet<T extends Closeable> implements Closeable {

    private T stream;
    protected byte type;
    protected long length;

    public byte type(){
        return type;
    }

    public long length(){
        return length;
    }

    public final T open() {
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

    protected abstract T createStream();

    protected void closeStream(T stream) throws IOException {
        stream.close();
    }

}
