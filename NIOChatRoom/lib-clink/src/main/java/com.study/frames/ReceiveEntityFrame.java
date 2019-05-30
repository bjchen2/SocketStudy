package com.study.frames;

import com.study.core.IoArgs;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

/**
 * 接收数据帧
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/3 16:45
 */
public class ReceiveEntityFrame extends AbsReceiveFrame {
    private WritableByteChannel channel;

    ReceiveEntityFrame(byte[] header) {
        super(header);
    }

    public void bindPacketChannel(WritableByteChannel channel) {
        this.channel = channel;
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        return channel == null? args.setEmpty(getConsumableLength()) : args.writeTo(channel);
    }
}
