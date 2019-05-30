package com.study.socket.UDPDemo;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用于监听并回复指定端口的UDP消息
 * Created By Cx On 2019/3/16 17:13
 */
public class UDPListener implements Runnable {
    //回复信息
    private final String sn;
    //是否已完成工作
    private boolean done = false;
    //用于传输工作
    private DatagramSocket ds = null;
    //监听端口
    private int port;
    //sender线程池
    private ExecutorService executor = Executors.newCachedThreadPool();

    public UDPListener(String sn,int port) {
        this.sn = sn;
        this.port = port;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void run() {
        System.out.println("开始通信");
        try {
            //指定监听端口
            ds = new DatagramSocket(port);
            while (!done) {
                byte[] buf = new byte[1024];
                //该数据包是用来接收数据的，所以无需指定发送端口和地址
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                //等待数据
                ds.receive(packet);
                //异步处理数据防止阻塞
                executor.submit(new Sender(packet));
            }
        } catch (Exception e) {
            //socket closed异常是正常的，因为调用close方法时，socket在receive方法处阻塞
            e.printStackTrace();
        } finally {
            close();
        }
        System.out.println("通信结束");
    }

    //结束
    public void close() {
        done = true;
        executor.shutdown();
        //必须调用close方法，不然可能会阻塞在receive方法处
        if (ds != null) {
            ds.close();
            ds = null;
        }
    }

    class Sender implements Runnable{
        private DatagramPacket packet;

        Sender(DatagramPacket packet){
            this.packet = packet;
        }

        @Override
        public void run() {
            String data = new String(packet.getData(), 0, packet.getLength());
            System.out.println("接收到消息，发送者为：" + packet.getSocketAddress());
            System.out.println("接收数据为：" + data);

            //回复
            byte[] buf = MessageCreator.buildSN(sn).getBytes();
            //解析需要回复的端口
            int port = MessageCreator.parsePort(data);
            if (port != -1) {
                //如果端口解析正常，进行回复
                packet = new DatagramPacket(buf, buf.length, packet.getAddress(), port);
                try {
                    ds.send(packet);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
