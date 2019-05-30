package com.study.box;

import com.study.core.ReceivePacket;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;

/**
 * String接收数据包
 *
 * @author Cx
 * @version jdk8 and idea On 2019/4/25 10:53
 */
public class StringReceivePacket extends AbsByteArrayReceivePacket<String> {

    public StringReceivePacket(long len) {
        super(len);
    }

    @Override
    protected String buildEntity(ByteArrayOutputStream stream) {
        return new String(stream.toByteArray());
    }

    @Override
    public byte type() {
        return TYPE_MEMORY_STRING;
    }
}
