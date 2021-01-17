package net.qiujuer.library.clink.core;

import java.io.Closeable;

/**
 * 发送数据调度者
 * 缓存所有需要发送的数据，通过队列对数据进行发送
 * 并且再发送数据的时候，实现基本包装
 * */
public interface SendDispatcher extends Closeable {

    /**
     * 发送数据
     * @param packet 数据
     * */
    void send(SendPacket packet);


    /**
     * 取消发送数据
     *
     * @param packet 数据
     * */
    void cancel(SendPacket packet);


}
