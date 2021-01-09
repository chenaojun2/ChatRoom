package net.qiujuer.library.clink.impl;

import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.core.Receiver;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketChannelAdapter implements Sender, Receiver, Cloneable {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final SocketChannel channel;
    private final IoProvider ioProvider;
    private final OnChannelStatusChannelListener listener;

    private IoArgs.IoArgsEventListener receiveIoEventListener;
    private IoArgs.IoArgsEventListener sendIoEventListener;

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider, OnChannelStatusChannelListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        channel.configureBlocking(false);
    }

    @Override
    public boolean receiveAsync(IoArgs.IoArgsEventListener listener) throws IOException {
        if (isClosed.get()) {
            throw new IOException("Current channel isClosed");
        }
        receiveIoEventListener = listener;
        return ioProvider.registerInput(channel, inputCallback);
    }

    @Override
    public boolean sendAsync(IoArgs args, IoArgs.IoArgsEventListener listener) throws IOException {
        if (isClosed.get()) {
            throw new IOException("Current channel isClosed");
        }
        sendIoEventListener = listener;
        //当前发送的数据附加到回调中
        outputCallback.setAttach(args);
        return ioProvider.registerOutput(channel, outputCallback);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            ioProvider.unRegisterInput(channel);
            ioProvider.unRegisterOutput(channel);
            CloseUtils.close(channel);
            listener.onChannelClosed(channel);
        }
    }

    private final IoProvider.HandleInputCallback inputCallback = new IoProvider.HandleInputCallback() {
        @Override
        protected void canProviderInput() {
            if (isClosed.get()) {
                return;
            }
            IoArgs args = new IoArgs();
            IoArgs.IoArgsEventListener listenr = SocketChannelAdapter.this.receiveIoEventListener;
            if (listenr != null) {
                listenr.onStarted(args);
            }

            //具体的读取操作
            try {
                if (args.read(channel) > 0) {
                    //读取完成回调
                    listenr.onCompleted(args);
                } else {
                    throw new IOException("Cannot read any data!!");
                }

            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    private final IoProvider.HandleOutputCallback outputCallback = new IoProvider.HandleOutputCallback() {
        @Override
        protected void canProviderOutput() {
            if (isClosed.get()) {
                return;
            }
            //TODO
            sendIoEventListener.onCompleted(null);
        }
    };

    public interface OnChannelStatusChannelListener {
        void onChannelClosed(SocketChannel channel);
    }
}
