package com.study.socket.TCPAndUDP.server;

import com.study.socket.TCPAndUDP.server.handle.ClientTCPHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 提供TCP连接给服务器
 * Created By Cx On 2019/3/15 19:10
 */
public class TCPServer {

    private List<ClientTCPHandler> handlers;
    private TCPListener listener;
    private final int port;

    TCPServer(int port){
        this.port = port;
        handlers = new ArrayList<>();
    }

    //提供建立TCP链接，使用listener建立新线程异步处理
    public boolean start(){
        try {
            if (listener == null) {
                listener = new TCPListener(port);
                listener.start();
            }
            System.out.println("tcp服务器启动,绑定端口："+port);
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void stop(){
        listener.close();
        listener.interrupt();
        listener = null;
        for (ClientTCPHandler handler : handlers){
            handler.exit();
        }
        handlers.clear();
    }

    //广播消息给所有连接tcp的用户
    public void broadcast(String str) {
        for (ClientTCPHandler clientHandler : handlers) {
            clientHandler.send(str);
        }
    }

    private class TCPListener extends Thread {

        private ExecutorService threadPool = new ThreadPoolExecutor(3,5,1,
                TimeUnit.MINUTES, new ArrayBlockingQueue<>(10));
        private boolean done;
        private ServerSocket serverSocket;

        TCPListener(int port) throws IOException {
            serverSocket = new ServerSocket(port);
        }

        //结束
        public void close() {
            done = true;
            threadPool.shutdown();
            //必须调用close方法，不然可能会阻塞在receive方法处
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                serverSocket = null;
            }
        }

        @Override
        public void run() {
            super.run();
            try {
                while (!done) {
                    //申请新线程处理客户端请求,当客户端连接时，返回客户端套接字，否则一直阻塞
                    ClientTCPHandler handler = new ClientTCPHandler(serverSocket.accept());
                    TCPServer.this.handlers.add(handler);
                    //构建异步线程获取客户端消息
                    handler.readToPrint();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
