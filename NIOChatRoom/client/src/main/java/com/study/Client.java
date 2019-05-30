package com.study;

import com.study.bean.ServerInfo;
import com.study.box.FileSendPacket;
import com.study.core.Connector;
import com.study.core.IoContext;
import com.study.core.ScheduleJob;
import com.study.core.schedule.IdleTimeoutScheduleJob;
import com.study.handle.ConnectorCloseChain;
import com.study.handle.ConnectorHandler;
import com.study.impl.IoSelectorProvider;
import com.study.impl.SchedulerImpl;
import com.study.utils.CloseUtils;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * 业务层，客户端
 *
 * @author cxd27419
 */
public class Client {
    public static void main(String[] args) throws IOException {
        File cachePath = Foo.getCacheDir("client");
        //初始化上下文
        IoContext.setup().ioProvider(new IoSelectorProvider()).scheduler(new SchedulerImpl(1)).start();
        ServerInfo info = UdpSearcher.searchServer(10000);
        System.out.println("Server:" + info);

        if (info != null) {
            TcpClient tcpClient = null;
            try {
                tcpClient = TcpClient.linkWith(info, cachePath);
                if (tcpClient == null) {
                    return;
                }
                //添加关闭连接责任链，当服务端关闭时，自动关闭键盘输入流
                tcpClient.getCloseChain().appendLast(new ConnectorCloseChain() {
                    @Override
                    protected boolean consume(ConnectorHandler handler, Connector connector) {
                        CloseUtils.close(System.in);
                        return true;
                    }
                });
                //添加心跳包发送机制
                ScheduleJob scheduleJob = new IdleTimeoutScheduleJob(10, TimeUnit.SECONDS,tcpClient);
                tcpClient.schedule(scheduleJob);

                write(tcpClient);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (tcpClient != null) {
                    tcpClient.exit();
                }
            }
        }
        //关闭上下文
        IoContext.close();
    }

    private static void write(TcpClient tcpClient) throws IOException {
        // 构建键盘输入流
        InputStream in = System.in;
        BufferedReader input = new BufferedReader(new InputStreamReader(in));

        do {
            // 键盘读取一行
            String str = input.readLine();
            //文件传输命令: --f filePath
            String fileStartCmd = "--f";
            if (str == null || Foo.COMMAND_EXIT.equalsIgnoreCase(str)) {
                break;
            }
            if(str.length() == 0){
                continue;
            }
            if (str.startsWith(fileStartCmd)) {
                //文件传输
                String[] strArray = str.split(" ");
                if (strArray.length > 1) {
                    String filePath = strArray[1];
                    File file = new File(filePath);
                    if (file.exists() && file.isFile()) {
                        FileSendPacket packet = new FileSendPacket(file);
                        tcpClient.send(packet);
                        continue;
                    }
                }
            }
            // 发送字符串到服务器
            tcpClient.send(str);

        } while (true);
    }
}
