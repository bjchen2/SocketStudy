package com.study;

import com.study.bean.ServerInfo;
import com.study.box.StringReceivePacket;
import com.study.handle.ConnectorHandler;
import com.study.handle.ConnectorStringPacketChain;
import com.study.utils.CloseUtils;

import java.io.*;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

/**
 * 当接收到消息时，打印消息
 * @author cxd27419
 */
public class TcpClient extends ConnectorHandler {

    public TcpClient(SocketChannel socketChannel, File cachePath, boolean isNeedPrintReceiveString) throws IOException {
        super(socketChannel,cachePath);
        //添加打印消息的责任链
        if (isNeedPrintReceiveString) {
            getStringPacketChain().appendLast(new PrintStringPacketChain());
        }
    }

    static TcpClient linkWith(ServerInfo info, File cachePath) throws IOException {
        return linkWith(info,cachePath,true);
    }

    static TcpClient linkWith(ServerInfo info, File cachePath, boolean isNeedPrintReceiveString) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();

        // 连接本地，端口2000
        socketChannel.connect(new InetSocketAddress(Inet4Address.getByName(info.getAddress()), info.getPort()));

        System.out.println("已发起服务器连接，并进入后续流程～");
        System.out.println("客户端信息：" + socketChannel.getLocalAddress());
        System.out.println("服务器信息：" + socketChannel.getRemoteAddress());

        try {
            return new TcpClient(socketChannel, cachePath, isNeedPrintReceiveString);
        } catch (Exception e) {
            System.out.println("异常关闭");
            CloseUtils.close(socketChannel);
        }
        return null;
    }

    /**
     * 消息打印责任链
     */
    private class PrintStringPacketChain extends ConnectorStringPacketChain{
        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            String str = stringReceivePacket.entity();
            System.out.println(str);
            return true;
        }
    }
}
