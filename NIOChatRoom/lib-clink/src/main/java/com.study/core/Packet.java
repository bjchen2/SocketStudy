package com.study.core;

import lombok.Getter;

import java.io.Closeable;
import java.io.IOException;

/**
 * 提供给业务层发送的数据包(是接收包与发送包的父类)
 * 公共的数据封装，提供类型及基本长度的定义
 * Use jdk8 and idea On 2019/4/25 10:35
 *
 * @author Cx
 */
public abstract class Packet<Stream extends Closeable> implements Closeable {
    /**
     * 最大包长度，5个字节满载的long类型
     */
    public static final long MAX_PACKET_SIZE = (((0xFFL) << 32) |
            ((0xFFL) << 24) | ((0xFFL) << 16) | ((0xFFL) << 8) | ((0xFFL)));
    /**
     * BYTES 类型，直接存储在内存中
     */
    public static final byte TYPE_MEMORY_BYTES = 1;
    /**
     * String 类型，直接存储在内存中
     */
    public static final byte TYPE_MEMORY_STRING = 2;
    /**
     * 文件 类型
     */
    public static final byte TYPE_STREAM_FILE = 3;
    /**
     * 长链接流 类型
     */
    public static final byte TYPE_STREAM_DIRECT = 4;
    /**
     * 发送数据长度
     */
    @Getter
    protected long length;
    /**
     * 发送/接收流
     */
    private Stream stream;

    /**
     * 类型，直接通过方法得到:
     * <p>
     * {@link #TYPE_MEMORY_BYTES}
     * {@link #TYPE_MEMORY_STRING}
     * {@link #TYPE_STREAM_FILE}
     * {@link #TYPE_STREAM_DIRECT}
     *
     * @return 类型
     */
    public abstract byte type();

    /**
     * 开启所需要的流
     *
     * @return 发送流/接收流
     */
    public final Stream open() {
        if (stream == null) {
            stream = createStream();
        }
        return stream;
    }

    /**
     * 创建发送/接收流，由子类去实现具体创建什么流
     *
     * @return 发送/接收流
     */
    protected abstract Stream createStream();

    @Override
    public final void close() throws IOException {
        if (stream != null) {
            closeStream(stream);
            stream = null;
        }
    }

    /**
     * 允许子类复写该方法，使其能在关闭时做一些事
     */
    protected void closeStream(Stream stream) throws IOException {
        stream.close();
    }

    /**
     * 头部额外信息，用于携带额外的校验信息等
     * 发送前先把当前数据的md5校验信息写在头部，接收后将其进行对比
     *
     * @return byte 数组，最大255长度
     */
    public byte[] headerInfo() {
        return null;
    }
}
