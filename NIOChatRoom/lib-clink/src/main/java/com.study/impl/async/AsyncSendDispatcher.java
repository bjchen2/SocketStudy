package com.study.impl.async;

import com.study.core.IoArgs;
import com.study.core.SendDispatcher;
import com.study.core.SendPacket;
import com.study.core.Sender;
import com.study.impl.exceptions.EmptyIoArgsException;
import com.study.utils.CloseUtils;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步发送调度
 *
 * @author Cx
 * @version jdk8 and idea On 2019/4/25 12:08
 */
public class AsyncSendDispatcher implements SendDispatcher, IoArgs.IoArgsEventProcessor, AsyncPacketReader.PacketProvider {
    /**
     * 发送者
     */
    private final Sender sender;
    /**
     * 发送队列，非阻塞安全队列
     */
    private final BlockingQueue<SendPacket> queue = new ArrayBlockingQueue<>(16);
    /**
     * 是否处于发送状态
     */
    private final AtomicBoolean isSending = new AtomicBoolean();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final AsyncPacketReader reader = new AsyncPacketReader(this);

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
        sender.setSendListener(this);
    }

    @Override
    public void send(SendPacket packet) {
        try {
            queue.put(packet);
            requestSend(false);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendHeartbeat() {
        if (!queue.isEmpty()){
            //尽量不要使用queue.size() > 0，因为在有些队列中，size方法会遍历整个队列，效率低，可能快速失败
            //如果当前有数据发送，则不需要发送心跳帧，因为数据发送本身就是一种心跳检测
            return;
        }
        if (reader.requestSendHeartbeatFrame()){
            requestSend(false);
        }
    }

    @Override
    public void cancel(SendPacket packet) {
        boolean ret;
        ret = queue.remove(packet);
        if (ret){
            //该packet还未发送，已被取消
            packet.cancel();
            return;
        }
        //该packet可能已发送或者正在发送
        reader.cancel(packet);
    }

    @Override
    public SendPacket takePacket() {
        SendPacket packet = queue.poll();
        if (packet == null){
            return null;
        }
        else if (packet.isCanceled()) {
            //当前数据包已取消发送
            return takePacket();
        }
        return packet;
    }

    /**
     * 完成packet的发送
     *
     * @param isSucceed 是否正常完成
     */
    @Override
    public void completedPacket(SendPacket packet, boolean isSucceed) {
        CloseUtils.close(packet);
    }

    /**
     * 请求发送数据包
     * @param callFromToConsume 是否是io调度流程中来的，若为true则是发送失败时(onConsumeFailed)调度的
     */
    private void requestSend(boolean callFromToConsume) {
        synchronized (isSending) {
            final boolean oldState = isSending.get();
            if (isClosed.get() || (oldState && !callFromToConsume)) {
                return;
            }
            if (callFromToConsume && !oldState) {
                throw new IllegalStateException("");
            }
            if (reader.requestTakePacket()) {
                isSending.set(true);
                try {
                    sender.postSendAsync();
                } catch (Exception e) {
                    e.printStackTrace();
                    CloseUtils.close(this);
                }
            } else {
                isSending.set(false);
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            synchronized (isSending){
                isSending.set(false);
            }
            //异常关闭导致的完成
            reader.close();
            // 将队列清空，防止链接断开后，内存泄漏
            queue.clear();
        }
    }

    @Override
    public IoArgs provideIoArgs() {
        return isClosed.get() ? null :  reader.fillData();
    }

    @Override
    public boolean onConsumeFailed(Throwable e) {
        if (e instanceof EmptyIoArgsException){
            // 继续请求发送当前数据
            requestSend(true);
            return false;
        }else {
            CloseUtils.close(this);
            return true;
        }
    }

    @Override
    public boolean onConsumeCompleted(IoArgs args) {
        //如果还有帧未发送，继续请求发送
        synchronized (isSending) {
            final boolean running = !isClosed.get();
            if (!isSending.get() && running) {
                throw new IllegalStateException("");
            }
            //设置sending状态，主要判断是否还有数据需要发送
            isSending.set(running && reader.requestTakePacket());
            return isSending.get();
        }
    }
}
