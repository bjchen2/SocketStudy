package com.study.socket.TCPAndUDP.client;

import com.study.socket.TCPAndUDP.client.bean.ServerInfo;
import com.study.socket.TCPAndUDP.constants.UDPConstants;
import com.study.socket.TCPAndUDP.util.ByteUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created By Cx On 2019/3/17 10:19
 */
public class UDPSearcher {

    //向监听listenPort搜索服务器，timeout毫秒后超时
    static ServerInfo searchServer(int listenPort, int timeout) throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        //锁存器，防止在监听线程未创建好之前进行发送和超时功能
        CountDownLatch listenLatch = new CountDownLatch(1), receiveLatch = new CountDownLatch(1);
        Listener listener = new Listener(listenPort, listenLatch, receiveLatch);
        //创建监听线程
        executorService.submit(listener);
        //等待监听线程启动完成
        listenLatch.await();
        //广播发送消息
        sendBroadcast();
        //等待timeout毫秒
        receiveLatch.await(timeout, TimeUnit.MILLISECONDS);
        executorService.shutdown();
        return listener.getServerInfo();
    }

    //发送广播
    private static void sendBroadcast() throws IOException {
        //不指定监听端口，由系统分配
        DatagramSocket socket = new DatagramSocket();
        //将数据包广播发送到20000端口
        ByteBuffer byteBuffer = ByteBuffer.allocate(128);
        byteBuffer.put(UDPConstants.HEADER);
        byteBuffer.putShort((short)1);
        byteBuffer.putInt(UDPConstants.PORT_CLIENT_RESPONSE);
        DatagramPacket packet = new DatagramPacket(byteBuffer.array(), byteBuffer.position(), InetAddress.getByName("255.255.255.255"),
                UDPConstants.PORT_SERVER);

        socket.send(packet);
        socket.close();
    }

    private static class Listener implements Runnable {
        //CountDownLatch用于防止主线程在监听线程未创建成功之前进行其他操作
        private final CountDownLatch listenLatch;
        //用于接收信息计时
        private final CountDownLatch receiveLatch;
        //存储监听到的所有设备信息
        private ServerInfo serverInfo = null;
        //是否完成
        private boolean done = false;
        //监听端口
        private final int listenPort;
        private DatagramSocket socket = null;

        private Listener(int listenPort,CountDownLatch listenLatch, CountDownLatch receiveLatch) {
            this.listenLatch = listenLatch;
            this.receiveLatch = receiveLatch;
            this.listenPort = listenPort;
        }

        @Override
        public void run() {
            //监听线程启动成功
            listenLatch.countDown();
            try {
                System.out.println("ClientUDP监听线程启动");
                socket = new DatagramSocket(listenPort);
                while (!done) {
                    //等待回应
                    byte[] buf = new byte[128];
                    //该数据包是用来接收数据的，所以无需指定发送端口和地址
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    byte[] serverData = packet.getData();
                    int serverLength = packet.getLength();
                    int headerLength = UDPConstants.HEADER.length;
                    InetAddress serverAddress = packet.getAddress();
                    //验证接收信息是否有效(口令头+指令（short,两字节）+客户端回送端口(int,4字节))
                    boolean isValid = serverLength >= UDPConstants.MSG_MIN_LENGTH && ByteUtils.startsWith(serverData,UDPConstants.HEADER);
                    System.out.println("接收到消息，发送者为：" + packet.getSocketAddress() + "\tisValid: "+isValid);

                    //无效则不做处理，继续接收
                    if (!isValid) continue;

                    //解析命令与回送端口
                    ByteBuffer byteBuffer = ByteBuffer.wrap(serverData,headerLength,serverLength);
                    short cmd = byteBuffer.getShort();
                    int responsePort = byteBuffer.getInt();

                    //判断合法性
                    if (cmd == 2 && responsePort > 0){
                        //组装服务器信息
                        serverInfo = new ServerInfo(responsePort,serverAddress.getHostAddress());
                    }
                    //将接收到的设备信息放入列表
                    receiveLatch.countDown();
                }
            } catch (IOException e) {
                //todo socket closed异常是正常的吗？调用close方法时，socket在receive方法处阻塞
            } finally {
                close();
            }
        }

        private void close() {
            done = true;
            if (socket != null) {
                socket.close();
                socket = null;
            }
        }

        private ServerInfo getServerInfo() {
            close();
            return serverInfo;
        }
    }
}
