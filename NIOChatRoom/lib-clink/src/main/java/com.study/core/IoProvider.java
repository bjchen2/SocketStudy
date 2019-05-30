package com.study.core;

import java.io.Closeable;
import java.nio.channels.SocketChannel;

/**
 * 观察者模式的提供者（提供注册功能）
 * @author cxd27419
 */
public interface IoProvider extends Closeable {
    /**
     * 注册输入/输出，观察channel是否可读/写，使用callback进行回调
     * @param callback 需要注册的任务
     * @throws Exception
     */
    void register(HandleProviderCallback callback) throws Exception;

    /**
     * 取消注册输入/输出
     * @param channel 需要解绑的通道
     */
    void unregister(SocketChannel channel);

    /**
     * 读/写执行回调类
     */
    abstract class HandleProviderCallback extends IoTask implements Runnable {
        private final IoProvider ioProvider;
        protected volatile IoArgs attach = null;

        public HandleProviderCallback(IoProvider ioProvider , SocketChannel channel, int ops) {
            super(channel, ops);
            this.ioProvider = ioProvider;
        }

        @Override
        public final void run() {
            final IoArgs attach = this.attach;
            this.attach = null;
            if (onProviderTo(attach)){
                //如果需要继续调度，再次注册
                try {
                    ioProvider.register(this);
                } catch (Exception e) {
                    //注册失败，抛出异常
                    fireThrowable(e);
                }
            }
        }

        @Override
        public final boolean onProcessIo() {
            final IoArgs attach = this.attach;
            this.attach = null;
            return onProviderTo(attach);
        }

        /**
         * 当前能够进行读/写操作时的回调
         * @param attach 携带之前的附加值
         * @return 是否需要后续继续调度
         */
        protected abstract boolean onProviderTo(IoArgs attach);

        public void checkAttachNull(){
            if (attach != null){
                throw new IllegalStateException(this.getClass() + "[checkAttachNull]当前attach不是空");
            }
        }
    }

}
