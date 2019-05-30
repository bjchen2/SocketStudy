package com.study.socket.TCPAndUDP.server;

import java.util.UUID;

/**
 * 监听UDP指定端口，若有客户端发送广播，则告诉客户端服务器具体IP
 * Created By Cx On 2019/3/15 19:15
 */
public class UDPProvider {

    private static volatile UDPListener listener = null;

    private static void createSingletonListener(int listenPort, int tcpPort){
        if (listener == null){
            synchronized (UDPProvider.class){
                if (listener == null){
                    listener = new UDPListener(UUID.randomUUID().toString(), listenPort, tcpPort);
                }
            }
        }
    }

    //开始监听某个端口,并告知客户端可以访问的tcpPort
    static void start(int listenPort, int tcpPort){
        stop();
        createSingletonListener(listenPort, tcpPort);
        listener.start();
    }

    static void stop(){
        if (listener != null){
            listener.close();
            listener = null;
        }
    }
}
