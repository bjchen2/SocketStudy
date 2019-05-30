package com.study;

import com.study.box.StringReceivePacket;
import com.study.handle.ConnectorHandler;
import com.study.handle.ConnectorStringPacketChain;

import java.util.ArrayList;
import java.util.List;

/**
 * 群
 * @author Cx
 * @version jdk8 and idea On 2019/5/12 18:34
 */
public class Group {
    /**
     * 群名称
     */
    private final String name;
    private final GroupMessageAdapter adapter;
    private final List<ConnectorHandler> members = new ArrayList<>();

    Group(String name, GroupMessageAdapter adapter) {
        this.name = name;
        this.adapter = adapter;
    }

    public String getName() {
        return name;
    }

    boolean addMember(ConnectorHandler handler){
        synchronized (members){
            if (!members.contains(handler)){
                members.add(handler);
                handler.getStringPacketChain().appendLast(new ForwardConnectorStringPacketChain());
                System.out.println("Group["+name+"] add new member:" + handler.getClientInfo());
                return true;
            }
        }
        return false;
    }

    boolean removeMember(ConnectorHandler handler){
        synchronized (members){
            if (members.remove(handler)){
                //获取该用户的责任链，并把该群移出该用户的责任链
                handler.getStringPacketChain().remove(ForwardConnectorStringPacketChain.class);
                System.out.println("Group["+name+"] leave member:" + handler.getClientInfo());
                return true;
            }
        }
        return false;
    }

    /**
     * 消息转发责任链
     */
    private class ForwardConnectorStringPacketChain extends ConnectorStringPacketChain{
        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            String msg = "[" + handler.getClientInfo() + "]发送消息：" + stringReceivePacket.entity();
            synchronized (members){
                for (ConnectorHandler member : members){
                    if (member == handler){
                        continue;
                    }
                    adapter.sendMessageToClient(member,msg);
                }
                return true;
            }
        }
    }

    interface GroupMessageAdapter{
        void sendMessageToClient(ConnectorHandler handler, String msg);
    }
}
