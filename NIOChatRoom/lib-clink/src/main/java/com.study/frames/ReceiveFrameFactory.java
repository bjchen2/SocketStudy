package com.study.frames;

import com.study.core.Frame;
import com.study.core.IoArgs;

/**
 * 接收帧创建工厂
 * @author Cx
 * @version jdk8 and idea On 2019/5/5 23:26
 */
public class ReceiveFrameFactory {
    /**
     * 创建一个接收帧
     * @param args 该帧需要接收的数据
     * @return 接收帧
     */
    public static AbsReceiveFrame createInstance(IoArgs args){
        //获取消息头
        byte[] buffer = new byte[Frame.FRAME_HEADER_LENGTH];
        args.writeTo(buffer,0);
        //根据消息头指定type创建接收帧
        byte type = buffer[2];
        switch (type){
            case Frame.TYPE_PACKET_HEADER:
                return new ReceiveHeaderFrame(buffer);
            case Frame.TYPE_PACKET_ENTITY:
                return new ReceiveEntityFrame(buffer);
            case Frame.TYPE_COMMAND_SEND_CANCEL:
                return new CancelReceiveFrame(buffer);
            case Frame.TYPE_COMMAND_HEARTBEAT:
                return HeartbeatReceiveFrame.INSTANCE;
            default:
                throw new UnsupportedOperationException("Unsupported frame type:" + type);
        }
    }
}
