package com.study.handle;


import com.study.core.Connector;

/**
 * 默认关闭连接链式结构实现类（只进行信息打印，不会进行具体消费）
 * @author Cx
 * @version jdk8 and idea On 2019/5/12 10:41
 */
public class DefaultPrintConnectorCloseChain extends ConnectorCloseChain {
    @Override
    protected boolean consume(ConnectorHandler handler, Connector connector) {
        System.out.println(handler.getClientInfo() + "已退出,Key:"+handler.getKey().toString());
        return false;
    }
}
