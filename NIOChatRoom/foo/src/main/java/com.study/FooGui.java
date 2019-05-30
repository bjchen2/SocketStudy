package com.study;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class FooGui extends JFrame {
    private Timer timer;
    private JLabel label;

    public FooGui(String name, Callback callback) {
        super(name);
        // 窗口模式设置
        JFrame.setDefaultLookAndFeelDecorated(true);

        // 创建及设置窗口
        setLayout(new BorderLayout());
        setMaximumSize(new Dimension(280, 160));
        setMinimumSize(new Dimension(280, 160));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 界面上的文字
        label = new JLabel(name, SwingConstants.CENTER);
        add(label, BorderLayout.CENTER);

        // 循环刷新
        timer = new Timer(2000, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Object[] objects = callback.takeText();
                if (objects == null || objects.length == 0) {
                    return;
                }
                StringBuilder text = new StringBuilder("<html>");
                for (int i = 0; i < objects.length; i++) {
                    text.append(objects[i].toString());
                    if (i != objects.length - 1) {
                        text.append("<br/>");
                    }
                }

                text.append("</html>");
                update(text.toString());
            }
        });
    }

    /**
     * 显示界面
     */
    public void doShow() {
        SwingUtilities.invokeLater(() -> {
            // 显示窗口
            pack();
            setVisible(true);
            // 定时器启动
            timer.start();
        });
    }

    /**
     * 销毁界面
     */
    public void doDismiss() {
        SwingUtilities.invokeLater(() -> {
            // 关闭定时器
            timer.stop();
            // 销毁界面
            setVisible(false);
            dispose();
        });
    }

    public void update(final String text) {
        SwingUtilities.invokeLater(() -> label.setText(text));
    }

    /**
     * 获取刷新数据的回调
     */
    public interface Callback {
        Object[] takeText();
    }
}
