package com.study.core;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author cxd27419
 */
public interface Sender extends Closeable {

    /**
     * 设置IoArgs处理回调
     *
     * @param processor 回调对象
     */
    void setSendListener(IoArgs.IoArgsEventProcessor processor);

    /**
     * 异步发送消息
     *
     * @return 是否发送成功
     * @throws Exception 异常
     */
    void postSendAsync() throws Exception;

    /**
     * 获取最近一次发送数据的时间
     * @return 最近一次发送数据的时间戳
     */
    long getLastWriteTime();
}
