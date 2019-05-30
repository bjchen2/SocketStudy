package com.study.core.schedule;

import com.study.core.Connector;
import com.study.core.ScheduleJob;

import java.util.concurrent.TimeUnit;

/**
 * 空闲超时定时任务
 * @author Cx
 * @version jdk8 and idea On 2019/5/14 11:08
 */
public class IdleTimeoutScheduleJob extends ScheduleJob {

    public IdleTimeoutScheduleJob(long idleTimeoutMilliseconds, TimeUnit unit, Connector connector) {
        super(idleTimeoutMilliseconds, unit, connector);
    }

    @Override
    public void run() {
        long lastActiveTime = connector.getLastActiveTime();
        //下次调度任务的延迟时间
        long nextDelay = idleTimeoutMilliseconds - (System.currentTimeMillis() - lastActiveTime);
        if (nextDelay <= 0){
            //已超时，发送超时事件
            schedule(idleTimeoutMilliseconds);
            try {
                connector.fireIdleTimeoutEvent();
            }catch (Throwable throwable){
                connector.fireExceptionCaught(throwable);
            }
        }else {
            schedule(nextDelay);
        }
    }
}
