package net.qiujuer.library.clink.core;

import java.io.Closeable;

/**
 * 接收数据的调度封装
 * 把一份的IOArgs组合成一份数据
 */
public interface ReceiveDispatcher extends Closeable {

    void start();

    void stop();

    interface ReceivePacketCallback {

        ReceivePacket<?,?> onArrivedNewPacket(byte type, long length);

        void onReceivePacketComplete(ReceivePacket packet);
    }

}
