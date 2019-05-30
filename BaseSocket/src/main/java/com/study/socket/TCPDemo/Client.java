package com.study.socket.TCPDemo;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;

/**
 * 客户端
 * Created By Cx On 2019/2/18 21:43
 */
public class Client {
    //监听端口
    private static final int REMOTE_PORT = 20000;
    //本地使用端口
    private static final int LOCAL_PORT = 20001;

    public static void main(String[] args) throws IOException {
        //创建一个绑定20000端口的socket
        Socket socket = createSocket();
        //初始化socket
        initSocket(socket);
        //连接远程服务端，连接超时时间为3s
        socket.connect(new InetSocketAddress(REMOTE_PORT),3000);
        //监听本地2000端口
        try{
            System.out.println("已连接服务器");
            System.out.println("本地地址为：" + socket.getLocalAddress() + ":" + socket.getLocalPort());
            System.out.println("服务器地址为：" + socket.getInetAddress() + ":" + socket.getPort());
            send2Server(socket);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            socket.close();
        }
    }

    //使用PrintStream包装发送，BufferedReader包装接收
    private static void send2Server(Socket socket) throws IOException {
        System.out.println("请输入需要向服务端发送的数据");
        Scanner in = new Scanner(System.in);
        String temp;
        //获取服务端的输出流，向服务端发送数据
        PrintStream writer = new PrintStream(socket.getOutputStream());
        do{
            temp=in.nextLine();
            writer.println(temp);
        }while(!temp.equals("bye"));
        //得到Socket的输入流，获取服务端的回复数据
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        System.out.println(reader.readLine());
        while ((temp = reader.readLine()) != null){
            System.out.println(temp);
        }
        //释放资源
        writer.close();
        reader.close();
    }

    //使用原生OutputStream发送，InputStream接收(好处：兼容性更好)
    private static void send2Server2(Socket socket) throws IOException {
        System.out.println("请输入需要向服务端发送的数据");
        Scanner in = new Scanner(System.in);
        String temp;
        //获取服务端的输出流，向服务端发送数据
        OutputStream writer = socket.getOutputStream();
        do{
            temp=in.nextLine();
            writer.write(temp.getBytes());
        }while(!temp.equals("bye"));
        writer.flush();
//        writer.write(1);
        System.out.println("发送数据结束");
        //得到Socket的输入流，获取服务端的回复数据
        InputStream reader = socket.getInputStream();
        byte[] buffer = new byte[1024];
        int length;
        while((length = reader.read(buffer)) != -1){
            System.out.println(new String(buffer,0,length));
        }
        //释放资源
        writer.close();
        reader.close();
    }


    private static Socket createSocket() throws IOException {
        /*
        // 无代理模式，等效于空构造函数
        Socket socket = new Socket(Proxy.NO_PROXY);
        // 新建一份具有HTTP代理的套接字，传输数据将通过www.baidu.com:8080端口转发
        Proxy proxy = new Proxy(Proxy.Type.HTTP,
                new InetSocketAddress(Inet4Address.getByName("www.baidu.com"), 8800));
        socket = new Socket(proxy);
        // 新建一个套接字，并且直接链接到本地20000的服务器上
        socket = new Socket("localhost", PORT);
        // 新建一个套接字，并且直接链接到本地20000的服务器上
        socket = new Socket(Inet4Address.getLocalHost(), PORT);
        // 新建一个套接字，并且直接链接到本地20000的服务器上，并且绑定到本地20001端口上
        socket = new Socket("localhost", PORT, Inet4Address.getLocalHost(), LOCAL_PORT);
        socket = new Socket(Inet4Address.getLocalHost(), PORT, Inet4Address.getLocalHost(), LOCAL_PORT);
        */
        Socket socket = new Socket();
        // 绑定到本地20001端口
        socket.bind(new InetSocketAddress(LOCAL_PORT));
        return socket;
    }

    private static void initSocket(Socket socket) throws SocketException {
        // 设置accept数据超时时间为2秒，超出后抛出异常
        socket.setSoTimeout(2000);
        // 是否复用未完全关闭的Socket地址，对于指定bind操作后的套接字有效
        // 一般time_wait阶段是不能被bind的，所以一般关闭后2分钟之内是不能被占用的
        socket.setReuseAddress(true);
        // 是否禁用Nagle算法（默认为false，Nagle算法是延迟一段时间发一个ACK,期间收到或发送数据量小的数据，合并成一个大的数据块，
        // 然后进行封包，若关闭，每个字节都会发一个ACK）
        socket.setTcpNoDelay(true);
        // 是否需要在长时无数据响应时发送确认数据（类似心跳包），时间大约为2小时，如果无回送，则会抛异常
        socket.setKeepAlive(true);
        // 对于close关闭操作行为进行怎样的处理；默认为false，0
        // false、0：默认情况，关闭时立即返回，底层系统接管输出流，将缓冲区内的数据发送完成
        // true、int x：关闭时最长阻塞x毫秒，若在时间内未处理完数据，缓冲区数据抛弃，直接发送RST结束命令到对方，并无需经过2MSL等待
        //若处理完则直接往下执行
        socket.setSoLinger(true, 20);
        // 是否让紧急数据内敛，默认false；紧急数据通过socket.sendUrgentData(1);发送
        // sendUrgentData(int) 将int值（32位）的低八位发送，服务器进行响应，可用作心跳包，
        // 一般false情况下服务端对紧急数据是无感知的（逻辑层之前就进行响应）
        // 一般不推荐设为true，因为逻辑层都是行为数据，设为true以后紧急数据也会出现在逻辑层，污染行为数据（可能会误将紧急数据当作行为数据）
        socket.setOOBInline(true);
        // 设置接收、发送缓冲器最大值（如果超出就要拆分）
        socket.setReceiveBufferSize(64 * 1024 * 1024);
        socket.setSendBufferSize(64 * 1024 * 1024);
        // 设置性能参数：长链接，延迟低(平均每秒发包次数)，带宽(平均每秒发包数)的权重（默认）
        // 一般延迟低带宽就高，如：每隔1s发一个包 或 每隔2s发5个包，后者延迟低，但带宽高
        //传输文件注重带宽，则可给带宽分配高权值，0，0，1
        //及时通信注重长连接和低延迟，则可：1,1,0
        socket.setPerformancePreferences(1, 1, 0);
    }
}
