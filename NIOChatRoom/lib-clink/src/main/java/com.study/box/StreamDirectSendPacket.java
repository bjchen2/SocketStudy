package com.study.box;

import com.study.core.Packet;
import com.study.core.SendPacket;

import java.io.InputStream;

/**
 * 直接流传输包
 * @author Cx
 * @version jdk8 and idea On 2019/5/15 9:47
 */
public class StreamDirectSendPacket extends SendPacket<InputStream> {
    private InputStream inputStream;

    public StreamDirectSendPacket(InputStream inputStream) {
        this.inputStream = inputStream;
        this.length = MAX_PACKET_SIZE;
    }

    @Override
    public byte type() {
        return Packet.TYPE_STREAM_DIRECT;
    }

    @Override
    protected InputStream createStream() {
        return inputStream;
    }
}
