package com.study.impl.stealing;

import com.study.core.IoProvider;
import com.study.core.IoTask;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.IntFunction;

/**
 * 窃取调度任务
 * @author cxd27419
 * @date 2019/5/29
 */
public class StealingService {
    /**
     * 当任务队列数量低于安全值时,不可窃取
     */
    private final int minSafetyThreshold;
    /**
     * 结束标示
     */
    private volatile boolean isTerminated;
    /**
     * 线程集合
     */
    private final StealingSelectorThread[] threads;
    /**
     * 对应的任务队列
     */
    private final Queue<IoTask>[] queues;


    public StealingService(int minSafetyThreshold, StealingSelectorThread[] threads) {
        this.minSafetyThreshold = minSafetyThreshold;
        this.threads = threads;
        this.queues = Arrays.stream(threads)
                .map(StealingSelectorThread::getReadyTaskQueue)
                .toArray((IntFunction<Queue<IoTask>[]>) ArrayBlockingQueue[]::new);
    }

    /**
     * 窃取一个任务,需排除自己,从他人的队列窃取一个任务
     * @param excludedQueue 不窃取的队列
     * @return 窃取成功返回实例, 失败返回null
     */
    IoTask steal(final Queue<IoTask> excludedQueue) {
        final int minSafetyThreshold = this.minSafetyThreshold;
        final Queue<IoTask>[] queues = this.queues;
        for (Queue<IoTask> queue : queues) {
            if (queue == excludedQueue) {
                continue;
            }
            int size = queue.size();
            if (size > minSafetyThreshold) {
                IoTask poll = queue.poll();
                if (poll != null) {
                    return poll;
                }
            }
        }
        return null;
    }

    /**
     * @return 获取一个最不繁忙的线程（即完成任务最少的线程）
     */
    public StealingSelectorThread getNotBusyThread() {
        StealingSelectorThread targetThread = null;
        long targetKeyCount = Long.MAX_VALUE;
        for (StealingSelectorThread thread : threads) {
            long saturatingCapacity = thread.getSaturatingCapacity();
            if (saturatingCapacity != -1 && saturatingCapacity < targetKeyCount) {
                targetKeyCount = saturatingCapacity;
                targetThread = thread;
            }
        }
        return targetThread;
    }

    public void register(IoProvider.HandleProviderCallback callback) throws IOException {
        StealingSelectorThread notBusyThread = getNotBusyThread();
        if (notBusyThread == null) {
            throw new IOException("IoStealingSelectorProvider is shutdown!");
        }
        notBusyThread.register(callback);
    }

    public void unregister(SocketChannel channel) {
        for (StealingSelectorThread thread : threads) {
            thread.unregister(channel);
        }
    }

    /**
     * 结束操作
     */
    public void shutdown() {
        if (isTerminated) {
            return;
        }
        isTerminated = true;
        for (StealingSelectorThread thread : threads) {
            thread.exit();
        }
    }
}
