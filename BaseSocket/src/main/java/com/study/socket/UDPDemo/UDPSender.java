package com.study.socket.UDPDemo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UDP发送者，发送给广播地址，20000端口告诉接收者回复端口为多少，并监听该端口获取对方回复
 * Created By Cx On 2019/2/19 15:20
 */
public class UDPSender {
    private static final int LISTEN_PORT = 30000;

    //主线程执行广播操作，创建多个新线程执行监听操作
    public static void main(String[] args) throws IOException, InterruptedException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        //锁存器，防止在监听线程未创建好之前进行发送
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Listener listener = new Listener(countDownLatch, LISTEN_PORT);
        //创建监听线程
        executorService.submit(listener);
        //等待监听线程启动完成
        countDownLatch.await();
        //广播发送消息
        sendBroadcast();
        //读取任意字符结束
        System.in.read();
        List<Device> devices = listener.getDevicesAndClose();
        for (Device device : devices) {
            System.out.println(device.ip + ":" + device.port + ",msg:" + device.msg);
        }
        executorService.shutdown();
    }

    //发送广播
    private static void sendBroadcast() throws IOException {
        //不指定监听端口，由系统分配
        DatagramSocket socket = new DatagramSocket();
        byte[] buf = MessageCreator.buildPort(LISTEN_PORT).getBytes();
        //将数据包广播发送到20000端口
        DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName("255.255.255.255"), 20000);

        socket.send(packet);
        socket.close();
    }

    //设备类，用于存储监听到回复的设备信息
    private static class Device {
        final String ip;
        final int port;
        final String msg;

        private Device(String ip, int port, String msg) {
            this.ip = ip;
            this.port = port;
            this.msg = msg;
        }
    }

    private static class Listener implements Runnable {
        //CountDownLatch用于防止主线程在监听线程未创建成功之前进行其他操作
        private final CountDownLatch cdl;
        //存储监听到的所有设备信息
        private final List<Device> devices;
        //是否完成
        private boolean done = false;
        //监听端口
        private final int listenPort;
        private DatagramSocket socket = null;

        private Listener(CountDownLatch cdl, int listenPort) {
            this.cdl = cdl;
            this.listenPort = listenPort;
            devices = new ArrayList<>();
        }

        @Override
        public void run() {
            //监听线程启动成功
            cdl.countDown();
            try {
                socket = new DatagramSocket(listenPort);
                while (!done) {
                    //等待回应
                    byte[] buf = new byte[1024];
                    //该数据包是用来接收数据的，所以无需指定发送端口和地址
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    //不能直接写成new String(packet.getData())，这样可能会造成因为buf的长度大于数据长度，导致转换后，末尾会有很多空格
                    String data = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("接收到消息，发送者为：" + packet.getSocketAddress());
                    System.out.println("接收数据为：" + data);
                    //将接收到的设备信息放入列表
                    devices.add(new Device(packet.getAddress().getHostAddress(), packet.getPort(), MessageCreator.parseSN(data)));
                }
            } catch (IOException e) {
                //todo socket closed异常是正常的吗？调用close方法时，socket在receive方法处阻塞
                e.printStackTrace();
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

        private List<Device> getDevicesAndClose() {
            close();
            return devices;
        }
    }
}
