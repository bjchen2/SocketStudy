package com.study.frames;

import com.study.core.Frame;
import com.study.core.IoArgs;
import com.study.core.Packet;
import com.study.core.SendPacket;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * 数据发送帧首帧（需要指定本次数据发送长度(5字节)和类型）
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/3 16:45
 */
public class SendHeaderFrame extends AbsSendPacketFrame {
    static final int PACKET_HEADER_FRAME_MIN_LENGTH = 6;
    private final byte[] body;

    public SendHeaderFrame(short identifier, SendPacket packet) {
        super(PACKET_HEADER_FRAME_MIN_LENGTH,Frame.TYPE_PACKET_HEADER,Frame.FLAG_NONE,identifier,packet);

        final long packetLength = packet.getLength();
        final byte packetType = packet.type();
        final byte[] packetHeaderInfo = packet.headerInfo();

        this.body = new byte[bodyRemaining];
        //设置长度
        body[0] = (byte) (packetLength >> 32);
        body[1] = (byte) (packetLength >> 24);
        body[2] = (byte) (packetLength >> 16);
        body[3] = (byte) (packetLength >> 8);
        body[4] = (byte) (packetLength);
        //设置类型
        body[5] = packetType;
        //设置数据
        if (packetHeaderInfo != null){
            System.arraycopy(packetHeaderInfo,0,body,PACKET_HEADER_FRAME_MIN_LENGTH, packetHeaderInfo.length);
        }
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        int count = bodyRemaining;
        int offset = body.length - bodyRemaining;
        //进行消费
        return args.readFrom(body, offset, count);
    }

    @Override
    public Frame buildNextFrame() {
        if (packet.type() == Packet.TYPE_STREAM_DIRECT){
            //直流类型
            return SendDirectEntityFrame.buildEntityFrame(packet,getBodyIdentifier());
        }else {
            InputStream stream = packet.open();
            ReadableByteChannel channel = Channels.newChannel(stream);
            return new SendEntityFrame(getBodyIdentifier(),packet.getLength(),channel,packet);
        }
    }
}
