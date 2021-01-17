package net.qiujuer.library.clink.impl.async;

import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendDispatcher;
import net.qiujuer.library.clink.core.SendPacket;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncSendDispatcher implements SendDispatcher {

    private final Sender sender;
    private final Queue<SendPacket> queue = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean isSending = new AtomicBoolean();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private IoArgs ioArgs = new IoArgs();
    private SendPacket packetTemp;

    //当前大小进度
    private int total;
    private int position;

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
    }

    @Override
    public void send(SendPacket packet) {
        queue.offer(packet);
        if(isSending.compareAndSet(false,true)){
            sendNextPacket();
        }
    }

    @Override
    public void cancel(SendPacket packet) {

    }

    private SendPacket takePacket() {
        SendPacket packet = queue.poll();
        if(packet != null && packet.isCanceled()) {
            //已经取消不用发送
            takePacket();
        }
        return packet;
    }

    private void sendNextPacket() {
        SendPacket temp = packetTemp;
        if(temp != null) {
            CloseUtils.close(temp);
        }
        SendPacket packet = packetTemp = takePacket();
        if(packet == null) {
            //队列为空取消
            isSending.set(false);
            return;
        }
        total = packet.length();
        position = 0;

        sendCurrentPacket();
    }

    private void sendCurrentPacket() {
        IoArgs args = ioArgs;

        args.startWriting();
        if(position >= total){
            sendNextPacket();
            return;
        } else if(position == 0){
            //发送首包，需要携带长度信息
            args.writeLength(total);
        }

        byte[] bytes = packetTemp.bytes();
        //把bytes的数据写入到IoArgs
        int count = args.readFrom(bytes,position);
        position += count;

        //完成数据封装
        args.finishWriting();
        try {
            sender.sendAsync(args,ioArgsEventListener);
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    @Override
    public void close() throws IOException {
        if(isClosed.compareAndSet(false,true)){
            isSending.set(false);
            SendPacket packet = packetTemp;
            if(packet != null) {
                packetTemp = null;
                CloseUtils.close(packet);
            }
        }
    }

    private final IoArgs.IoArgsEventListener ioArgsEventListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {

        }

        @Override
        public void onCompleted(IoArgs args) {
            //继续发送当前包
            sendCurrentPacket();
        }
    };


}
