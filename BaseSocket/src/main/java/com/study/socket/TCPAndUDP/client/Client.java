package com.study.socket.TCPAndUDP.client;

import com.study.socket.TCPAndUDP.client.bean.ServerInfo;
import com.study.socket.TCPAndUDP.constants.UDPConstants;

/**
 * Created By Cx On 2019/3/17 10:20
 */
public class Client {
    public static void main(String[] args) {
        //搜索服务器，10s后超时
        ServerInfo info;
        try {
            info = UDPSearcher.searchServer(UDPConstants.PORT_CLIENT_RESPONSE,10000);
            System.out.println("serverInfo : " + info);
            TCPClient.linkedWith(info);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
