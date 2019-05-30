package com.study.impl.async;

import com.study.core.Frame;
import com.study.core.IoArgs;
import com.study.core.ReceivePacket;
import com.study.frames.*;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;
import java.util.HashMap;

/**
 * 异步读取客户端的Frame写入Packet
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/3 16:43
 */
class AsyncPacketWriter implements Closeable {
    private final PacketProvider provider;
    private final HashMap<Short,PacketModel> packetMap = new HashMap<>();
    private final IoArgs args = new IoArgs();
    private volatile Frame frameTemp;

    public AsyncPacketWriter(PacketProvider provider) {
        this.provider = provider;
    }

    /**
     * 构建一份数据包容纳数据
     * 当前帧如果没有则返回至少6字节长度的IoArgs，
     * 如果当前帧有，则返回当前帧未消费完成的区间
     * @return 构建的IoArgs
     */
    synchronized IoArgs takeIoArgs() {
        args.limit(frameTemp == null?Frame.FRAME_HEADER_LENGTH : frameTemp.getConsumableLength());
        return args;
    }

    /**
     * 消费数据
     * @param args 需要消费的数据
     */
    synchronized void consumeIoArgs(IoArgs args) {
        if (frameTemp == null){
            //如果当前帧为空，先构建数据
            Frame temp;
            do {
                //如果还能构建数据，且temp为空，则构建数据
                temp = buildNewFrame(args);
            }while (temp == null && args.remained());
            if (temp == null){
                return;
            }
            frameTemp = temp;
            if (!args.remained()){
                return;
            }
        }

        //开始消费数据
        Frame currentFrame = frameTemp;
        do {
            try {
                if (currentFrame.handle(args)){
                    if (currentFrame instanceof ReceiveHeaderFrame){
                        ReceiveHeaderFrame headerFrame = (ReceiveHeaderFrame) currentFrame;
                        ReceivePacket packet = provider.takePacket(headerFrame.getPacketType(),headerFrame.getPacketLength(),
                                headerFrame.getPacketHeaderInfo());
                        appendNewPacket(headerFrame.getBodyIdentifier(),packet);
                    }else if (currentFrame instanceof ReceiveEntityFrame){
                        completeEntityFrame((ReceiveEntityFrame) currentFrame);
                    }
                    //消费结束，将保存帧置空并退出
                    frameTemp = null;
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }while (args.remained());

    }

    private void completeEntityFrame(ReceiveEntityFrame frame) {
        short identifier = frame.getBodyIdentifier();
        int length = frame.getBodyLength();
        synchronized(packetMap) {
            PacketModel model = packetMap.get(identifier);
            if (model == null){
                return;
            }
            model.unreceivedLength -= length;
            if (model.unreceivedLength <= 0) {
                provider.completedPacket(model.packet, true);
                packetMap.remove(identifier);
            }
        }
    }

    private void appendNewPacket(short identifier, ReceivePacket packet) {
        PacketModel model = new PacketModel(packet);
        synchronized(packetMap) {
            packetMap.put(identifier, model);
        }
    }

    /**
     * 根据args创建新的帧
     * 若当前解析的帧是取消帧，则直接进行取消操作，并返回null
     *
     * @param args IoArgs
     * @return 返回新的帧
     */
    private Frame buildNewFrame(IoArgs args) {
        AbsReceiveFrame frame = ReceiveFrameFactory.createInstance(args);
        if (frame instanceof ReceiveEntityFrame){
            WritableByteChannel channel = getPacketChannel(frame.getBodyIdentifier());
            ((ReceiveEntityFrame) frame).bindPacketChannel(channel);
        }
        else if (frame instanceof CancelReceiveFrame){
            cancelReceivePacket(frame.getBodyIdentifier());
            return null;
        }
        else if (frame instanceof  HeartbeatReceiveFrame){
            provider.onReceiveHeartbeat();
            return null;
        }
        return frame;
    }

    /**
     * 获取Packet对应的输出通道，用以设置给帧进行数据传输
     * 因为关闭当前map的原因，可能存在返回NULL
     *
     * @param identifier Packet对应的标志
     * @return 通道
     */
    private WritableByteChannel getPacketChannel(short identifier) {
        synchronized(packetMap) {
            PacketModel model = packetMap.get(identifier);
            return model == null? null : model.channel;
        }
    }

    private void cancelReceivePacket(short identifier) {
        PacketModel model;
        synchronized(packetMap) {
            model = packetMap.get(identifier);
        }
        if (model != null){
            ReceivePacket packet = model.packet;
            provider.completedPacket(packet,false);
        }
    }

    /**
     * 关闭操作，关闭时若当前还有正在接收的Packet，则尝试停止对应的Packet接收
     */
    @Override
    public void close() throws IOException {
        synchronized(packetMap){
            Collection<PacketModel> values = packetMap.values();
            for (PacketModel model : values){
                provider.completedPacket(model.packet,false);
            }
            packetMap.clear();
        }
    }

    interface PacketProvider{
        /**
         * 获取下一个可接收数据包
         *
         * @param type       Packet类型
         * @param length     Packet长度
         * @param headerInfo Packet headerInfo
         * @return 通过类型，长度，描述等信息得到一份接收Packet
         */
        ReceivePacket takePacket(byte type,long length,byte[] headerInfo);

        /**
         * 完成packet接收
         * @param packet 完成接收的packet
         * @param isSucceed 是否为成功接收
         */
        void completedPacket(ReceivePacket packet, boolean isSucceed);

        /**
         * 当心跳包到达时
         */
        void onReceiveHeartbeat();
    }

    static class PacketModel{
        final ReceivePacket packet;
        final WritableByteChannel channel;
        volatile long unreceivedLength;

        public PacketModel(ReceivePacket<?,?> packet) {
            this.packet = packet;
            this.channel = Channels.newChannel(packet.open());
            this.unreceivedLength = packet.getLength();
        }
    }
}
