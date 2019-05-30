package com.study.handle;

/**
 * 新消息处理责任链，泛型表示当前需要消费数据类型
 * @author Cx
 * @version jdk8 and idea On 2019/5/12 10:41
 */
public abstract class ConnectorHandlerChain<Model> {
    private volatile ConnectorHandlerChain<Model> next;

    /**
     * 添加节点到末尾
     * @param newChain 需要添加的节点
     * @return 添加的节点
     */
    public ConnectorHandlerChain<Model> appendLast(ConnectorHandlerChain<Model> newChain){
        if (newChain == this || this.getClass().equals(newChain.getClass())){
            //如果当前节点与新增节点是同一个类，则不继续添加，防止重复造成死循环
            return this;
        }
        synchronized (this){
            if (next == null){
                next = newChain;
                return newChain;
            }
            return next.appendLast(newChain);
        }
    }

    /**
     * 移除类型为clx的节点
     * @param clx 需要移除节点的类
     * @return 是否移除成功
     */
    public synchronized boolean remove(Class<? extends ConnectorHandlerChain<Model>>clx){
        if (this.getClass().equals(clx)){
            //自己不能移除自己
            return false;
        }
        synchronized (this){
            if (next == null){
                return false;
            }
            else if (next.getClass().equals(clx)){
                next = next.next;
                return true;
            }else {
                return next.remove(clx);
            }
        }
    }


    /**
     * 消费模板
     */
    synchronized boolean handle(ConnectorHandler handler, Model model){
        ConnectorHandlerChain<Model> next = this.next;
        if (consume(handler,model)){
            return true;
        }
        boolean consumed = next!=null && next.handle(handler,model);
        if (consumed){
            return true;
        }
        return consumeAgain(handler, model);
    }

    /**
     * 具体消费方法
     */
    protected abstract boolean consume(ConnectorHandler handler, Model model);

    /**
     * 再次重新消费
     * 解决场景：有一个节点消费的条件为：当前链的所有节点都无法消费，才让他消费
     */
    protected boolean consumeAgain(ConnectorHandler handler, Model model){
        return false;
    }
}
