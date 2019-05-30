package com.study.frames;

import com.study.core.Frame;
import com.study.core.IoArgs;

/**
 * 取消发送帧，用于标志某Packet取消进行发送数据
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/3 16:47
 */
public class CancelSendFrame extends AbsSendFrame {
    /**
     * 取消帧的发送
     * @param identifier 需要取消的帧的唯一标识
     */
    public CancelSendFrame(short identifier) {
        super(0, Frame.TYPE_COMMAND_SEND_CANCEL, Frame.FLAG_NONE, identifier);
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
