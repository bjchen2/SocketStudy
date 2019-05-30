package com.study.impl;

import com.study.core.Scheduler;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * 任务调度器实现类
 * @author Cx
 * @version jdk8 and idea On 2019/5/14 9:43
 */
public class SchedulerImpl implements Scheduler {
    /**
     * 延迟定时任务线程池
     */
    private final ScheduledExecutorService scheduledExecutorService;
    /**
     * 普通任务线程池
     */
    private final ExecutorService deliveryPool;

    public SchedulerImpl(int poolSize) {
        this.scheduledExecutorService = Executors.newScheduledThreadPool(poolSize,
                new NameableThreadFactory("Scheduler-Thread-"));
        this.deliveryPool = Executors.newFixedThreadPool(4,
                new NameableThreadFactory("Delivery-Thread-"));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit timeUnit) {
        return scheduledExecutorService.schedule(runnable, delay, timeUnit);
    }

    @Override
    public void delivery(Runnable runnable) {
        deliveryPool.execute(runnable);
    }

    @Override
    public void close() throws IOException {
        scheduledExecutorService.shutdownNow();
        deliveryPool.shutdownNow();
    }
}
