package com.study.handle;


import com.study.box.StringReceivePacket;

/**
 * 默认String接收节点，不做任何事情
 *
 * @author Cx
 * @version jdk8 and idea On 2019/5/12 10:41
 */
public class DefaultNonConnectorStringPacketChain extends ConnectorStringPacketChain {
    @Override
    protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
        return false;
    }
}
