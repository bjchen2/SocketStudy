package com.study.impl.brige;


import com.study.core.*;
import com.study.impl.exceptions.EmptyIoArgsException;
import com.study.utils.plugin.CircularByteBuffer;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 桥接调度器，实现桥接发送与接收逻辑
 * @author Cx
 * @version jdk8 and idea On 2019/5/15 17:48
 */
public class BridgeSocketDispatcher implements ReceiveDispatcher, SendDispatcher {

    /**
     * 数据暂存缓冲区,512字节
     * 当缓冲区写满时，继续写将阻塞（如果是false则会抛出异常）
     */
    private final CircularByteBuffer buffer = new CircularByteBuffer(512, true);

    private final ReadableByteChannel readableByteChannel = Channels.newChannel(buffer.getInputStream());
    private final WritableByteChannel writableByteChannel = Channels.newChannel(buffer.getOutputStream());

    /**
     * 接收数据的IoArgs，有数据则接收，不要求填满，有多少返回多少
     */
    private final IoArgs receiveIoArgs = new IoArgs(256, false);
    private final Receiver receiver;
    /**
     * 发送数据的IoArgs
     */
    private final IoArgs sendIoArgs = new IoArgs();
    private volatile Sender sender;

    /**
     * 当前是否处于发送
     */
    private final AtomicBoolean isSending = new AtomicBoolean(false);

    public BridgeSocketDispatcher(Receiver receiver) {
        this.receiver = receiver;
    }

    /**
     * 绑定一个新的发送者，在绑定时，将旧发送者对应调度设为null
     * @param sender 新发送者
     */
    public void bindSender(Sender sender) {
        final Sender oldSender = this.sender;
        if (oldSender != null) {
            oldSender.setSendListener(null);
        }

        synchronized (isSending) {
            isSending.set(false);
        }
        buffer.clear();

        this.sender = sender;
        if (sender != null) {
            sender.setSendListener(sendEventProcessor);
            requestSend();
        }
    }

    @Override
    public void start() {
        receiver.setReceiveListener(receiveEventProcessor);
        registerReceive();
    }

    private void requestSend() {
        synchronized (isSending) {
            if (isSending.get() || sender == null) {
                return;
            }

            //返回True代表当前有数据可读，即可以发送
            if (buffer.getAvailable() > 0) {
                try {
                    isSending.set(true);
                    sender.postSendAsync();
                } catch (Exception e) {
                    e.printStackTrace();
                    isSending.set(false);
                }
            }
        }
    }

    /**
     * 请求网络进行数据接收
     */
    private void registerReceive() {
        try {
            receiver.postReceiveAsync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final IoArgs.IoArgsEventProcessor sendEventProcessor = new IoArgs.IoArgsEventProcessor() {
        @Override
        public IoArgs provideIoArgs() {
            try {
                int available = buffer.getAvailable();
                IoArgs args = BridgeSocketDispatcher.this.sendIoArgs;
                if (available > 0) {
                    args.limit(available);
                    args.startWriting();
                    args.readFrom(readableByteChannel);
                    args.finishWriting();
                    return args;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public boolean onConsumeFailed(Throwable e) {
            if (e instanceof EmptyIoArgsException) {
                // 设置当前发送状态
                synchronized (isSending) {
                    isSending.set(false);
                    // 继续请求发送当前的数据
                    requestSend();
                }
                // 无需关闭链接
                return false;
            } else {
                // 关闭链接
                return true;
            }
        }

        @Override
        public boolean onConsumeCompleted(IoArgs ioArgs) {
            if(buffer.getAvailable() > 0){
                return true;
            }else{
                // 设置当前发送状态
                synchronized (isSending){
                    isSending.set(false);
                }
                // 继续请求发送当前的数据
                requestSend();
                return false;
            }
        }
    };

    private final IoArgs.IoArgsEventProcessor receiveEventProcessor = new IoArgs.IoArgsEventProcessor() {
        @Override
        public IoArgs provideIoArgs() {
            receiveIoArgs.resetLimit();
            receiveIoArgs.startWriting();
            return receiveIoArgs;
        }

        @Override
        public boolean onConsumeFailed(Throwable e) {
            new RuntimeException(e).printStackTrace();
            return true;
        }

        @Override
        public boolean onConsumeCompleted(IoArgs args) {
            args.finishWriting();
            try {
                args.writeTo(writableByteChannel);
                //接收到数据后直接请求发送数据
                requestSend();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    };

    @Override
    public void stop() {
    }

    @Override
    public void send(SendPacket packet) {
    }

    @Override
    public void sendHeartbeat() {
    }

    @Override
    public void cancel(SendPacket packet) {
    }

    @Override
    public void close() throws IOException {
    }
}