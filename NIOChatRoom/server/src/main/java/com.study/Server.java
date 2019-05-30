package com.study;


import com.study.constants.TcpConstants;
import com.study.core.IoContext;
import com.study.impl.SchedulerImpl;
import com.study.impl.IoStealingSelectorProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 服务端
 *
 * @author cxd27419
 */
public class Server {
    public static void main(String[] args) throws IOException {
        File cachePath = Foo.getCacheDir("server");
        //初始化上下文
        IoContext.setup().ioProvider(new IoStealingSelectorProvider(3)).scheduler(new SchedulerImpl(1)).start();
        TcpServer tcpServer = new TcpServer(TcpConstants.PORT_SERVER,cachePath);
        boolean isSucceed = tcpServer.start();
        if (!isSucceed) {
            System.out.println("Start TCP server failed!");
            return;
        }

        UdpProvider.start(TcpConstants.PORT_SERVER);

        //启动GUI界面
        FooGui gui = new FooGui("Clink-Server", tcpServer::getStatusString);
        gui.doShow();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String str;
        do {
            str = bufferedReader.readLine();
            if (str == null || Foo.COMMAND_EXIT.equalsIgnoreCase(str)){
                //当客户端和服务端异常中断时readLine可能返回null
                break;
            }
            if(str.length() == 0){
                continue;
            }
            tcpServer.broadcast(str);
        } while (true);

        UdpProvider.stop();
        tcpServer.stop();

        //关闭上下文
        IoContext.close();
        gui.doDismiss();
    }
}
