package com.study.utils.plugin;

import java.io.IOException;

/**
 * 缓冲区溢出异常
 * @author Cx
 * @version jdk8 and idea On 2019/5/15 17:50
 */
public class BufferOverflowException extends IOException {

    public BufferOverflowException() {
        super();
    }

    public BufferOverflowException(String msg) {
        super(msg);
    }
}
