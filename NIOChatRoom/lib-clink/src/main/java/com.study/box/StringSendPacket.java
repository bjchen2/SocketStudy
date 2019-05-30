package com.study.box;

/**
 * 发送String的数据包
 *
 * @author Cx
 * @version jdk8 and idea On 2019/4/25 10:51
 */
public class StringSendPacket extends BytesSendPacket {
    /**
     * 字符串发送时就是Byte数组，所以直接得到Byte数组，并按照Byte的发送方式发送即可
     *
     * @param msg 字符串
     */
    public StringSendPacket(String msg) {
        super(msg.getBytes());
    }

    @Override
    public byte type() {
        return TYPE_MEMORY_STRING;
    }
}
