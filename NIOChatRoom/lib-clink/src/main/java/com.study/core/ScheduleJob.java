package com.study.core;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 任务基类
 * @author Cx
 * @version jdk8 and idea On 2019/5/14 9:47
 */
public abstract class ScheduleJob implements Runnable {
    /**
     * 可允许空闲时间,单位：毫秒，如果最近活跃时间至今超出可允许空闲时间，则启动该定时任务
     * idle:闲置的
     */
    protected final long idleTimeoutMilliseconds;
    /**
     * 当前连接
     */
    protected final Connector connector;
    private volatile Scheduler scheduler;
    private volatile ScheduledFuture scheduledFuture;

    protected ScheduleJob(long idleTimeoutMilliseconds,TimeUnit unit, Connector connector) {
        this.idleTimeoutMilliseconds = unit.toMillis(idleTimeoutMilliseconds);
        this.connector = connector;
    }

    /**
     * 执行该定时任务，由外部Connector调用
     * @param scheduler 定时任务执行者
     */
    synchronized void scheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
        schedule(idleTimeoutMilliseconds);
    }

    /**
     * 取消执行定时任务
     */
    synchronized void unSchedule(){
        scheduler = null;
        if (scheduledFuture != null){
            //取消任务执行，允许中断
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
    }

    /**
     * 执行该定时任务，提供给子类和自己调用
     */
    protected void schedule(long timeoutMilliseconds){
        if (scheduler != null){
            scheduler.schedule(this,timeoutMilliseconds, TimeUnit.MILLISECONDS);
        }
    }

}
