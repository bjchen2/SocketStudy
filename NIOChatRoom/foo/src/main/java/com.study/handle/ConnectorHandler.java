package com.study.handle;

import com.study.Foo;
import com.study.box.StringReceivePacket;
import com.study.core.Connector;
import com.study.core.IoContext;
import com.study.core.Packet;
import com.study.core.ReceivePacket;
import com.study.utils.CloseUtils;
import lombok.Getter;

import java.io.*;
import java.nio.channels.SocketChannel;

/**
 * 消息处理者
 * @author cxd27419
 */
public class ConnectorHandler extends Connector {
    private final File cachePath;
    /**
     * 客户端信息
     */
    @Getter
    private final String clientInfo;
    private final ConnectorCloseChain closeChain = new DefaultPrintConnectorCloseChain();
    private final ConnectorStringPacketChain stringPacketChain = new DefaultNonConnectorStringPacketChain();

    public ConnectorHandler(SocketChannel socketChannel, File cachePath) throws IOException {
        this.clientInfo = socketChannel.getRemoteAddress().toString();
        this.cachePath = cachePath;

        setup(socketChannel);
    }

    public void exit() {
        CloseUtils.close(this);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        closeChain.handle(this, this);
    }

    @Override
    protected void onReceivedPacket(ReceivePacket packet) {
        super.onReceivedPacket(packet);
        switch (packet.type()){
            case Packet.TYPE_MEMORY_STRING:{
                deliveryStringPacket((StringReceivePacket) packet);
                break;
            }
            default:{
                System.out.println("New Packet:" + packet.type() + "-" + packet.getLength());
            }
        }
    }

    @Override
    protected File createNewReceiveFile(long length, byte[] headerInfo) {
        return Foo.createRandomTemp(cachePath);
    }

    @Override
    protected OutputStream createNewReceiveDirectOutputStream(long length, byte[] headerInfo) {
        // 服务器默认创建一个内存存储输出流
        return new ByteArrayOutputStream();
    }

    /**
     * 发送消息，delivery ： 传送/交付
     */
    private void deliveryStringPacket(StringReceivePacket packet) {
        IoContext.get().getScheduler().delivery(()-> stringPacketChain.handle(this,packet));
    }

    public ConnectorStringPacketChain getStringPacketChain() {
        return stringPacketChain;
    }

    public ConnectorCloseChain getCloseChain() {
        return closeChain;
    }
}
