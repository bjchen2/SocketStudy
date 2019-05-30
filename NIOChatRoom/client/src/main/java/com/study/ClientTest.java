package com.study;

import com.study.bean.ServerInfo;
import com.study.core.Connector;
import com.study.core.IoContext;
import com.study.handle.ConnectorCloseChain;
import com.study.handle.ConnectorHandler;
import com.study.impl.SchedulerImpl;
import com.study.impl.IoStealingSelectorProvider;
import com.study.utils.CloseUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhengquan
 * @date 2019/1/18
 */
public class ClientTest {

    // 不考虑发送消耗，并发量：500*4/400*1000 = 5000/s 算上来回2次数据解析：1w/s
    // 能保证4000/s，不知是机器性能还是代码问题瓶颈(CPU跑满了)
    private static final int CLIENT_SIZE = 400;
    private static final int SEND_THREAD_SIZE = 4;
    private static final int SEND_THREAD_DELAY = 400;
    private static volatile boolean done;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void main(String[] args) throws IOException {
        ServerInfo info = UdpSearcher.searchServer(10000);
        System.out.println("Server:" + info);
        if (info == null) {
            return;
        }

        File cachePath = Foo.getCacheDir("client/test");
        IoContext.setup()
                .ioProvider(new IoStealingSelectorProvider(3))
                .scheduler(new SchedulerImpl(1))
                .start();

        // 当前连接数量
        int size = 0;
        final List<TcpClient> tcpClients = new ArrayList<>(CLIENT_SIZE);

        // 关闭时移除
        final ConnectorCloseChain closeChain = new ConnectorCloseChain() {
            @Override
            protected boolean consume(ConnectorHandler handler, Connector connector) {
                //noinspection SuspiciousMethodCalls
                tcpClients.remove(handler);
                if (tcpClients.size() == 0) {
                    CloseUtils.close(System.in);
                }
                return false;
            }
        };

        // 添加
        for (int i = 0; i < CLIENT_SIZE; i++) {
            try {
                TcpClient tcpClient = TcpClient.linkWith(info, cachePath, false);
                if (tcpClient == null) {
                    throw new NullPointerException();
                }
                // 添加关闭链式节点
                tcpClient.getCloseChain().appendLast(closeChain);
                tcpClients.add(tcpClient);
                System.out.println("连接成功：" + (++size));
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
                System.out.println("连接异常");
                break;
            }
        }


        System.in.read();

        Runnable runnable = () -> {
            while (!done) {
                TcpClient[] copyClients = tcpClients.toArray(new TcpClient[0]);
                for (TcpClient client : copyClients) {
                    client.send("Hello~~");
                }

                if (SEND_THREAD_DELAY > 0) {
                    try {
                        Thread.sleep(SEND_THREAD_DELAY);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        };

        List<Thread> threads = new ArrayList<>(SEND_THREAD_SIZE);
        for (int i = 0; i < SEND_THREAD_SIZE; i++) {
            Thread thread = new Thread(runnable);
            thread.start();
            threads.add(thread);
        }

        System.in.read();

        // 等待线程完成
        done = true;

        // 客户端结束操作
        TcpClient[] copyClients = tcpClients.toArray(new TcpClient[0]);
        for (TcpClient tcpClient : copyClients) {
            tcpClient.exit();
        }

        // 关闭框架线程池
        IoContext.close();

        // 强制结束处于等待的线程
        for (Thread thread : threads) {
            try {
                thread.interrupt();
            } catch (Exception ignored) {
            }
        }
    }

}
