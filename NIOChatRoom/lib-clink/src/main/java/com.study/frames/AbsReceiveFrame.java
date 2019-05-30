package com.study.frames;

import com.study.core.Frame;
import com.study.core.IoArgs;

import java.io.IOException;

/**
 * 接收帧基类
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/3 16:48
 */
public abstract class AbsReceiveFrame extends Frame {
    /**
     * 帧体可读写区域大小
     */
    volatile int bodyRemaining;

    AbsReceiveFrame(byte[] header) {
        super(header);
        bodyRemaining = getBodyLength();
    }

    @Override
    public synchronized boolean handle(IoArgs args) throws IOException {
        if (bodyRemaining == 0) {
            // 已读取所有数据
            return true;
        }

        bodyRemaining -= consumeBody(args);

        return bodyRemaining == 0;
    }

    @Override
    public final Frame nextFrame() {
        return null;
    }

    @Override
    public int getConsumableLength() {
        return bodyRemaining;
    }

    /**
     * 消费body剩余空间
     * @param args 用于操作的数据包
     * @return 已消费的body空间长度
     * @throws IOException IO异常
     */
    protected abstract int consumeBody(IoArgs args) throws IOException;
}
