package com.study.core;


import com.study.box.*;
import com.study.impl.SocketChannelAdapter;
import com.study.impl.async.AsyncReceiveDispatcher;
import com.study.impl.async.AsyncSendDispatcher;
import com.study.impl.brige.BridgeSocketDispatcher;
import com.study.utils.CloseUtils;
import lombok.Getter;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 连接器,负责通过channel读或者写数据
 *
 * @author cxd27419
 */
public abstract class Connector implements Closeable, SocketChannelAdapter.OnChannelStatusChangedListener {
    @Getter
    protected UUID key = UUID.randomUUID();
    private SocketChannel channel;
    private Sender sender;
    private Receiver receiver;
    private SendDispatcher sendDispatcher;
    private ReceiveDispatcher receiveDispatcher;
    /**
     * 定时任务列表
     */
    private final List<ScheduleJob> scheduleJobs = new ArrayList<>(4);

    public void setup(SocketChannel socketChannel) throws IOException {
        this.channel = socketChannel;

        socketChannel.configureBlocking(false);
        socketChannel.socket().setSoTimeout(1000);
        socketChannel.socket().setPerformancePreferences(1, 3, 3);
        socketChannel.setOption(StandardSocketOptions.SO_SNDBUF, 16 * 1024);
        socketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 16 * 1024);
        socketChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
        socketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);

        IoContext context = IoContext.get();
        SocketChannelAdapter adapter = new SocketChannelAdapter(channel, context.getIoProvider(), this);

        this.sender = adapter;
        this.receiver = adapter;

        sendDispatcher = new AsyncSendDispatcher(sender);
        receiveDispatcher = new AsyncReceiveDispatcher(receiver, receivePacketCallback);

        receiveDispatcher.start();
    }

    public void send(SendPacket packet) {
        sendDispatcher.send(packet);
    }

    public void send(String str) {
        send(new StringSendPacket(str));
    }

    /**
     * 启动定时任务
     * @param job 定时任务
     */
    public void schedule(ScheduleJob job){
        synchronized (scheduleJobs){
            if (scheduleJobs.contains(job)){
                return;
            }
            Scheduler scheduler = IoContext.get().getScheduler();
            job.scheduler(scheduler);
            scheduleJobs.add(job);
        }
    }

    /**
     * 发送超时事件
     */
    public void fireIdleTimeoutEvent() {
        sendDispatcher.sendHeartbeat();
    }

    /**
     * 发送失败捕获
     * @param throwable 异常
     */
    public void fireExceptionCaught(Throwable throwable) {
    }


    /**
     * 获取最近一次发送/接收数据的时间
     */
    public long getLastActiveTime(){
        return Math.max(sender.getLastWriteTime(),receiver.getLastReadTime());
    }

    @Override
    public void close() throws IOException {
        CloseUtils.close(receiveDispatcher, sendDispatcher, sender, receiver, channel);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        synchronized (scheduleJobs){
            for (ScheduleJob scheduleJob : scheduleJobs){
                scheduleJob.unSchedule();
            }
            scheduleJobs.clear();
        }
        CloseUtils.close(this);
    }

    /**
     * 当接收数据包完成时，打印数据包信息
     *
     * @param packet 接收的数据包
     */
    protected void onReceivedPacket(ReceivePacket packet) {
//        System.out.println(key.toString() + ":[New Packet]-Type:" + packet.type() + ", Length:" + packet.length);
    }

    /**
     * 创建用于接收文件的File，延迟到业务层实现，由业务层控制接收的文件应该存在哪里，而不该由框架设定
     *
     * @param length     将要接收的数据长度
     * @param headerInfo 额外信息
     * @return 返回用于接收的File
     */
    protected abstract File createNewReceiveFile(long length, byte[] headerInfo);

    /**
     * 创建用于传输的输出流，延迟到业务层实现，由业务层控制接收的数据应该传输到哪里，而不该由框架设定
     * 当接受包是直流数据报时，所有接收数据都将通过输出流输出
     *
     * @param length     将要接收的数据长度
     * @param headerInfo 额外信息
     * @return 用于接收数据传输的输出流
     */
    protected abstract OutputStream createNewReceiveDirectOutputStream(long length, byte[] headerInfo);

    /**
     * 更换当前调度器为桥接模式
     */
    public void changeToBridge() {
        if (receiveDispatcher instanceof BridgeSocketDispatcher) {
            return;
        }
        receiveDispatcher.stop();
        BridgeSocketDispatcher dispatcher = new BridgeSocketDispatcher(receiver);
        receiveDispatcher = dispatcher;
        dispatcher.start();
    }

    /**
     * 将另一个链接的发送者绑定到当前链接的桥接调度器，实现两个链接桥接功能
     * @param sender 另一个链接的发送者
     */
    public void bindToBridge(Sender sender) {
        if (sender == this.sender) {
            throw new UnsupportedOperationException("不能连接自己");
        }
        if (!(receiveDispatcher instanceof BridgeSocketDispatcher)) {
            throw new IllegalStateException("receiveDispatcher 不是桥接调度");
        }
        ((BridgeSocketDispatcher) receiveDispatcher).bindSender(sender);
    }

    /**
     * 将之前连接的发送者解绑
     */
    public void unBindToBridge() {
        if (!(receiveDispatcher instanceof BridgeSocketDispatcher)) {
            throw new IllegalStateException("receiveDispatcher 不是桥接调度");
        }
        ((BridgeSocketDispatcher) receiveDispatcher).bindSender(null);
    }

    public Sender getSender() {
        return sender;
    }

    /**
     * 当收到一个新的packet时进行回调
     */
    private ReceiveDispatcher.ReceivePacketCallback receivePacketCallback = new ReceiveDispatcher.ReceivePacketCallback() {
        @Override
        public ReceivePacket<?, ?> onNewPacketArrived(byte type, long length, byte[] headerInfo) {
            switch (type) {
                case Packet.TYPE_MEMORY_BYTES:
                    return new BytesReceivePacket(length);
                case Packet.TYPE_MEMORY_STRING:
                    return new StringReceivePacket(length);
                case Packet.TYPE_STREAM_FILE:
                    return new FileReceivePacket(length, createNewReceiveFile(length,headerInfo));
                case Packet.TYPE_STREAM_DIRECT:
                    return new StreamDirectReceivePacket(createNewReceiveDirectOutputStream(length,headerInfo),length);
                default:
                    throw new UnsupportedOperationException("Unsupported packet type:" + type);
            }
        }

        @Override
        public void onReceivePacketCompleted(ReceivePacket packet) {
            onReceivedPacket(packet);
        }

        @Override
        public void onReceiveHeartbeat() {
            System.out.println(key + "：收到一个心跳检测帧");
        }
    };
}
