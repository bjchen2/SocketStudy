package com.study.core.ds;

/**
 * 带优先级的节点, 可用于构成链表
 * @author cxd27419
 */
public class BytePriorityNode<Item> {
    /**
     * 优先级，越大越提前发
     */
    public byte priority;
    public Item item;
    public BytePriorityNode<Item> next;

    public BytePriorityNode(Item item) {
        this.item = item;
    }

    /**
     * 按优先级追加Node到当前链表中
     *
     * @param node 待插入的Node
     */
    public void appendWithPriority(BytePriorityNode<Item> node) {
        if (next == null) {
            next = node;
        } else {
            BytePriorityNode<Item> after = this.next;
            if (after.priority < node.priority) {
                // 中间位置插入
                this.next = node;
                node.next = after;
            } else {
                after.appendWithPriority(node);
            }
        }
    }
}
