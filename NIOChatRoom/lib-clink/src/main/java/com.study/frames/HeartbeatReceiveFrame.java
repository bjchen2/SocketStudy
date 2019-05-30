package com.study.frames;

import com.study.core.IoArgs;

/**
 * 心跳接收帧,因为所有心跳帧都一样，所以可以写为单例模式
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/3 16:47
 */
public class HeartbeatReceiveFrame extends AbsReceiveFrame {
    static final HeartbeatReceiveFrame INSTANCE = new HeartbeatReceiveFrame();

    private HeartbeatReceiveFrame() {
        super(HeartbeatSendFrame.HEARTBEAT_DATA);
    }

    @Override
    protected int consumeBody(IoArgs args) {
        return 0;
    }
}
