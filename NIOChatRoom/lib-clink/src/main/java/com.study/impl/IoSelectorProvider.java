package com.study.impl;

import com.study.core.IoProvider;
import com.study.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 提供者实现类，提供注册功能
 * Created By Cx On 2019/3/30 18:19
 *
 * @author cxd27419
 */
public class IoSelectorProvider implements IoProvider {

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    /**
     * 是否处于注册input过程中
     */
    private final AtomicBoolean inRegInput = new AtomicBoolean(false);
    /**
     * 是否处于注册output过程中
     */
    private final AtomicBoolean inRegOutput = new AtomicBoolean(false);

    private final Selector readSelector;
    private final Selector writeSelector;

    /**
     * 用于存储每个SelectionKey对应的回调对象
     */
    private final HashMap<SelectionKey, Runnable> inputCallbackMap = new HashMap<>();
    private final HashMap<SelectionKey, Runnable> outputCallbackMap = new HashMap<>();

    private final ExecutorService inputHandlePool;
    private final ExecutorService outputHandlePool;

    public IoSelectorProvider() throws IOException {
        readSelector = Selector.open();
        writeSelector = Selector.open();

        inputHandlePool = Executors.newFixedThreadPool(4,
                new NameableThreadFactory("IoProvider-Input-Thread-"));
        outputHandlePool = Executors.newFixedThreadPool(4,
                new NameableThreadFactory("IoProvider-Output-Thread-"));

        // 开始输出输入的监听
        startRead();
        startWrite();
    }

    @Override
    public void register(HandleProviderCallback callback) throws Exception {
        SelectionKey key;
        if (SelectionKey.OP_READ == callback.ops) {
            key = registerSelection(callback.channel, readSelector, SelectionKey.OP_READ, inRegInput, inputCallbackMap, callback);
        } else {
            key = registerSelection(callback.channel, writeSelector, SelectionKey.OP_WRITE, inRegOutput, outputCallbackMap, callback);
        }

        if (key == null) {
            throw new IOException("注册失败。[通道]:" + callback.channel + "[ops]:" + callback.ops);
        }
    }

    @Override
    public void unregister(SocketChannel channel) {
        unRegisterSelection(channel, readSelector, inputCallbackMap, inRegInput);
        unRegisterSelection(channel, writeSelector, outputCallbackMap, inRegOutput);
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            inputHandlePool.shutdown();
            outputHandlePool.shutdown();

            inputCallbackMap.clear();
            outputCallbackMap.clear();

            CloseUtils.close(readSelector, writeSelector);
        }
    }

    private void startRead() {
        Thread thread = new SelectThread("Clink IoSelectorProvider ReadSelector Thread",
                isClosed,inRegInput,readSelector,inputCallbackMap,inputHandlePool,SelectionKey.OP_READ);
        thread.start();
    }

    private void startWrite() {
        Thread thread = new SelectThread("Clink IoSelectorProvider WriteSelector Thread",
                isClosed,inRegOutput,writeSelector,outputCallbackMap,outputHandlePool,SelectionKey.OP_WRITE);
        thread.start();
    }

    //注册事件
    private static SelectionKey registerSelection(SocketChannel socketChannel, Selector selector, int ops, AtomicBoolean inReg,
                                                  HashMap<SelectionKey, Runnable> callbackMap, Runnable runnable) {
        synchronized (inReg) {
            inReg.set(true);
            //唤醒selector，让其不处于select状态阻塞
            selector.wakeup();
            try {
                SelectionKey key = socketChannel.keyFor(selector);
                //该通道在该选择器上是否注册
                if (key != null) {
                    key.interestOps(key.interestOps() | ops);
                } else {
                    //该通道未在该selector上注册过,注册到selector
                    key = socketChannel.register(selector, ops);
                    //注册回调到map
                    callbackMap.put(key, runnable);
                }
                return key;
            } catch (ClosedChannelException | CancelledKeyException | ClosedSelectorException  e) {
                return null;
            } finally {
                inReg.set(false);
                try {
                    //因为可能没有线程阻塞，所以要捕获异常
                    inReg.notifyAll();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 解除注册
     */
    private static void unRegisterSelection(SocketChannel channel, Selector selector,
                                            Map<SelectionKey, Runnable> callbackMap,AtomicBoolean locker) {
        //集合中的元素变少，需要修改locker值，唤醒selector，让其重新进行新的轮询
        synchronized (locker) {
            locker.set(true);
            selector.wakeup();
            try {
                if (channel.isRegistered()) {
                    SelectionKey key = channel.keyFor(selector);
                    if (key != null) {
                        //取消所有事件的监听
                        key.cancel();
                        callbackMap.remove(key);
                    }
                }
            }finally {
                locker.set(false);
                try {
                    //因为可能没有线程阻塞，所以要捕获异常
                    locker.notifyAll();
                } catch (Exception ignored) {
                }
            }
        }
    }

    //根据ops处理key
    private static void handleSelection(SelectionKey key, int ops, HashMap<SelectionKey, Runnable> callbackMap,
                                        ExecutorService handlePool, AtomicBoolean locker) {
        //集合中的元素未发生变化，不用修改locker值
        synchronized (locker){
            try {
                //取消对keyOps的监听，直到完成读操作，否则startRead的select方法会一直非阻塞
                //readyOps返回当前就绪操作集
                key.interestOps(key.interestOps() & ~ops);
            }catch (CancelledKeyException e){
                //如果监听事件已取消，直接返回即可
                return;
            }
        }
        Runnable runnable = callbackMap.get(key);
        if (runnable != null && !handlePool.isShutdown()) {
            //异步调度
            handlePool.execute(runnable);
        }
    }

    //等待注册完成
    private static void waitSelection(final AtomicBoolean locker) {
        synchronized (locker) {
            if (locker.get()) {
                try {
                    locker.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class SelectThread extends Thread{
        private final AtomicBoolean isClosed;
        private final AtomicBoolean locker;
        private final Selector selector;
        private final HashMap<SelectionKey,Runnable> callbackMap;
        private final ExecutorService threadPool;
        private final int keyOps;

        SelectThread(String threadName, AtomicBoolean isClosed, AtomicBoolean locker, Selector selector,
                            HashMap<SelectionKey, Runnable> callbackMap, ExecutorService threadPool, int keyOps) {
            super(threadName);
            this.locker = locker;
            this.isClosed = isClosed;
            this.selector = selector;

            this.callbackMap = callbackMap;
            this.threadPool = threadPool;
            this.keyOps = keyOps;
            this.setPriority(Thread.MAX_PRIORITY);
        }

        @Override
        public void run() {
            super.run();
            while (!isClosed.get()) {
                try {
                    if (selector.select() == 0) {
                        waitSelection(locker);
                        continue;
                    }else if (locker.get()){
                        //如果当前处于注册事件状态，则是因为register方法唤醒的select，所以需先等待锁释放
                        waitSelection(locker);
                    }
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = keys.iterator();
                    while(iterator.hasNext()){
                        SelectionKey key = iterator.next();
                        if (key.isValid()) {
                            handleSelection(key, keyOps, callbackMap, threadPool,locker);
                        }
                    }
                    keys.clear();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClosedSelectorException e){
                    break;
                }
            }
        }
    }
}
