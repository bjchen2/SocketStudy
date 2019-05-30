package com.study.socket.TCPAndUDP.server;

import com.study.socket.TCPAndUDP.constants.TCPConstants;
import com.study.socket.TCPAndUDP.constants.UDPConstants;

import java.util.Scanner;

/**
 * Created By Cx On 2019/3/15 19:08
 */
public class Server {
    public static void main(String[] args) {
        TCPServer tcpServer = new TCPServer(TCPConstants.PORT_SERVER);
        if (!tcpServer.start()) return;
        UDPProvider.start(UDPConstants.PORT_SERVER, TCPConstants.PORT_SERVER);
        Scanner input = new Scanner(System.in);
        String s;
        while(true){
            s = input.nextLine();
            if ("bye".equals(s)) break;
            tcpServer.broadcast(s);
        }
        UDPProvider.stop();
        tcpServer.stop();
    }
}
