package com.study.socket.UDPDemo;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UDP信息接收者，需要先监听某个端口（用于和其他端口交流），接收到信息后解析数据获取需要回复的端口，并给出回复
 * UDP不存在客户端和服务端，这里的接收者和发送者是相对的，因为双方都可读可写
 * Created By Cx On 2019/2/19 15:20
 */
public class UDPReceiver {
    public static void main(String[] args) throws IOException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        //监听20000端口，若有消息，回复我收到信息了……
        UDPListener listener = new UDPListener("我收到信息了，谢谢",20000);
        executor.submit(listener);
        //输入任意字符，停止监听
        System.in.read();
        //必须要先close，保证线程没有工作以后才能关闭线程
        listener.close();
        executor.shutdown();
        System.out.println("close");
    }
}
