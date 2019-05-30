package com.study.audio;


import com.study.handle.ConnectorHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


/**
 * 语音房间基本封装(支持双人聊天)
 *
 * @author zhengquan
 * @date 2019/2/26
 */
public class AudioRoom {
    private final String roomCode;
    private volatile ConnectorHandler handler1;
    private volatile ConnectorHandler handler2;

    public AudioRoom() {
        this.roomCode = getRandomString(5);
    }

    public String getRoomCode() {
        return roomCode;
    }

    /**
     * 获取房间里的所有连接
     *
     * @return 房间里的所有连接
     */
    public ConnectorHandler[] getConnectors() {
        List<ConnectorHandler> handlerList = new ArrayList<>(2);
        if (handler1 != null) {
            handlerList.add(handler1);
        }
        if (handler2 != null) {
            handlerList.add(handler2);
        }
        return handlerList.toArray(new ConnectorHandler[0]);
    }

    /**
     * 获取对方连接
     *
     * @param handler 当前连接
     * @return 对方连接
     */
    public ConnectorHandler getTheOtherHandler(ConnectorHandler handler) {
        return (handler1 == handler || handler1 == null) ? handler2 : handler1;

    }

    /**
     * 房间是否可以聊天(即双方是不是都在房间里)
     *
     * @return 是否可以聊天
     */
    public synchronized boolean isEnable() {
        return handler1 != null && handler2 != null;
    }


    /**
     * 加入房间
     *
     * @param handler 加入房间者
     * @return 加入是否成功
     */
    public synchronized boolean enterRoom(ConnectorHandler handler) {
        if (handler1 == null) {
            handler1 = handler;
            return true;
        } else if (handler2 == null) {
            handler2 = handler;
            return true;
        }
        return false;
    }

    /**
     * 退出房间
     *
     * @param handler 退出房间者
     * @return 返回另一个链接（如果另一个连接也退出了，返回null）
     */
    public synchronized ConnectorHandler exitRoom(ConnectorHandler handler) {
        if (handler1 == handler) {
            handler1 = null;
        } else if (handler2 == handler) {
            handler2 = null;
        }
        return handler1 == null ? handler2 : handler1;
    }

    /**
     * 生成一个简单的随机数字字符串
     *
     * @param length 字符串长度
     * @return 随机字符串
     */
    private String getRandomString(final int length) {
        final String str = "1234567890";
        final Random random = new Random();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(str.length());
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }
}
