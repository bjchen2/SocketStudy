package com.study;


import com.study.audio.AudioRoom;
import com.study.box.StringReceivePacket;
import com.study.core.Connector;
import com.study.core.ScheduleJob;
import com.study.core.schedule.IdleTimeoutScheduleJob;
import com.study.handle.ConnectorHandler;
import com.study.handle.ConnectorCloseChain;
import com.study.handle.ConnectorHandlerChain;
import com.study.handle.ConnectorStringPacketChain;
import com.study.utils.CloseUtils;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 将clientHandlerList的所有增删遍历操作进行同步，防止快速失败等
 *
 * @author cxd27419
 */
public class TcpServer implements ServerAcceptor.AcceptListener , Group.GroupMessageAdapter {
    private final int port;
    private final File cachePath;
    private ServerAcceptor acceptor;
    private final List<ConnectorHandler> connectorHandlerList = new ArrayList<>();
    /**
     * 群容器，key为群名，value为群
     */
    private final Map<String,Group> groups = new HashMap<>();
    /**
     * NIO ServerChannel
     */
    private ServerSocketChannel serverChannel;

    private final ServerStatistics statistics = new ServerStatistics();

    public TcpServer(int port, File cachePath) {
        this.port = port;
        this.cachePath = cachePath;
        this.groups.put(Foo.DEFAULT_GROUP_NAME,new Group(Foo.DEFAULT_GROUP_NAME, this));
    }

    public boolean start() {
        try {
            //启动子线程监听客户端
            acceptor = new ServerAcceptor(this);
            serverChannel = ServerSocketChannel.open();
            //设置为非阻塞
            serverChannel.configureBlocking(false);
            //绑定本地端口
            serverChannel.socket().bind(new InetSocketAddress(port));
            //注册事件,客户端连接到达时监听
            serverChannel.register(acceptor.getSelector(), SelectionKey.OP_ACCEPT);
            acceptor.start();
            if (acceptor.awaitRunning()) {
                System.out.println("服务器准备就绪~~");
                System.out.println("服务器信息:" + serverChannel.getLocalAddress());
                return true;
            } else {
                System.out.println("启动异常");
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void stop() {
        //退出监听
        if (acceptor != null) {
            acceptor.exit();
        }

        //清空监听列表,并关闭所有监听者
        ConnectorHandler[] connectorHandlers;
        synchronized (connectorHandlerList) {
            //因为在退出方法会调用关闭连接责任链执行，而关闭责任链会回调当前调用的clientHandler退出列表
            //所以若使用for循环遍历clientHandlerList退出,在迭代过程中会导致快速失败
            connectorHandlers = connectorHandlerList.toArray(new ConnectorHandler[0]);
            connectorHandlerList.clear();
        }
        for (ConnectorHandler connectorHandler : connectorHandlers) {
            connectorHandler.exit();
        }
        //关闭通道
        CloseUtils.close(serverChannel);
    }

    /**
     * 系统广播通知所有用户
     * @param str 通知信息
     */
    void broadcast(String str) {
        str = "[系统通知]".concat(str);
        //todo 视频中，此处遍历应该像stop方法中，先转换成数组，再遍历，但我觉得没必要
        synchronized(connectorHandlerList){
            for (ConnectorHandler connectorHandler : connectorHandlerList) {
                sendMessageToClient(connectorHandler,str);
            }
        }
    }

    /**
     * 发送消息给客户端
     * @param handler 发送客户端的处理类
     * @param msg 发送消息
     */
    @Override
    public void sendMessageToClient(ConnectorHandler handler, String msg){
        handler.send(msg);
        statistics.sendSize++;
    }

    /**
     * @return 当前状态信息数组
     */
    Object[] getStatusString(){
        return new String[]{
                "客户端数量：" + connectorHandlerList.size(),
                "已发送信息数量：" + statistics.sendSize,
                "已接收信息数量: " + statistics.receiveSize
        };
    }

    /**
     * 当客户端到达时，建立ClientHandler处理
     * @param channel 当前到达客户端的Channel
     */
    @Override
    public void onNewSocketArrived(SocketChannel channel) {
        try {
            ConnectorHandler connectorHandler = new ConnectorHandler(channel,cachePath);
            System.out.println(connectorHandler.getClientInfo() + "连接成功");

            //添加收到消息的处理责任链
            connectorHandler.getStringPacketChain().appendLast(statistics.statisticsChain())
                    .appendLast(new ParseCommandConnectorStringPacketChain())
                    .appendLast(new ParseAudioStreamCommandStringPacketChain());
            //添加关闭连接的责任链
            connectorHandler.getCloseChain().appendLast(new RemoveQueueConnectorClosedChain())
                    .appendLast(new RemoveAudioQueueOnConnectorClosedChain());

            //添加定时任务，心跳检测
            ScheduleJob scheduleJob = new IdleTimeoutScheduleJob(20, TimeUnit.SECONDS, connectorHandler);
            connectorHandler.schedule(scheduleJob);

            synchronized (connectorHandlerList){
                connectorHandlerList.add(connectorHandler);
                System.out.println("当前客户端数量：" + connectorHandlerList.size());
            }

            //回送当前连接的唯一标示
            sendMessageToClient(connectorHandler, Foo.COMMAND_INFO_NAME + connectorHandler.getKey().toString());
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("客户端连接异常：" + e.getMessage());
        }
    }

    /**
     * 连接关闭责任链
     * 当连接关闭时移除队列
     */
    private class RemoveQueueConnectorClosedChain extends ConnectorCloseChain{
        @Override
        protected boolean consume(ConnectorHandler handler, Connector connector) {
           synchronized (connectorHandlerList){
               connectorHandlerList.remove(handler);
               //移除群聊中的客户端
               Group group = groups.get(Foo.DEFAULT_GROUP_NAME);
               group.removeMember(handler);
           }
            return false;
        }
    }

    /**
     * 解析命令责任链（解析字符串数据包连接）
     */
    private class ParseCommandConnectorStringPacketChain extends ConnectorStringPacketChain{
        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            String str = stringReceivePacket.entity();
            if (str.startsWith(Foo.COMMAND_GROUP_JOIN)){
                //加入群
                Group group = groups.get(Foo.DEFAULT_GROUP_NAME);
                if (group.addMember(handler)){
                    sendMessageToClient(handler,"Join Group:" + group.getName());
                }
                return true;
            }else if (str.startsWith(Foo.COMMAND_GROUP_LEAVE)){
                //退出群
                Group group = groups.get(Foo.DEFAULT_GROUP_NAME);
                if (group.removeMember(handler)){
                    sendMessageToClient(handler,"Leave Group:" + group.getName());
                }
                return true;
            }
            return false;
        }

        @Override
        protected boolean consumeAgain(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            //如果第一次未消费，则说明该用户未加入任何群，则通知用户
            sendMessageToClient(handler,"消息未发出，您未加入任何群");
            return true;
        }
    }


    /**
     * 房间映射表，房间号-房间映射
     */
    private final HashMap<String, AudioRoom> audioRoomMap = new HashMap<>(50);
    /**
     * 链接与房间的映射表，音频链接-房间映射
     */
    private final HashMap<ConnectorHandler, AudioRoom> audioStreamRoomMap = new HashMap<>();
    private final HashMap<ConnectorHandler, ConnectorHandler> audioCmdToStreamMap = new HashMap<>();
    private final HashMap<ConnectorHandler, ConnectorHandler> audioStreamToCmdMap = new HashMap<>();

    /**
     * 音频命令解析
     */
    private class ParseAudioStreamCommandStringPacketChain extends ConnectorHandlerChain<StringReceivePacket> {
        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            String str = stringReceivePacket.entity();
            if (str.startsWith(Foo.COMMAND_CONNECTOR_BIND)) {
                //绑定命令,将音频流绑定到当前命令流上
                String key = str.substring(Foo.COMMAND_CONNECTOR_BIND.length());
                ConnectorHandler audioStreamConnector = findConnectorFromKey(key);
                if (audioStreamConnector != null) {
                    //添加绑定关系
                    audioCmdToStreamMap.put(handler, audioStreamConnector);
                    audioStreamToCmdMap.put(audioStreamConnector, handler);
                    //转换为桥接模式
                    audioStreamConnector.changeToBridge();
                }
            } else if (str.startsWith(Foo.COMMAND_AUDIO_CREATE_ROOM)) {
                ConnectorHandler audioStreamConnector = findAudioStreamConnector(handler);
                if (audioStreamConnector != null) {
                    AudioRoom room = createNewRoom();
                    joinRoom(room, audioStreamConnector);
                    sendMessageToClient(handler, Foo.COMMAND_INFO_AUDIO_ROOM + room.getRoomCode());
                }
            } else if (str.startsWith(Foo.COMMAND_AUDIO_LEAVE_ROOM)) {
                ConnectorHandler audioStreamConnector = findAudioStreamConnector(handler);
                if (audioStreamConnector != null) {
                    //任意一人离开都销毁房间
                    dissolveRoom(audioStreamConnector);
                    sendMessageToClient(handler, Foo.COMMAND_INFO_AUDIO_STOP);
                }
            }else if(str.startsWith(Foo.COMMAND_AUDIO_JOIN_ROOM)){
                ConnectorHandler audioStreamConnector = findAudioStreamConnector(handler);
                if(audioStreamConnector!=null){
                    String roomCode = str.substring(Foo.COMMAND_AUDIO_JOIN_ROOM.length());
                    AudioRoom room = audioRoomMap.get(roomCode);
                    if(room!=null && joinRoom(room,audioStreamConnector)){
                        //如果房间存在且加入房间成功
                        ConnectorHandler theOtherHandler = room.getTheOtherHandler(audioStreamConnector);

                        //绑定对方为桥接发送者，自己为对方的桥接发送者
                        theOtherHandler.bindToBridge(audioStreamConnector.getSender());
                        audioStreamConnector.bindToBridge(theOtherHandler.getSender());

                        sendMessageToClient(handler,Foo.COMMAND_INFO_AUDIO_START);
                        sendMessageToClient(theOtherHandler,Foo.COMMAND_INFO_AUDIO_START);
                    }else{
                        sendMessageToClient(handler,Foo.COMMAND_INFO_AUDIO_ERROR);
                    }
                }
            }else {
                return false;
            }
            return true;
        }
    }

    /**
     * 销毁房间 ； dissolve:解散
     * @param audioStreamConnector 离开房间者
     */
    private void dissolveRoom(ConnectorHandler audioStreamConnector) {
        AudioRoom room = audioStreamRoomMap.get(audioStreamConnector);
        if (room == null) {
            return;
        }
        ConnectorHandler[] connectorHandlers = room.getConnectors();
        for (ConnectorHandler connectorHandler : connectorHandlers) {
            //解除桥接
            connectorHandler.unBindToBridge();
            audioStreamRoomMap.remove(connectorHandler);
            if (connectorHandler != audioStreamConnector) {
                //告诉对方，房间已解散
                sendStreamConnectorMessage(connectorHandler, Foo.COMMAND_INFO_AUDIO_STOP);
            }
        }

        audioRoomMap.remove(room.getRoomCode());
    }

    /**
     * 加入房间
     * @param room 房间
     * @param streamConnector 加入者
     * @return 加入是否成功
     */
    private boolean joinRoom(AudioRoom room, ConnectorHandler streamConnector) {
        if (room.enterRoom(streamConnector)) {
            audioStreamRoomMap.put(streamConnector,room);
            return true;
        }
        return false;
    }

    /**
     * 创建新房间
     * @return 创建的房间
     */
    private AudioRoom createNewRoom() {
        AudioRoom room;
        do {
            room = new AudioRoom();
        } while (audioRoomMap.containsKey(room.getRoomCode()));
        audioRoomMap.put(room.getRoomCode(), room);
        return room;
    }

    /**
     * 获得当前命令链接操纵的桥接链接对象
     * @param handler 当前命令链接
     * @return 桥接链接
     */
    private ConnectorHandler findAudioStreamConnector(ConnectorHandler handler) {
        ConnectorHandler connectorHandler = audioCmdToStreamMap.get(handler);
        if (connectorHandler == null) {
            sendMessageToClient(handler, Foo.COMMAND_INFO_AUDIO_ERROR);
        }
        return connectorHandler;
    }

    /**
     * 获取当前key对应的链接
     * @param key 链接唯一标识
     * @return 对应链接
     */
    private ConnectorHandler findConnectorFromKey(String key) {
        synchronized (connectorHandlerList) {
            for (ConnectorHandler connectorHandler : connectorHandlerList) {
                if (connectorHandler.getKey().toString().equalsIgnoreCase(key)) {
                    return connectorHandler;
                }
            }
        }
        return null;
    }

    /**
     * 音频链接关闭时。退出音频房间等操作
     */
    private class RemoveAudioQueueOnConnectorClosedChain extends ConnectorHandlerChain<Connector> {
        @Override
        protected boolean consume(ConnectorHandler handler, Connector connector) {
            if(audioCmdToStreamMap.containsKey(handler)){
                //如果是命令链接
                audioCmdToStreamMap.remove(handler);
            }else if(audioStreamToCmdMap.containsKey(handler)){
                //如果是音频链接
                audioStreamToCmdMap.remove(handler);
                dissolveRoom(handler);
            }
            return false;
        }
    }

    /**
     * 给音频流对应的命令流发送消息
     * @param streamConnector 音频流
     * @param msg 消息
     */
    private void sendStreamConnectorMessage(ConnectorHandler streamConnector,String msg){
        if(streamConnector!=null){
            ConnectorHandler audioCmdConnector = findAudioCmdConnector(streamConnector);
            sendMessageToClient(audioCmdConnector,msg);
        }
    }

    private ConnectorHandler findAudioCmdConnector(ConnectorHandler streamConnector) {
        return audioStreamToCmdMap.get(streamConnector);
    }
}
