package com.study.socket.TCPAndUDP.server;

import com.study.socket.TCPAndUDP.constants.UDPConstants;
import com.study.socket.TCPAndUDP.util.ByteUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用于监听指定端口UDP消息并回复可以连接的TCP端口
 * Created By Cx On 2019/3/16 17:49
 */
public class UDPListener extends Thread {
    //回复信息
    private final String responseMsg;
    //是否已完成工作
    private boolean done = false;
    //用于传输工作
    private DatagramSocket ds = null;
    //监听端口
    private int listenPort;
    //TCP连接端口
    private int TCPPort;
    //sender线程池处理数据
    private ExecutorService e = Executors.newCachedThreadPool();

    public UDPListener(String responseMsg, int listenPort, int TCPPort) {
        this.responseMsg = responseMsg;
        this.listenPort = listenPort;
        this.TCPPort = TCPPort;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void run() {
        System.out.println("UDP服务器开始通信");
        try {
            //指定监听端口
            ds = new DatagramSocket(listenPort);
            while (!done) {
                byte[] buf = new byte[1024];
                //该数据包是用来接收数据的，所以无需指定发送端口和地址
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                //等待数据
                ds.receive(packet);
                //使用线程池处理线程
                e.submit(new Sender(packet));
            }
        } catch (Exception e) {
            //socket closed异常是正常的，因为调用close方法时，socket在receive方法处阻塞
            e.printStackTrace();
        } finally {
            close();
        }
        System.out.println("UDP服务器通信结束");
    }

    //结束
    public void close() {
        done = true;
        e.shutdown();
        //必须调用close方法，不然可能会阻塞在receive方法处
        if (ds != null) {
            ds.close();
            ds = null;
        }
    }

    class Sender implements Runnable{
        private final DatagramPacket packet;

        Sender(DatagramPacket packet){
            this.packet = packet;
        }

        @Override
        public void run() {
            byte[] clientData = packet.getData();
            int clientLength = packet.getLength();
            int headerLength = UDPConstants.HEADER.length;
            InetAddress clientAddress = packet.getAddress();
            //验证接收信息是否有效(口令头+指令（short,两字节）+客户端回送端口(int,4字节))
            boolean isValid = clientLength >= UDPConstants.MSG_MIN_LENGTH && ByteUtils.startsWith(clientData,UDPConstants.HEADER);
            System.out.println("接收到消息，发送者为：" + packet.getSocketAddress() + "\tisValid: "+isValid);

            //无效则不做处理，继续接收
            if (!isValid) return;

            //解析命令与回送端口
            short cmd = (short)((clientData[headerLength++]<<8)|(clientData[headerLength++] & 0xff));
            int responsePort = ((clientData[headerLength++]<<24) | ((clientData[headerLength++] & 0xff)<<16) |
                    ((clientData[headerLength++] & 0xff)<<8) | (clientData[headerLength] & 0xff));

            //判断合法性
            if (cmd == 1 && responsePort > 0){
                //todo 回送数据
                ByteBuffer bf = ByteBuffer.allocate(128);
                bf.put(UDPConstants.HEADER);
                bf.putShort((short)2);
                bf.putInt(TCPPort);
                bf.put(responseMsg.getBytes());
                //构建回送信息
                DatagramPacket responsePacket = new DatagramPacket(bf.array(),bf.position(),clientAddress,responsePort);
                try {
                    ds.send(responsePacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
