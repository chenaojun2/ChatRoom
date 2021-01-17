package net.qiujuer.lesson.sample.client;


import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.*;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;

public class TCPClient extends Connector {


    public TCPClient(SocketChannel socketChanne) throws IOException {
      setup(socketChanne);
    }

    public void exit(){
        CloseUtils.close(this);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        System.out.println("连接已经关闭，无法读取数据");
    }

    public static TCPClient startWith(ServerInfo info) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();


        // 连接本地，端口2000；超时时间3000ms
        socketChannel.connect(new InetSocketAddress(Inet4Address.getByName(info.getAddress()), info.getPort()));

        System.out.println("已发起服务器连接，并进入后续流程～");
        System.out.println("客户端信息：" + socketChannel.getLocalAddress().toString());
        System.out.println("服务器信息：" + socketChannel.getRemoteAddress().toString());

        try {
            return new TCPClient(socketChannel);
        } catch (Exception e) {
            System.out.println("连接异常");
            CloseUtils.close(socketChannel);
        }
        return null;
    }

}
