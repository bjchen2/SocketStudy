package com.study.core;

import lombok.Builder;
import lombok.Getter;

import java.io.IOException;

/**
 * IO上下文，存放一些全局的变量
 *
 * @author cxd27419
 */
public class IoContext {
    private static IoContext INSTANCE;
    /**
     * 注册提供者
     */
    private final IoProvider ioProvider;
    private final Scheduler scheduler;

    private IoContext(IoProvider ioProvider, Scheduler scheduler) {
        this.ioProvider = ioProvider;
        this.scheduler = scheduler;
    }

    public IoProvider getIoProvider() {
        return ioProvider;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public static IoContext get() {
        return INSTANCE;
    }

    public static StartedBoot setup() {
        return new StartedBoot();
    }

    public static void close() throws IOException {
        if (INSTANCE != null) {
            INSTANCE.callClose();
        }
    }

    private void callClose() throws IOException {
        ioProvider.close();
        scheduler.close();
    }

    public static class StartedBoot {
        private IoProvider ioProvider;
        private Scheduler scheduler;

        public StartedBoot ioProvider(IoProvider ioProvider) {
            this.ioProvider = ioProvider;
            return this;
        }

        public StartedBoot scheduler(Scheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public IoContext start() {
            INSTANCE = new IoContext(ioProvider, scheduler);
            return INSTANCE;
        }
    }
}
