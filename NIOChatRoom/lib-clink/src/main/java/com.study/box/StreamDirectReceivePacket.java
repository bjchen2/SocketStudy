package com.study.box;

import com.study.core.Packet;
import com.study.core.ReceivePacket;
import com.study.core.SendPacket;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 直流接收包
 * @author Cx
 * @version jdk8 and idea On 2019/5/15 9:47
 */
public class StreamDirectReceivePacket extends ReceivePacket<OutputStream,OutputStream> {
    private OutputStream outputStream;

    public StreamDirectReceivePacket(OutputStream outputStream, long len) {
        super(len);
        this.outputStream = outputStream;
    }


    @Override
    public byte type() {
        return Packet.TYPE_STREAM_DIRECT;
    }

    @Override
    protected OutputStream createStream() {
        return outputStream;
    }

    @Override
    protected OutputStream buildEntity(OutputStream stream) {
        return outputStream;
    }
}
