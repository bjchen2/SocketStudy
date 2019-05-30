package com.study.frames;

import com.study.core.Frame;
import com.study.core.IoArgs;

/**
 * 心跳发送帧
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/13 16:47
 */
public class HeartbeatSendFrame extends AbsSendFrame {
    static final byte[] HEARTBEAT_DATA = new byte[]{0,0,Frame.TYPE_COMMAND_HEARTBEAT,0,0,0};

    public HeartbeatSendFrame() {
        super(HEARTBEAT_DATA);
    }

    @Override
    protected int consumeBody(IoArgs args) {
        return 0;
    }

    @Override
    public Frame nextFrame() {
        return null;
    }
}
