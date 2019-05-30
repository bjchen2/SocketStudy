package com.study.core;

import lombok.Setter;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

/**
 * 发送数据类，对Buffer进行封装，防止内存无节制的申请
 *
 * @author cxd27419
 */
public class IoArgs {

    /**
     * 单次写操作buffer的容纳区间(在startWriting中设置，channel读中生效)
     */
    private volatile int limit;
    private final ByteBuffer buffer;
    /**
     * 是否需要填满/消费(写/读)所有剩余区间
     */
    private final boolean isNeedConsumeRemaining;

    public IoArgs() {
        this(256);
    }

    public IoArgs(int size) {
        this(size,true);
    }

    public IoArgs(int size, boolean isNeedConsumeRemaining) {
        this.limit = size;
        this.isNeedConsumeRemaining = isNeedConsumeRemaining;
        this.buffer = ByteBuffer.allocate(size);
    }

    /**
     * 从bytes数组中读取count个数据到buffer中，bytes偏移量为offset
     *
     * @param bytes 读取源
     * @param offset 偏移量，开始读取的位置
     * @param count 需要读取的长度
     * @return 返回读取到的数据大小
     */
    public int readFrom(byte[] bytes, int offset, int count) {
        int size = Math.min(count,buffer.remaining());
        if (size <= 0){
            return 0;
        }
        buffer.put(bytes, offset, count);
        return size;
    }

    /**
     * 从ReadableByteChannel中读取数据到buffer中
     *
     * @param channel 读取源
     * @return 返回读取到的数据大小
     */
    public int readFrom(ReadableByteChannel channel) throws IOException {
        int size = 0;
        while (buffer.hasRemaining()) {
            int len = channel.read(buffer);
            if (len < 0) {
                throw new EOFException();
            }
            size += len;
        }
        return size;
    }

    /**
     * 从SocketChannel中读取数据到buffer
     *
     * @param channel 读取通道
     * @return 读取大小
     */
    public int readFrom(SocketChannel channel) throws IOException {
        //读入数据总数
        int bytesRead = 0;
        //每次读入数据字节数
        int len;
        //当len为0时，说明当前网卡资源已让给其他线程，不再继续读入，等待下次读事件就绪再读，否则空循环浪费资源
        do {
            len = channel.read(buffer);
            if (len < 0) {
                //读取失败
                throw new EOFException(this.getClass() + "[readFrom]不能继续读取数据，Channel:" + channel);
            }
            bytesRead += len;
        }while (buffer.hasRemaining() && len != 0);
        return bytesRead;
    }

    /**
     * 从buffer中写入数据到WritableByteChannel中
     *
     * @param channel 写入目标
     * @return 返回写入的数据大小
     */
    public int writeTo(WritableByteChannel channel) throws IOException {
        int bytesWrite = 0;
        while (buffer.hasRemaining()) {
            int len = channel.write(buffer);
            if (len < 0) {
                //写失败
                throw new EOFException();
            }
            bytesWrite += len;
        }
        return bytesWrite;
    }

    public int writeTo(SocketChannel channel) throws IOException {
        //写入数据总数
        int bytesWrite = 0;
        //每次写入数据字节数
        int len;
        //当len为0时，说明当前网卡资源已让给其他线程，不再继续写入，等待下次写事件就绪再写，否则空循环浪费资源
        do {
            len = channel.write(buffer);
            if (len < 0) {
                //写失败
                throw new EOFException("通道写入异常，channel ：" + channel);
            }
            bytesWrite += len;
        }while (buffer.hasRemaining() && len != 0);
        return bytesWrite;
    }

    /**
     * 从buffer中写入数据到bytes数组中，bytes偏移量为offset
     *
     * @param bytes 读取源
     * @param offset 偏移量，开始写入的位置
     * @return 返回写入的数据大小
     */
    public int writeTo(byte[] bytes, int offset) {
        int size = Math.min(bytes.length - offset,buffer.remaining());
        buffer.get(bytes,offset,size);
        return size;
    }

    /**
     * 开始写入数据到buffer
     * 写入前，先清空缓冲取
     */
    public void startWriting() {
        buffer.clear();
        //定义容纳区间
        buffer.limit(limit);
    }

    /**
     * 写完数据，将buffer反转，方便后续读取
     */
    public void finishWriting() {
        buffer.flip();
    }

    public int getInt() {
        return buffer.getInt();
    }

    public int capacity() {
        return buffer.capacity();
    }

    public void limit(int limit){
        this.limit = Math.min(limit,buffer.capacity());
    }

    /**
     * 重置limit = capacity
     */
    public void resetLimit(){
        limit = buffer.capacity();
    }

    /**
     * 当前缓冲区是否未满
     * @return 当前缓冲区是否未满
     */
    public boolean remained() {
        return buffer.hasRemaining();
    }

    public boolean isNeedConsumeRemaining() {
        return isNeedConsumeRemaining;
    }

    /**
     * 填充空数据,实际就是把position位置增加
     * @param size 需要填充的空数据大小
     * @return 实际填充的空数据大小
     */
    public int fillEmpty(int size) {
        int fillSize = Math.min(size,buffer.remaining());
        buffer.position(buffer.position() + fillSize);
        return fillSize;
    }

    /**
     * 清空部分数据
     *
     * @param size 想要清空的数据长度
     * @return 真实清空的数据长度
     */
    public int setEmpty(int size) {
        int emptySize = Math.min(size, buffer.remaining());
        buffer.position(buffer.position() + emptySize);
        return emptySize;
    }

    /**
     * IoArgs 提供者、处理者；数据的生产或消费者
     */
    public interface IoArgsEventProcessor {

        /**
         * 提供IoArgs
         *
         * @return 可供消费的IoArgs
         */
        IoArgs provideIoArgs();

        /**
         * 当消费失败时，Consume：消费
         *
         * @param e    失败原因
         * @return 是否需要关闭连接
         */
        boolean onConsumeFailed(Throwable e);

        /**
         * 当消费成功时
         *
         * @param args 操作的IoArgs
         * @return 是否继续注册发送数据
         */
        boolean onConsumeCompleted(IoArgs args);
    }
}
