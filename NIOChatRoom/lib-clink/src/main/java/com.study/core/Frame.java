package com.study.core;

import java.io.IOException;

/**
 * 表示数据传输中被分片后的每一帧
 * 帧规则：
 *  头部6字节：当前帧大小(2)，帧类型(1)，帧标志信息(1)，唯一标识(1)，预留空间(1)
 *  数据：
 * @author Cx
 * @version jdk8 and idea On 2019/5/3 16:11
 */
public abstract class Frame {
    /**
     * 帧头部长度
     */
    public static final int FRAME_HEADER_LENGTH = 6;
    /**
     * 数据最大容量 2^16 - 1 = 65535
     */
    public static final int MAX_CAPACITY = (1<<16) - 1;

    /**
     * packet消息头
     */
    public static final byte TYPE_PACKET_HEADER = 11;
    /**
     * packet消息体
     */
    public static final byte TYPE_PACKET_ENTITY = 12;

    /**
     * 取消发送命令
     */
    public static final byte TYPE_COMMAND_SEND_CANCEL = 41;
    /**
     * 拒绝接收命令
     */
    public static final byte TYPE_COMMAND_RECEIVE_REJECT = 42;
    /**
     * 心跳包命令
     */
    public static final byte TYPE_COMMAND_HEARTBEAT = 81;

    public static final byte FLAG_NONE = 0;

    protected final byte[] header = new byte[FRAME_HEADER_LENGTH];

    /**
     * @param length 未消费数据长度(即当前帧大小)
     * @param type 数据类型
     * @param flag 标志信息
     * @param identifier 唯一标识
     */
    public Frame(int length,byte type,byte flag,short identifier){
        if (length < 0 || length > MAX_CAPACITY){
            throw new RuntimeException("");
        }
        else if (identifier < 1 || identifier > 255){
            throw new RuntimeException("");
        }
        //存储length，获取16-9位
        header[0] = (byte) (length >> 8);
        //获取8-0位
        header[1] = (byte) length;

        header[2] = type;
        header[3] = flag;

        header[4] = (byte)identifier;
        header[5] = 0;
    }

    public Frame(byte[] header){
        System.arraycopy(header,0,this.header,0,FRAME_HEADER_LENGTH);
    }

    /**
     * 获取该帧长度
     * @return 当前帧Body总长度[0~MAX_CAPACITY]
     */
    public int getBodyLength(){
        //byte转换成高字节类型，缺失位会全补为符号位(所以不能使用这种写法： (header[0]<<8) | header[1])
        //因为当length为65535（0x1111）时相当于 （-1<<8) | -1;
        //而-1<<8 = 0x1111,所以最终结果为-1而非65535
        //todo 视频写法： ((((int) header[0]) & 0xFF) << 8) | (((int) header[1]) & 0xFF)
        return ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
    }

    /**
     * 获取Body的类型
     *
     * @return 类型[0~255]
     */
    public byte getBodyType() {
        return header[2];
    }

    /**
     * 获取Body的Flag
     *
     * @return Flag
     */
    public byte getBodyFlag() {
        return header[3];
    }

    /**
     * 获取Body的唯一标志
     *
     * @return 标志[0~255]
     */
    public short getBodyIdentifier() {
        //todo 位运算后为int类型，再强转为short就不会出错，视频写法： (short) ( ((short)header[4]) &0xff);
        return (short) (header[4]&0xff);
    }

    /**
     * 进行数据读或写操作
     *
     * @param args 用于操作的数据包
     * @return 是否已消费完， True：则无需再传递数据到Frame或从当前Frame读取数据
     * @throws IOException IO异常
     */
    public abstract boolean handle(IoArgs args) throws IOException;

    /**
     * 基于当前帧尝试构建下一份待消费的帧
     * 该方法保证只有在被调用时才构建下一帧，防止过早构建剩余帧占用内存
     * 因为一帧才64kb，所以若是一个大文件则可能需要成千上万帧，假如提前构造不仅占用内存，取消传输时也比较麻烦
     *
     * @return 下一帧，如果为NULL表示没有待消费的帧
     */
    public abstract Frame nextFrame();

    /**
     * 获取当前帧可消费的数据长度
     * @return 当前帧可消费的数据长度
     */
    public abstract int getConsumableLength();
}
