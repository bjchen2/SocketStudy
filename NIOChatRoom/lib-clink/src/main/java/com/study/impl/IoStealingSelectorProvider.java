package com.study.impl;

import com.study.core.IoProvider;
import com.study.core.IoTask;
import com.study.impl.stealing.StealingSelectorThread;
import com.study.impl.stealing.StealingService;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * 可窃取任务的IoProvider
 * @author Cx
 * @version jdk8 and idea On 2019/5/26 20:10
 */
public class IoStealingSelectorProvider implements IoProvider {

    private final StealingService stealingService;
    private final IoStealingThread[] threads;

    public IoStealingSelectorProvider(int poolSize) throws IOException {
        threads = new IoStealingThread[poolSize];
        for (int i = 0; i < poolSize; i++) {
            Selector selector = Selector.open();
            threads[i] = new IoStealingThread("IoStealingProvider-Thread-" + (i + 1), selector);
        }
        StealingService stealingService = new StealingService(10, threads);
        for (IoStealingThread thread : threads) {
            thread.setStealingService(stealingService);
            thread.setDaemon(false);
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.start();
        }
        this.stealingService = stealingService;
    }

    @Override
    public void register(HandleProviderCallback callback) throws Exception {
        stealingService.register(callback);
    }

    @Override
    public void unregister(SocketChannel channel) {
        if (channel.isOpen()) {
            stealingService.unregister(channel);
        }
    }

    @Override
    public void close() throws IOException {
        stealingService.shutdown();
    }

    static class IoStealingThread extends StealingSelectorThread {
        public IoStealingThread(String name, Selector selector) {
            super(selector);
            //设置当前线程名称
            setName(name);
        }
        @Override
        protected boolean processTask(IoTask task) {
            return task.onProcessIo();
        }
    }
}
