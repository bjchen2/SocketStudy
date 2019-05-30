package com.study.socket.TCPAndUDP.constants;

public interface UDPConstants {
    // 公用头部
    byte[] HEADER = new byte[]{7, 7, 7, 7, 7, 7, 7, 7};
    // 服务器固化UDP接收端口
    int PORT_SERVER = 30201;
    // 客户端回送端口
    int PORT_CLIENT_RESPONSE = 30202;
    //消息最小长度 头部长度+short（2字节，表命令）+int（4字节，表端口）
    int MSG_MIN_LENGTH = HEADER.length + 2 + 4;
}
