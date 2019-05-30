package com.study.core;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author cxd27419
 */
public interface Receiver extends Closeable {
    /**
     * 设置IoArgs处理回调
     *
     * @param processor 回调对象
     */
    void setReceiveListener(IoArgs.IoArgsEventProcessor processor);

    /**
     * 异步接收消息
     * @throws Exception IO异常
     */
    void postReceiveAsync() throws Exception;

    /**
     * 获取最近一次接收数据的时间
     * @return 最近一次接收数据的时间戳
     */
    long getLastReadTime();
}
