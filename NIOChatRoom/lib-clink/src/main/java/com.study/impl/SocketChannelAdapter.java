package com.study.impl;

import com.study.core.IoArgs;
import com.study.core.IoProvider;
import com.study.core.Receiver;
import com.study.core.Sender;
import com.study.impl.exceptions.EmptyIoArgsException;
import com.study.utils.CloseUtils;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 发送和接收者的实现类
 * Created By Cx On 2019/3/30 18:22
 *
 * @author cxd27419
 */
public class SocketChannelAdapter implements Sender, Receiver, Closeable {
    /**
     * 是否被关闭
     */
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final SocketChannel channel;
    private final IoProvider ioProvider;
    private final OnChannelStatusChangedListener listener;

    private final AbsProviderCallback inputCallback;
    private final AbsProviderCallback outputCallback;

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider, OnChannelStatusChangedListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;
        this.inputCallback = new InputProviderCallback(channel, SelectionKey.OP_READ, ioProvider);
        this.outputCallback = new OutputProviderCallback(channel, SelectionKey.OP_WRITE, ioProvider);
    }

    @Override
    public void setReceiveListener(IoArgs.IoArgsEventProcessor processor) {
        inputCallback.eventProcessor = processor;
    }

    @Override
    public void setSendListener(IoArgs.IoArgsEventProcessor processor) {
        outputCallback.eventProcessor = processor;
    }

    @Override
    public long getLastReadTime() {
        return inputCallback.lastActiveTime;
    }

    @Override
    public long getLastWriteTime() {
        return outputCallback.lastActiveTime;
    }

    @Override
    public void postReceiveAsync() throws Exception {
        if (isClosed.get() || !channel.isOpen()) {
            throw new IOException("当前Channel已关闭");
        }
        //进行Callback状态检测，判断是否处于自循环,只有当attach为空时才不处于自循环，此时才能注册
        inputCallback.checkAttachNull();
        ioProvider.register(inputCallback);
    }

    @Override
    public void postSendAsync() throws Exception {
        if (isClosed.get() || !channel.isOpen()) {
            throw new IOException("当前Channel已关闭");
        }
        //进行Callback状态检测，判断是否处于自循环,只有当attach为空时才不处于自循环，此时才能注册
        outputCallback.checkAttachNull();
        ioProvider.register(outputCallback);
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            ioProvider.unregister(channel);
            CloseUtils.close(channel);
            //回调通知channel已关闭
            listener.onChannelClosed(channel);
        }
    }

    abstract class AbsProviderCallback extends IoProvider.HandleProviderCallback {

        volatile IoArgs.IoArgsEventProcessor eventProcessor;

        volatile long lastActiveTime = System.currentTimeMillis();

        AbsProviderCallback(SocketChannel channel, int ops, IoProvider ioProvider) {
            super(ioProvider, channel,ops);
        }

        @Override
        protected final boolean onProviderTo(IoArgs args) {
            if (isClosed.get()) {
                return false;
            }
            IoArgs.IoArgsEventProcessor processor = eventProcessor;
            if (processor == null) {
                return false;
            }
            //刷新最近活跃时间
            lastActiveTime = System.currentTimeMillis();
            args = (args == null ? processor.provideIoArgs() : args);
            try {
                if (args == null) {
                    throw new EmptyIoArgsException("提供的缓冲区为空");
                }

                int count = consumeIoArgs(args, channel);
                if (args.remained() && (count == 0 || args.isNeedConsumeRemaining())) {
                    attach = args;
                    return true;
                } else {
                    //写入完成回调
                    return processor.onConsumeCompleted(args);
                }
            } catch (IOException e) {
                if (processor.onConsumeFailed(e)) {
                    CloseUtils.close(SocketChannelAdapter.this);
                }
                return false;
            }
        }

        @Override
        public void fireThrowable(Throwable e) {
            final IoArgs.IoArgsEventProcessor eventProcessor = this.eventProcessor;
            if (eventProcessor == null || eventProcessor.onConsumeFailed(e)) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }

        /**
         * 消费IoArgs
         * @param args 需要消费的IoArgs
         * @param channel 消费的通道
         * @return 消费字节数
         * @throws IOException 异常
         */
        protected abstract int consumeIoArgs(IoArgs args, SocketChannel channel) throws IOException;
    }

    class InputProviderCallback extends AbsProviderCallback {
        InputProviderCallback(SocketChannel channel, int ops, IoProvider ioProvider) {
            super(channel, ops, ioProvider);
        }

        @Override
        protected int consumeIoArgs(IoArgs args, SocketChannel channel) throws IOException {
            return args.readFrom(channel);
        }
    }

    class OutputProviderCallback extends AbsProviderCallback {
        OutputProviderCallback(SocketChannel channel, int ops, IoProvider ioProvider) {
            super(channel, ops, ioProvider);
        }

        @Override
        protected int consumeIoArgs(IoArgs args, SocketChannel channel) throws IOException {
            return args.writeTo(channel);
        }
    }

    /**
     * 当Channel状态改变时进行回调
     */
    public interface OnChannelStatusChangedListener {
        /**
         * 当channel关闭时
         *
         * @param channel 通信通道
         */
        void onChannelClosed(SocketChannel channel);
    }
}
