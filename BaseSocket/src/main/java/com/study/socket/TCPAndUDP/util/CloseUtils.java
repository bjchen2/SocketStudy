package com.study.socket.TCPAndUDP.util;

import java.io.Closeable;
import java.io.IOException;

/**
 * Created By Cx On 2019/3/19 8:50
 */
public class CloseUtils {
    public static void close(Closeable... closeables){
        if (closeables.length < 1) return;
        for (Closeable c: closeables){
            try {
                c.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
