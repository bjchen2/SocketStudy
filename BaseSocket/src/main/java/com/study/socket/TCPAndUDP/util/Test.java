package com.study.socket.TCPAndUDP.util;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Created By Cx On 2019/3/18 18:29
 */
public class Test {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(30401);
        System.out.println("yes");
        serverSocket.close();
    }
}
