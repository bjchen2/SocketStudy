package com.study.socket.TCPAndUDP.server.handle;

import com.study.socket.TCPAndUDP.util.CloseUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 处理客户端的TCP请求
 * Created By Cx On 2019/3/19 8:52
 */
public class ClientTCPHandler {

    private Socket socket;
    private ExecutorService threadPool;
    private Reader reader;

    public ClientTCPHandler(Socket socket){
        this.socket = socket;
        this.threadPool = new ThreadPoolExecutor(3,5,1,
                TimeUnit.MINUTES, new ArrayBlockingQueue<>(10));
        reader = new Reader();
        System.out.println("有客户端连接，地址为：" + socket.getInetAddress() + ":" + socket.getPort());
    }


    public void readToPrint() {
        if (threadPool == null) {
            System.out.println("连接已关闭");
            return;
        }
        threadPool.execute(reader);
    }

    public void send(String str) {
        if (threadPool == null) {
            System.out.println("连接已关闭");
            return;
        }
        threadPool.execute(new Writer(str));
    }

    public void exit(){
        reader.exit();
        CloseUtils.close(socket);
        socket = null;
        threadPool.shutdown();
        threadPool = null;
    }

    class Reader implements Runnable{
        private boolean done = false;
        @Override
        public void run() {
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                //获取客户端输入数据
                String s;
                while (!done && (s = reader.readLine()) != null) {
                    if ("bye".equals(s)) break;
                    System.out.println(s);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                exit();
            }
        }

        public void exit(){
            done = true;
            System.out.println("TCP服务器读取通信结束");
        }
    }

    class Writer implements Runnable{
        private final String msg;
        Writer(String msg){
            this.msg = msg;
        }
        @Override
        public void run() {
            // 得到打印流，用于数据输出；服务器回送数据使用
            try{
                PrintStream writer = new PrintStream(socket.getOutputStream());
                writer.println(msg);
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }
}
