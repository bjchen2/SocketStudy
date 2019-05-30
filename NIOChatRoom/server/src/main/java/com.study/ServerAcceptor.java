package com.study;

import com.study.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

/**
 * 服务端线程监听者，监听客户端连接事件，当有事件就绪时，回调给TcpServer处理
 * @author Cx
 * @version jdk8 and idea On 2019/5/12 9:26
 */
public class ServerAcceptor extends Thread {
    private final AcceptListener listener;
    /**
     * 用于监听连接事件
     */
    private final Selector selector;
    /**
     * 运行同步栅栏,用于检测线程是否真正启动
     */
    private final CountDownLatch latch = new CountDownLatch(1);
    private boolean done = false;

    public ServerAcceptor(AcceptListener listener) throws IOException {
        super("Server-Accept-Thread");
        this.listener = listener;
        selector = Selector.open();
    }

    /**
     * 等待线程启动
     * @return 是否启动成功
     */
    boolean awaitRunning(){
        try {
            latch.await();
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        super.run();
        latch.countDown();
        Selector selector = this.selector;
        // 等待客户端连接
        do {
            try {
                //多路选择器查看当前连接到达通道数，若没有则阻塞，当wakeUp方法唤醒时返回0
                if (selector.select() == 0) {
                    //epoll bug会造成空轮询，详见官方bug6403933
                    if (done) {
                        break;
                    }
                    continue;
                }
                //获得事件，构建异步线程读取信息
                //使用迭代器获取所有事件，防止快速失败
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    if (done) {
                        break;
                    }
                    SelectionKey key = it.next();
                    it.remove();

                    if (key.isAcceptable()) {
                        //该通道接收到连接请求
                        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                        //获取客户端通道，因为已确认需要连接，所以不会阻塞
                        SocketChannel socketChannel = serverSocketChannel.accept();
                        //回调
                        listener.onNewSocketArrived(socketChannel);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (!done);

        System.out.println("ServerAcceptor已关闭！");
    }

    void exit() {
        done = true;
        CloseUtils.close(selector);
    }

    public Selector getSelector() {
        return selector;
    }

    /**
     * 连接事件就绪回调
     */
    interface AcceptListener {
        /**
         * 当有新的Socket到达时
         * @param channel 当前到达客户端的Channel
         */
        void onNewSocketArrived(SocketChannel channel);
    }
}
