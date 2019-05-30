package com.study;

import com.study.box.StringReceivePacket;
import com.study.handle.ConnectorHandler;
import com.study.handle.ConnectorStringPacketChain;

/**
 * 服务端信息统计类；statistics：统计资料
 * @author Cx
 * @version jdk8 and idea On 2019/5/12 18:07
 */
public class ServerStatistics {
    long sendSize;
    long receiveSize;

    ConnectorStringPacketChain statisticsChain(){
        return new StatisticsConnectorStringPacketChain();
    }

    class StatisticsConnectorStringPacketChain extends ConnectorStringPacketChain{
        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            receiveSize++;
            return false;
        }
    }
}
