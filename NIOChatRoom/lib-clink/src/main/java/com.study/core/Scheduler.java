package com.study.core;

import java.io.Closeable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 任务调度接口
 * @author Cx
 * @version jdk8 and idea On 2019/5/13 21:58
 */
public interface Scheduler extends Closeable {

    /**
     * 执行延迟定时任务
     * @param runnable 需要执行的任务
     * @param delay 执行延时时长
     * @param timeUnit delay单位
     * @return 任务执行状态
     */
    ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit timeUnit);

    /**
     * 执行一个任务
     * @param runnable 需要执行的任务
     */
    void delivery(Runnable runnable);
}
