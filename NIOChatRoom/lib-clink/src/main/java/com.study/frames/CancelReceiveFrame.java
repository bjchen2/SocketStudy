package com.study.frames;

import com.study.core.IoArgs;

/**
 * 取消接收帧
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/3 16:47
 */
public class CancelReceiveFrame extends AbsReceiveFrame {
    /**
     * 取消帧的接收
     */
    CancelReceiveFrame(byte[] header) {
        super(header);
    }

    @Override
    protected int consumeBody(IoArgs args) {
        return 0;
    }
}
