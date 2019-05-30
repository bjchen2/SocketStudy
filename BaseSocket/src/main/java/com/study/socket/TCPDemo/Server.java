package com.study.socket.TCPDemo;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 服务端
 * Created By Cx On 2019/2/18 21:42
 */
public class Server {
    private static final int LISTEN_PORT = 20000;
    public static void main(String[] args) throws IOException {
        //建立一个线程池,该连接池默认线程数为3（超出线程1分钟未使用即失效），最大线程数为5，阻塞队列可存放10个线程
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(3,5,1,
                TimeUnit.MINUTES, new ArrayBlockingQueue<>(10));
        System.out.println("服务器启动");
        ServerSocket serverSocket = createServerSocket();
        initServerSocket(serverSocket);
        //设置监听port端口，等待连接数最多为50个。必须先初始化，再绑定，因为绑定以后就开始监听，再设置就无效了
        serverSocket.bind(new InetSocketAddress(LISTEN_PORT),50);
        //监听2000端口
        try {
            while (true) {
                //当客户端连接时，返回客户端套接字，否则一直阻塞
                Socket socket = serverSocket.accept();
                //申请新线程处理客户端请求
                threadPool.execute(() -> {
                    System.out.println("有客户端连接，地址为："+socket.getInetAddress()+":"+socket.getPort());
                    try {
                        //获取客户端输入数据
                        //Client的send2Server ： 不能用!=null读到文件末尾，因为只要客户端不关闭输入流，就不可能为null
                        //下述方法不可用于接收send2Server2，因为readLine()方法会因为读不到行尾符而阻塞
//                        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//                        String s;
//                        while(!(s = reader.readLine()).equals("bye")){
//                            System.out.println(s);
//                        }
                        //Client的send2Server2 ：使用readLine()方法会因为读不到行尾符而阻塞
                        //下述方法可用于send2Server
                        InputStream reader = socket.getInputStream();
                        byte[] buffer = new byte[1024];
                        int length;
                        while((length = reader.read(buffer)) != -1){
                            String s = new String(buffer,0,length);
                            if ("bye".equals(s)) break;
                            System.out.println(s);
                        }
                        //回复客户端
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                        writer.write("byebye");
                        System.out.println("回复结束");
                        //将输出流全部输出，因为接下来将要关闭输出流
                        writer.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }finally {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }finally {
            serverSocket.close();
            threadPool.shutdown();
        }
    }

    private static ServerSocket createServerSocket() throws IOException {
        // 创建基础的ServerSocket
        ServerSocket serverSocket = new ServerSocket();
        // 绑定到本地端口20000上，并且设置当前可允许等待链接的队列为50个，若超出则客户端触发异常
        //serverSocket = new ServerSocket(PORT);
        // 等效于上面的方案，队列设置为50个
        //serverSocket = new ServerSocket(PORT, 50);
        // 与上面等同
        // serverSocket = new ServerSocket(PORT, 50, Inet4Address.getLocalHost());
        return serverSocket;
    }

    private static void initServerSocket(ServerSocket serverSocket) throws IOException {
        // 是否复用未完全关闭的地址端口
        serverSocket.setReuseAddress(true);
        // 设置发送缓冲器最大值
        serverSocket.setReceiveBufferSize(64 * 1024 * 1024);
        // 设置serverSocket#accept超时时间
        // serverSocket.setSoTimeout(2000);
        // 设置性能参数：短链接，延迟，带宽的权重
        serverSocket.setPerformancePreferences(1, 1, 1);
    }
}
