package com.study.core;

import java.io.Closeable;

/**
 * 发送数据的调度者
 * 缓存所有需要发送的数据，通过队列对数据进行发送，发送时，对数据进行基本转换处理
 * 因为Sender需要接收的参数是IoArgs，而业务层提供的是SenderPacket，所以需要该类进行一个转换处理
 *
 * @author Cx
 * @version jdk8 and idea On 2019/4/25 11:29
 */
public interface SendDispatcher extends Closeable {
    /**
     * 发送一份数据
     *
     * @param packet 需要发送的数据包
     */
    void send(SendPacket packet);

    /**
     * 发送一份心跳帧
     */
    void sendHeartbeat();

    /**
     * 取消发送数据
     *
     * @param packet 需要取消发送的数据包
     */
    void cancel(SendPacket packet);
}
