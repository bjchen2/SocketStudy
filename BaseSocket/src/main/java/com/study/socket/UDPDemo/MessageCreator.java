package com.study.socket.UDPDemo;

/**
 * 消息创建者
 * Created By Cx On 2019/2/21 16:43
 */
public class MessageCreator {
    //回应数据前缀
    private static final String SN_HEADER = "收到数据，数据为(SN):";
    //回复端口前缀
    private static final String PORT_HEADER = "收到暗号，请回送到端口:";

    public static String buildSN(String sn) {
        return SN_HEADER.concat(sn);
    }

    public static String parseSN(String data) {
        if (data.startsWith(SN_HEADER)) {
            return data.substring(SN_HEADER.length());
        }
        return null;
    }

    public static String buildPort(int port) {
        return PORT_HEADER + port;
    }

    public static int parsePort(String data) {
        if (data.startsWith(PORT_HEADER)) {
            return Integer.parseInt(data.substring(PORT_HEADER.length()));
        }
        return -1;
    }
}
