package com.study.frames;

import com.study.core.Frame;
import com.study.core.IoArgs;
import com.study.core.SendPacket;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;

/**
 * 发送数据帧
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/3 16:45
 */
public class SendEntityFrame extends AbsSendPacketFrame {
    /**
     * 未消费数据长度
     */
    private final long unConsumeEntityLength;
    private final ReadableByteChannel channel;

    SendEntityFrame(short identifier, long entityLength, ReadableByteChannel channel, SendPacket packet) {
        super((int) Math.min(entityLength, Frame.MAX_CAPACITY), Frame.TYPE_PACKET_ENTITY, Frame.FLAG_NONE, identifier, packet);
        unConsumeEntityLength = entityLength - bodyRemaining;
        this.channel = channel;
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        if (packet == null){
            //当前帧已终止发送，则填充假数据
            args.fillEmpty(1);
        }
        return args.readFrom(channel);
    }

    @Override
    public Frame buildNextFrame() {
        if (unConsumeEntityLength == 0) {
            return null;
        }
        return new SendEntityFrame(getBodyIdentifier(), unConsumeEntityLength, channel, packet);
    }
}
