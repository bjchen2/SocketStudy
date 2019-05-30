package com.study.impl.exceptions;

import java.io.IOException;

/**
 * 空IoArgs异常
 * @author cxd27419
 * @date 2019/5/30
 */
public class EmptyIoArgsException extends IOException {
    public EmptyIoArgsException(String message) {
        super(message);
    }
}
