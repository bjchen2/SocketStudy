package com.study.box;

import com.study.core.SendPacket;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * 发送文件的数据包
 *
 * @author Cx
 * @version jdk8 and idea On 2019/4/25 10:51
 */
public class FileSendPacket extends SendPacket<FileInputStream> {
    private final File file;

    public FileSendPacket(File file) {
        this.file = file;
        this.length = file.length();
    }

    @Override
    public byte type() {
        return TYPE_STREAM_FILE;
    }

    /**
     * 使用File构建文件读取流，用以读取本地的文件数据进行发送
     *
     * @return 文件读取流
     */
    @Override
    protected FileInputStream createStream() {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
    }
}
