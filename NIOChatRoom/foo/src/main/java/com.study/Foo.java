package com.study;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 客户端和服务端公用方法
 *
 * @author cxd27419
 */
public class Foo {
    /**
     * 缓存存放的公共目录
     */
    private static final String CACHE_DIR = "cache";

    /**
     * 退出命令
     */
    public static final String COMMAND_EXIT = "00bye00";

    //群相关命令
    //群操作前缀
    public static final String COMMAND_GROUP_PREFIX = "--m g ";
    /**
     * 加入群命令
     */
    public static final String COMMAND_GROUP_JOIN = "--m g join";
    /**
     * 退出群命令
     */
    public static final String COMMAND_GROUP_LEAVE = "--m g leave";
    /**
     * 默认群名
     */
    public static final String DEFAULT_GROUP_NAME = "IMOOC";

    //语音通信，客户端请求服务器命令
    /**
     * 绑定Stream到一个命令连接(带参数：另一个链接的唯一标识)
     */
    public static final String COMMAND_CONNECTOR_BIND = "--m c bind ";
    /**
     * 创建对话房间
     */
    public static final String COMMAND_AUDIO_CREATE_ROOM = "--m a create";
    /**
     * 加入对话房间(带参数：需要加入房间号)
     */
    public static final String COMMAND_AUDIO_JOIN_ROOM = "--m a join ";
    /**
     * 主动离开对话房间
     */
    public static final String COMMAND_AUDIO_LEAVE_ROOM = "--m a leave";

    // 语音通信，服务器回送客户端命令
    /**
     * 回送服务器上的唯一标示(带参数：创建链接的唯一标识)
     */
    public static final String COMMAND_INFO_NAME = "--i server ";
    /**
     * 回送语音房间号(带参数：创建房间的唯一标识)
     */
    public static final String COMMAND_INFO_AUDIO_ROOM = "--i a room ";
    /**
     * 回送语音开始(带参数：房间号)
     */
    public static final String COMMAND_INFO_AUDIO_START = "--i a start ";
    /**
     * 回送语音结束
     */
    public static final String COMMAND_INFO_AUDIO_STOP = "--i a stop";
    /**
     * 回送语音操作错误
     */
    public static final String COMMAND_INFO_AUDIO_ERROR = "--i a error";

    //创建群
    public static final String COMMAND_GROUP_CREATE = "create";
    //给群发送信息
    public static final String COMMAND_GROUP_SEND = "send";
    public static final String COMMAND_HELP = "[操作指南]\r\n" +
            "创建群 --m g create [群名]\r\n" +
            "加入群 --m g join [群名]\r\n" +
            "退出群 --m g leave [群名]\r\n" +
            "群发信息 --m g send [群名] [信息体]";

    /**
     * 创建目录
     *
     * @param dir 需要创建的目录
     * @return 创建的目录
     */
    public static File getCacheDir(String dir) {
        String path = System.getProperty("user.dir") + File.separator + CACHE_DIR + File.separator + dir;
        File file = new File(path);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                throw new RuntimeException("Create path error : " + path);
            }
        }
        return file;
    }

    /**
     * 创建临时文件
     *
     * @param parent 文件对应的文件夹
     * @return 创建的临时文件
     */
    public static File createRandomTemp(File parent) {
        //创建一个临时文件，使用UUID防止文件名冲突
        String fileName = UUID.randomUUID().toString().concat(".tmp");
        //从父抽象路径名和子路径名字符串创建新的File实例。
        File file = new File(parent, fileName);
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }
}
