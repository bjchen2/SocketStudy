package com.study.frames;

import com.study.core.IoArgs;

/**
 * 接收头帧
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/3 16:45
 */
public class ReceiveHeaderFrame extends AbsReceiveFrame {
    private final byte[] body;

    ReceiveHeaderFrame(byte[] header) {
        super(header);
        body = new byte[bodyRemaining];
    }

    @Override
    protected int consumeBody(IoArgs args) {
        int offset = body.length - bodyRemaining;
        return args.writeTo(body, offset);
    }

    public long getPacketLength() {
        return ((((long) body[0]) & 0xFFL) << 32)
                | ((((long) body[1]) & 0xFFL) << 24)
                | ((((long) body[2]) & 0xFFL) << 16)
                | ((((long) body[3]) & 0xFFL) << 8)
                | (((long) body[4]) & 0xFFL);
    }

    public byte getPacketType() {
        return body[5];
    }

    public byte[] getPacketHeaderInfo() {
        if (body.length > SendHeaderFrame.PACKET_HEADER_FRAME_MIN_LENGTH) {
            byte[] headerInfo = new byte[body.length - SendHeaderFrame.PACKET_HEADER_FRAME_MIN_LENGTH];
            System.arraycopy(body, SendHeaderFrame.PACKET_HEADER_FRAME_MIN_LENGTH,
                    headerInfo, 0, headerInfo.length);
            return headerInfo;
        }
        return null;
    }
}
