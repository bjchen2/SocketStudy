package com.study.impl.stealing;

import com.study.core.IoProvider;
import com.study.core.IoTask;
import com.study.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可窃取任务线程
 * @author Cx
 * @version jdk8 and idea On 2019/5/26 20:14
 */
public abstract class StealingSelectorThread extends Thread {
    private static final int MAX_ONCE_READ_TASK = 128;
    private static final int MAX_ONCE_WRITE_TASK = 32;
    private static final int MAX_ONCE_RUN_TASK = MAX_ONCE_READ_TASK + MAX_ONCE_WRITE_TASK;
    /**
     * 允许的操作
     */
    private static final int VALID_OPS = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
    private final Selector selector;
    /**
     * 是否还处于运行中
     */
    private volatile boolean running = true;

    /**
     * 已就绪任务队列
     */
    private final ArrayBlockingQueue<IoTask> readyTaskQueue = new ArrayBlockingQueue<>(MAX_ONCE_RUN_TASK);
    /**
     * 待注册任务队列,注册任务时不打断selector，而是放在该队列中等待被调度
     */
    private final ConcurrentLinkedQueue<IoTask> registerTaskQueue = new ConcurrentLinkedQueue<>();
    /**
     * 任务饱和度量
     */
    private final AtomicLong saturatingCapacity = new AtomicLong();
    /**
     * 用于多线程任务窃取协同工作
     */
    private StealingService stealingService;

    /**
     * 解除注册锁
     */
    private final AtomicBoolean unregisterLocker = new AtomicBoolean(false);

    protected StealingSelectorThread(Selector selector) {
        this.selector = selector;
    }

    /**
     * 获取任务就绪的队列
     *
     * @return
     */
    public Queue<IoTask> getReadyTaskQueue() {
        return readyTaskQueue;
    }

    /**
     * @return 获取当前线程的任务饱和度
     */
    long getSaturatingCapacity() {
        if (selector.isOpen()) {
            return saturatingCapacity.get();
        }
        return -1;
    }

    /**
     * 绑定窃取其他线程任务的服务逻辑
     *
     * @param stealingService
     */
    public void setStealingService(StealingService stealingService) {
        this.stealingService = stealingService;
    }

    /**
     * 将通道注册到当前的Selector中
     */
    public void register(IoTask task) {
        if ((task.ops & ~VALID_OPS) != 0) {
            throw new UnsupportedOperationException("");
        }
        registerTaskQueue.offer(task);
        selector.wakeup();
    }

    /**
     * 取消注册
     * @param channel 通道
     */
    public void unregister(SocketChannel channel) {
        SelectionKey selectionKey = channel.keyFor(selector);
        if (selectionKey != null && selectionKey.attachment() != null) {
            //如果该通道已在selector上进行注册，则关闭前可使用Attach简单判断是否已处于队列中（即selectionKey.attachment() == null）
            //因为取消注册操作是作为一个任务添加进队列，而不是立即执行的，所以防止多次添加取消注册任务
            selectionKey.attach(null);
            if (Thread.currentThread() == this) {
                //如果当前线程就是持有selector的线程，说明该线程没被阻塞，直接取消
                selectionKey.cancel();
            } else {
                synchronized (unregisterLocker) {
                    unregisterLocker.set(true);
                    selector.wakeup();
                    selectionKey.cancel();
                    unregisterLocker.set(false);
                }
            }
        }
    }

    /**
     * 注册待注册队列中的所有任务
     * @param registerTaskQueue 待注册的队列
     */
    private void consumeRegisterTodoTasks(final ConcurrentLinkedQueue<IoTask> registerTaskQueue) {
        final Selector selector = this.selector;
        IoTask registerTask = registerTaskQueue.poll();
        while (registerTask != null) {
            try {
                final SocketChannel channel = registerTask.channel;
                int ops = registerTask.ops;
                SelectionKey key = channel.keyFor(selector);

                //注册监听事件
                if (key == null){
                    //该通道首次注册事件
                    key = channel.register(selector,ops,new KeyAttachment());
                }else {
                    key.interestOps(key.interestOps() | ops);
                }

                //添加附加值
                Object attachment = key.attachment();
                if (attachment instanceof KeyAttachment){
                    ((KeyAttachment) attachment).attach(ops,registerTask);
                }else {
                    //类型有误，直接取消
                    key.cancel();
                }
            } catch (ClosedChannelException | CancelledKeyException | ClosedSelectorException e) {
                registerTask.fireThrowable(e);
            } finally {
                registerTask = readyTaskQueue.poll();
            }
        }
    }

    /**
     * 将单次就绪的任务缓存加入到总队列中
     * @param readyTaskQueue     总任务队列
     * @param onceReadyTaskCache 单次待执行的任务
     */
    private void joinTaskQueue(final Queue<IoTask> readyTaskQueue, final List<IoTask> onceReadyTaskCache) {
        readyTaskQueue.addAll(onceReadyTaskCache);
    }

    /**
     * 消费自己待完成的任务，消费完后窃取别人的任务接着消费
     * @param readyTaskQueue 就绪任务队列
     * @param registerTaskQueue 待注册任务队列
     */
    private void consumeTodoTasks(final Queue<IoTask> readyTaskQueue, ConcurrentLinkedQueue<IoTask> registerTaskQueue) {
        final AtomicLong saturatingCapacity = this.saturatingCapacity;
        //从就绪任务队列中取一个任务
        IoTask task = readyTaskQueue.poll();
        while (task != null) {
            //增加饱和度
            saturatingCapacity.incrementAndGet();
            //做任务
            if (processTask(task)) {
                registerTaskQueue.offer(task);
            }
            //下个任务
            task = readyTaskQueue.poll();
        }

        //窃取其他的任务
        final StealingService stealingService = this.stealingService;
        if (stealingService != null) {
            //窃取一个任务
            task = stealingService.steal(readyTaskQueue);
            while (task != null) {
                //增加饱和度
                saturatingCapacity.incrementAndGet();
                //做任务
                if (processTask(task)) {
                    registerTaskQueue.offer(task);
                }
                //窃取下个任务
                task = stealingService.steal(readyTaskQueue);
            }
        }
    }

    /**
     *
     */
    @Override
    public void run() {
        super.run();

        final Selector selector = this.selector;
        final ArrayBlockingQueue<IoTask> readyTaskQueue = this.readyTaskQueue;
        final ConcurrentLinkedQueue<IoTask> registerTaskQueue = this.registerTaskQueue;
        //单次就绪读任务缓存，随后一次性添加到就绪队列
        //因为就绪队列是对外暴露的，通过缓存添加的方式，能减少就绪队列的改变次数
        final List<IoTask> onceReadyReadTaskCache = new ArrayList<>(MAX_ONCE_READ_TASK);
        final List<IoTask> onceReadyWriteTaskCache = new ArrayList<>(MAX_ONCE_WRITE_TASK);

        try {
            while (running) {
                //注册待注册队列中的所有任务
                consumeRegisterTodoTasks(registerTaskQueue);

                int count = selector.select();

                while(unregisterLocker.get()){
                    Thread.yield();
                }

                if (count == 0){
                    continue;
                }

                //处理已就绪的通道
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                int onceReadTaskCount = MAX_ONCE_READ_TASK;
                int onceWriteTaskCount = MAX_ONCE_WRITE_TASK;

                //迭代已就绪的任务
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    Object attachment = selectionKey.attachment();
                    //检查有效性
                    if (selectionKey.isValid() && attachment instanceof KeyAttachment) {
                        final KeyAttachment attach = (KeyAttachment) attachment;
                        try {
                            final int readyOps = selectionKey.readyOps();
                            int interestOps = selectionKey.interestOps();

                            //是否可读
                            if ((readyOps & SelectionKey.OP_READ) != 0 && (onceReadTaskCount--) > 0) {
                                onceReadyReadTaskCache.add(attach.taskForReadable);
                                interestOps = interestOps & ~SelectionKey.OP_READ;
                            }
                            //是否可写
                            if ((readyOps & SelectionKey.OP_WRITE) != 0 && (onceWriteTaskCount--) > 0) {
                                onceReadyWriteTaskCache.add(attach.taskForWritable);
                                interestOps = interestOps & ~SelectionKey.OP_WRITE;
                            }
                            //取消已就绪的关注
                            selectionKey.interestOps(interestOps);
                        } catch (CancelledKeyException ignored) {
                            //当前链接被取消、断开时直接移除相关任务
                            //当前链接被取消、断开时直接移除相关任务
                            if (attach.taskForReadable != null) {
                                onceReadyReadTaskCache.remove(attach.taskForReadable);
                            }
                            if (attach.taskForWritable != null) {
                                onceReadyWriteTaskCache.remove(attach.taskForWritable);
                            }
                        }
                    }
                    iterator.remove();
                }

                //判断本次是否有待执行的读任务
                if (!onceReadyReadTaskCache.isEmpty()) {
                    joinTaskQueue(readyTaskQueue, onceReadyReadTaskCache);
                    onceReadyReadTaskCache.clear();
                }
                //判断本次是否有待执行的写任务
                if (!onceReadyWriteTaskCache.isEmpty()) {
                    joinTaskQueue(readyTaskQueue, onceReadyWriteTaskCache);
                    onceReadyWriteTaskCache.clear();
                }

                //消费总队列中的任务
                consumeTodoTasks(readyTaskQueue, registerTaskQueue);
            }
        }catch (ClosedSelectorException ignored){
            //可能是exit方法导致关闭，不做任何操作
        }
        catch (IOException e) {
            CloseUtils.close(selector);
        } finally {
            readyTaskQueue.clear();
            registerTaskQueue.clear();
        }
    }

    /**
     * 线程退出操作
     */
    public void exit() {
        running = false;
        CloseUtils.close(selector);
        interrupt();
    }

    /**
     * 调用子类执行任务操作
     * @param task 任务
     * @return 执行任务后是否需要继续关注该任务
     */
    protected abstract boolean processTask(IoTask task);

    /**
     * 用以注册时添加附件
     */
    static class KeyAttachment {
        /**
         * 可读时执行的任务
         */
        IoTask taskForReadable;
        /**
         * 可写时执行的任务
         */
        IoTask taskForWritable;

        /**
         * 附加任务
         * @param ops  任务关注的事件类型
         * @param task 任务
         */
        void attach(int ops, IoTask task) {
            if (ops == SelectionKey.OP_READ) {
                taskForReadable = task;
            } else {
                taskForWritable = task;
            }
        }
    }
}
