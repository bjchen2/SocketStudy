package com.study.frames;

import com.study.core.Frame;
import com.study.core.IoArgs;
import com.study.core.SendPacket;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * 直流输出帧
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/15 10:55
 */
public class SendDirectEntityFrame extends AbsSendPacketFrame {
    private final ReadableByteChannel channel;

    /**
     * @param identifier 当前帧唯一标识
     * @param available 可发送数据量
     * @param channel 发送数据读取通道
     * @param packet 接收包
     */
    SendDirectEntityFrame(short identifier, int available, ReadableByteChannel channel, SendPacket packet) {
        super(Math.min(available, Frame.MAX_CAPACITY), Frame.TYPE_PACKET_ENTITY, Frame.FLAG_NONE, identifier, packet);
        this.channel = channel;
    }

    @Override
    protected Frame buildNextFrame() {
        int available = packet.available();
        if (available <= 0) {
            return new CancelSendFrame(getBodyIdentifier());
        }
        return new SendDirectEntityFrame(getBodyIdentifier(), available, channel, packet);
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        if (packet == null) {
            return args.fillEmpty(bodyRemaining);
        }
        return args.readFrom(channel);
    }


    /**
     * 通过Packet构建内容发送帧
     * 若当前内容无可读内容，则直接发送取消帧
     *
     * @param packet     Packet
     * @param identifier 当前标识
     * @return 内容帧
     */
    static Frame buildEntityFrame(SendPacket<?> packet, short identifier) {
        int available = packet.available();
        if (available <= 0) {
            // 直流结束
            return new CancelSendFrame(identifier);
        }
        // 构建首帧
        InputStream stream = packet.open();
        ReadableByteChannel channel = Channels.newChannel(stream);
        return new SendDirectEntityFrame(identifier, available, channel, packet);
    }
}
