package com.study.impl.async;

import com.study.core.*;
import com.study.utils.CloseUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步接收调度者
 *
 * @author Cx
 * @version jdk8 and idea On 2019/4/25 20:51
 */
public class AsyncReceiveDispatcher implements ReceiveDispatcher, IoArgs.IoArgsEventProcessor, AsyncPacketWriter.PacketProvider {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final Receiver receiver;
    private final ReceivePacketCallback callback;

    private final AsyncPacketWriter writer = new AsyncPacketWriter(this);

    public AsyncReceiveDispatcher(Receiver receiver, ReceivePacketCallback callback) {
        this.receiver = receiver;
        receiver.setReceiveListener(this);
        this.callback = callback;
    }

    @Override
    public void start() {
        registerReceive();
    }

    @Override
    public void stop() {
        receiver.setReceiveListener(null);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            writer.close();
            receiver.setReceiveListener(null);
        }
    }

    /**
     * 异步等待接收下一条消息
     */
    private void registerReceive() {
        try {
            receiver.postReceiveAsync();
        } catch (Exception e) {
            e.printStackTrace();
            CloseUtils.close(this);
        }
    }

    @Override
    public IoArgs provideIoArgs() {
        IoArgs ioArgs = writer.takeIoArgs();
        //提供前，先调用开始写
        ioArgs.startWriting();
        return ioArgs;
    }

    @Override
    public boolean onConsumeFailed(Throwable e) {
        CloseUtils.close(this);
        return true;
    }

    @Override
    public boolean onConsumeCompleted(IoArgs args) {
        args.finishWriting();
        do {
            writer.consumeIoArgs(args);
        }while(args.remained() && !isClosed.get());

        return !isClosed.get();
    }

    @Override
    public ReceivePacket takePacket(byte type, long length, byte[] headerInfo) {
        return callback.onNewPacketArrived(type, length, headerInfo);
    }

    @Override
    public void completedPacket(ReceivePacket packet, boolean isSucceed) {
        CloseUtils.close(packet);
        callback.onReceivePacketCompleted(packet);
    }

    @Override
    public void onReceiveHeartbeat() {
        callback.onReceiveHeartbeat();
    }
}
