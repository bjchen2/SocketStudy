package com.study.impl.async;

import com.study.core.Frame;
import com.study.core.IoArgs;
import com.study.core.SendPacket;
import com.study.core.ds.BytePriorityNode;
import com.study.frames.*;

import java.io.Closeable;
import java.io.IOException;

/**
 * 异步读取服务端Packet写入Frame
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/3 16:43
 */
public class AsyncPacketReader implements Closeable {
    private final PacketProvider provider;
    private volatile IoArgs args = new IoArgs();

    private volatile BytePriorityNode<Frame> node;
    private volatile int nodeSize = 0;

    /**
     * 最新的唯一标识
     */
    private short lastIdentifier;

    AsyncPacketReader(PacketProvider provider) {
        this.provider = provider;
    }

    /**
     * 请求获取下一个packet
     * @return 当前队列中是否还有数据可以发送
     */
    boolean requestTakePacket() {
        synchronized (this){
            if (nodeSize > 0){
                //如果有packet，则直接消费自己的packet，不去拿packet
                return true;
            }
        }
        SendPacket packet = provider.takePacket();
        if (packet != null){
            short identifier = generateIdentifier();
            SendHeaderFrame frame = new SendHeaderFrame(identifier,packet);
            appendNewFrame(frame);
        }
        synchronized (this){
            return nodeSize != 0;
        }
    }

    /**
     * 请求发送心跳帧
     * @return 是否添加心跳帧到发送队列（若队列已存在心跳帧则不继续添加，返回false）
     */
    boolean requestSendHeartbeatFrame(){
        synchronized (this){
            for (BytePriorityNode<Frame> x = node; x != null; x = x.next){
                Frame frame = x.item;
                if (frame instanceof HeartbeatSendFrame){
                    return false;
                }
            }
            appendNewFrame(new HeartbeatSendFrame());
            return true;
        }
    }

    /**
     * 填充数据到IoArgs中
     *
     * @return 如果当前有可用于发送的帧，则填充数据并返回，如果填充失败返回null
     */
    IoArgs fillData() {
        Frame currentFrame = getCurrentFrame();
        if (currentFrame == null){
            return null;
        }
        try {
            if (currentFrame.handle(args)){
                //消费完本帧，尝试构建后续帧
                Frame nextFrame = currentFrame.nextFrame();
                if (nextFrame != null){
                    appendNewFrame(nextFrame);
                } else if (currentFrame instanceof SendEntityFrame){
                    //如果没有下一帧，且当前帧为实体帧，说明发送完成
                    provider.completedPacket(((SendEntityFrame) currentFrame).getPacket(),true);
                }
                //将消费完的当前帧从链表中弹出
                popCurrentFrame();
            }
            return args;
        }catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 取消Packet对应的帧发送，如果当前Packet已发送部分数据（就算只是头数据）
     * 也应该在当前帧队列中发送一份取消发送的标志{@link CancelSendFrame}
     * @param packet 需要取消发送的packet
     */
    synchronized void cancel(SendPacket packet) {
        if (nodeSize == 0){
            return;
        }
        //遍历node队列，寻找是否存在需要取消发送的packet
        for (BytePriorityNode<Frame> now = node,before = null; now != null; before = now,now = now.next){
            Frame frame = now.item;
            if (frame instanceof AbsSendPacketFrame){
                AbsSendPacketFrame packetFrame = (AbsSendPacketFrame) frame;
                if (packet == packetFrame.getPacket()){
                    boolean removable = packetFrame.abort();
                    if (removable){
                        //如果当前数据未被发送
                        removeFrame(now,before);
                        if (packetFrame instanceof SendHeaderFrame){
                            //如果是头帧，且未发送任何数据，直接取消，不需要添加取消发送帧告知对方
                            break;
                        }
                    }
                    //如果当前数据已经在发送，构建一个取消发送帧
                    CancelSendFrame cancelSendFrame = new CancelSendFrame(packetFrame.getBodyIdentifier());
                    appendNewFrame(cancelSendFrame);

                    //意外终止，返回失败
                    provider.completedPacket(packet,false);
                    break;
                }
            }
        }
    }

    @Override
    public synchronized void close(){
        while (node != null){
            Frame frame = node.item;
            if (frame instanceof AbsSendPacketFrame){
                SendPacket packet = ((AbsSendPacketFrame) frame).getPacket();
                provider.completedPacket(packet,false);
            }
            node = node.next;
        }

        nodeSize = 0;
        node = null;
    }

    /**
     * 添加一个新的帧
     * @param frame 需要添加的帧
     */
    private synchronized void appendNewFrame(Frame frame) {
        BytePriorityNode<Frame> newNode = new BytePriorityNode<>(frame);
        if (node != null){
            node.appendWithPriority(newNode);
        }else {
            node = newNode;
        }
        nodeSize++;
    }

    /**
     * 获取当前帧
     * @return 当前帧
     */
    private synchronized Frame getCurrentFrame() {
        if (node == null){
            return null;
        }
        return node.item;
    }

    /**
     * 将当前帧移出链表
     */
    private synchronized void popCurrentFrame() {
        node = node.next;
        nodeSize--;
        if (node == null){
            requestTakePacket();
        }
    }

    /**
     * 移除now节点
     * @param now 需要移除的节点
     * @param before 需要移除的前一个节点，当now为头节点时，该值为null
     */
    private synchronized void removeFrame(BytePriorityNode<Frame> now, BytePriorityNode<Frame> before) {
        if (before == null){
            node = now.next;
        }else {
            before.next = now.next;
        }
        nodeSize--;
        if (node == null){
            requestTakePacket();
        }
    }

    private short generateIdentifier(){
        short identifier = ++lastIdentifier;
        short maxIdentifier = 255;
        if (identifier == maxIdentifier){
            lastIdentifier = 0;
        }
        return identifier;
    }

    interface PacketProvider{
        /**
         * 获取下一个可发送数据包
         * @return 下一个packet
         */
        SendPacket takePacket();

        /**
         * 完成packet发送
         * @param packet 完成发送的packet
         * @param isSucceed 是否为成功发送
         */
        void completedPacket(SendPacket packet, boolean isSucceed);
    }
}
