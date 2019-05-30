package com.study.frames;

import com.study.core.Frame;
import com.study.core.IoArgs;

import java.io.IOException;

/**
 * 发送帧基类
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/3 16:48
 */
public abstract class AbsSendFrame extends Frame {
    /**
     * 头部还有多少字节没使用
     */
    volatile byte headerRemaining = Frame.FRAME_HEADER_LENGTH;
    /**
     * 消息体还有多少字节没设置
     */
    int bodyRemaining;

    AbsSendFrame(int length, byte type, byte flag, short identifier) {
        super(length, type, flag, identifier);
        bodyRemaining = length;
    }

    AbsSendFrame(byte[] header) {
        super(header);
    }

    @Override
    public synchronized boolean handle(IoArgs args) throws IOException {
        args.limit(headerRemaining + bodyRemaining);
        args.startWriting();
        try {
            if (headerRemaining > 0 && args.remained()){
                //如果头部未消费完，且缓冲区还有剩余，则消费头部
                headerRemaining -= consumeHeader(args);
            }
            if (headerRemaining == 0 && args.remained() && bodyRemaining > 0){
                //如果头部已消费完，且缓冲区有剩余，则消费body
                bodyRemaining -= consumeBody(args);
            }
            return headerRemaining == 0 && bodyRemaining == 0;
        }finally {
            args.finishWriting();
        }
    }

    @Override
    public int getConsumableLength() {
        return headerRemaining + bodyRemaining;
    }

    /**
     * 消费头部剩余空间
     * @param args 用于操作的数据包
     * @return 已消费的头部空间长度
     * @throws IOException IO异常
     */
    private byte consumeHeader(IoArgs args) throws IOException{
        int count = headerRemaining;
        int offset = header.length - headerRemaining;
        //进行消费
        return (byte) args.readFrom(header, offset, count);
    }

    /**
     * 消费body剩余空间
     * @param args 用于操作的数据包
     * @return 已消费的body空间长度
     * @throws IOException IO异常
     */
    protected abstract int consumeBody(IoArgs args) throws IOException;

    /**
     * 该帧是否在发送中,需同步保证headerRemaining可见性
     */
    protected synchronized boolean isSending(){
        return headerRemaining < Frame.FRAME_HEADER_LENGTH;
    }
}
