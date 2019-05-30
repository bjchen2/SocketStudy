package com.study.core;

import com.study.core.IoProvider;

import java.nio.channels.SocketChannel;

/**
 * 可窃取Io任务
 * @author Cx
 * @version jdk8 and idea On 2019/5/26 20:05
 */
public abstract class IoTask {
    /**
     * 任务使用通道
     */
    public final SocketChannel channel;
    /**
     * 需要注册任务类型
     */
    public final int ops;

    public IoTask(SocketChannel channel, int ops) {
        this.channel = channel;
        this.ops = ops;
    }

    public abstract boolean onProcessIo();

    /**
     * 抛出异常
     * @param e 需要抛出的异常
     */
    public abstract void fireThrowable(Throwable e);
}
