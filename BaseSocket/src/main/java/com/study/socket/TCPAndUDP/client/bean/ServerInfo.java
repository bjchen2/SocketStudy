package com.study.socket.TCPAndUDP.client.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 保存UDP广播搜索获取到的服务器信息
 * Created By Cx On 2019/3/17 10:20
 */
@AllArgsConstructor
@Data
public class ServerInfo {
    //服务器tcp连接端口
    private int tcpPort;
    //ip地址
    private String address;
}
