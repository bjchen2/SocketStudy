package com.study.core;

import java.io.Closeable;

/**
 * 接收数据的调度者
 * 把一份或多份IoArgs组合成一份Packet
 *
 * @author Cx
 * @version jdk8 and idea On 2019/4/25 11:29
 */
public interface ReceiveDispatcher extends Closeable {
    /**
     * 开始接收数据
     */
    void start();

    /**
     * 结束接收数据
     */
    void stop();

    /**
     * 接收数据回调
     */
    interface ReceivePacketCallback {
        /**
         * 当有新消息到达时，返回用于处理的接收包
         *
         * @param type   消息类型
         * @param length 消息长度
         * @param headerInfo 头信息
         * @return 用于接收数据的接受包
         */
        ReceivePacket<?, ?> onNewPacketArrived(byte type, long length, byte[] headerInfo);

        /**
         * 当接收数据完成时
         *
         * @param packet 接收数据包
         */
        void onReceivePacketCompleted(ReceivePacket packet);

        /**
         * 当接收到心跳帧时
         */
        void onReceiveHeartbeat();
    }
}
