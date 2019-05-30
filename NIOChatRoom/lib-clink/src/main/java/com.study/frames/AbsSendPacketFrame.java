package com.study.frames;


import com.study.core.Frame;
import com.study.core.IoArgs;
import com.study.core.SendPacket;

import java.io.IOException;

/**
 * @author Cx
 * @version jdk8 and idea On 2019/5/3 16:48
 */
public abstract class AbsSendPacketFrame extends AbsSendFrame {
    protected volatile SendPacket<?> packet;

    public AbsSendPacketFrame(int length, byte type, byte flag, short identifier, SendPacket packet) {
        super(length, type, flag, identifier);
        this.packet = packet;
    }

    /**
     * 获取当前对应的发送Packet
     *
     * @return SendPacket
     */
    public synchronized SendPacket getPacket() {
        return packet;
    }

    @Override
    public synchronized boolean handle(IoArgs args) throws IOException {
        if (packet == null && !isSending()){
            //已取消，并且未发送数据，直接返回结束，发送下一帧
            return true;
        }
        return super.handle(args);
    }

    @Override
    public final synchronized Frame nextFrame() {
        return packet == null? null : buildNextFrame();
    }

    /**
     * 构建下一帧
     * @return 下一帧
     */
    protected abstract Frame buildNextFrame();

    /**
     * 中止发送；abort : 中止
     * @return true：当前帧没发送任何数据
     */
    public final synchronized boolean abort(){
        boolean isSending = isSending();
        if (isSending){
            //填充假数据
            fillDirtyDataOnAbort();
        }
        packet = null;
        return !isSending;
    }

    /**
     * 在中止时填充假数据
     */
    protected void fillDirtyDataOnAbort(){}
}
