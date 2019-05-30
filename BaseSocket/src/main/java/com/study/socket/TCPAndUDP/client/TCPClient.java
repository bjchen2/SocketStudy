package com.study.socket.TCPAndUDP.client;

import com.study.socket.TCPAndUDP.client.bean.ServerInfo;
import com.study.socket.TCPAndUDP.util.CloseUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;

/**
 * Created By Cx On 2019/3/17 10:19
 */
public class TCPClient {

    public static void linkedWith(ServerInfo serverInfo) throws IOException {
        Socket socket = new Socket();
        //连接远程服务端，连接超时时间为3s
        socket.connect(new InetSocketAddress(serverInfo.getAddress(),serverInfo.getTcpPort()),3000);
        //监听端口,建立子线程监听回复信息
        Reader reader = null;
        try{
            System.out.println("已连接服务器，服务器地址为：" + socket.getInetAddress() + ":" + socket.getPort());
            reader = new Reader(socket.getInputStream());
            reader.start();
            send2Server(socket);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            socket.close();
            if (reader != null){
                reader.close();
                reader.interrupt();
            }
        }
    }

    static class Reader extends Thread{
        private final InputStream inputStream;
        private boolean done;
        Reader(InputStream inputStream){
            this.inputStream = inputStream;
        }

        public void close(){
            done = true;
            CloseUtils.close(inputStream);
        }

        @Override
        public void run() {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            System.out.println("TCPClient读取线程已启动");
            String str;
            try {
                while (!done && (str = reader.readLine()) != null){
                    System.out.println("heeeeee");
                    if ("bye".equals(str)) {
                        System.out.println("TCPClient读取通信结束");
                        break;
                    }
                    System.out.println("TCPClient收到消息："+str);
                }
            }catch (IOException e){
                e.printStackTrace();
            }finally {
                close();
            }
        }
    }

    private static void send2Server(Socket socket) throws IOException {
        // 构建键盘输入流
        Scanner input = new Scanner(System.in);
        // 得到Socket输出流，并转换为打印流
        PrintStream socketPrintStream = new PrintStream(socket.getOutputStream());

        String str;
        do {
            str = input.nextLine();
            // 发送到服务器
            socketPrintStream.println(str);
        } while (!"bye".equals(str));

        // 资源释放
        socketPrintStream.close();
        input.close();
    }
}
