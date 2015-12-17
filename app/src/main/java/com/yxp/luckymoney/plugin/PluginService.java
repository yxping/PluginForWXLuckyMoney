package com.yxp.luckymoney.plugin;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 抢红包主要逻辑:
 * 需要注意:保持屏幕常亮,需要把每个群的消息免打扰取消,主要为了能够接收到通知Notification,在一开始请回到Home
 * <p/>
 * 1.接收到Notification之后,存入List中作为待处理红包.
 * 2.逐个处理List中的PendingIntent,开启微信的聊天界面,获取可以点击的红包列表.逐个点击
 * 3.进入红包处理,两种情况,
 * 一,进入接收红包的界面,获取抢红包的按钮,触发点击.进入红包detail界面.结束
 * 二,已经抢过,进入红包detail界面,或者被抢完了,停留在receive界面.结束.
 * 回到主界面
 * 4.回到步骤1
 * <p/>
 * Created by yanxing on 15/12/12.
 */
public class PluginService extends AccessibilityService {

    // 待处理的红包信息
    private volatile List<PendingIntent> untreatedLuckyMoneyList = new ArrayList<PendingIntent>();

    // 在聊天界面上有领取红包样式的红包列表
    private List<AccessibilityNodeInfo> canOpenNode;
    // 被删除的已经打开过的红包列表
    private List<AccessibilityNodeInfo> trashOpenNode;

    // 记录当前所在的页面
    private Status curStatus = Status.OUT_WE_CHAT;

    public enum Status {
        OUT_WE_CHAT, ON_WE_CHAT_HOME, ON_CHAT_ROOM, ON_LUCKY_MONEY_RECEIVED, ON_LUCKY_MONEY_DETAIL
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        switch (eventType) {
            // 监听通知栏消息
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                handlerNotification(event);
                break;
            // 监听窗口变化消息
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                handlerWindowStateChange(event);
                break;
            // 监听窗口内容的动态变化
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                handlerWindowContentChange(event);
                break;
            default:
                break;
        }
    }

    /**
     * 处理Notification的逻辑
     *
     * @param event
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void handlerNotification(AccessibilityEvent event) {
        if (event == null) return;
        List<CharSequence> contents = event.getText();
        if (contents != null && !contents.isEmpty()) {
            for (CharSequence content : contents) {
                String text = content.toString();
                if (!text.contains("[微信红包]")) continue;
                if (event.getParcelableData() != null &&
                        event.getParcelableData() instanceof Notification) {
                    Notification notification = (Notification) event.getParcelableData();
                    PendingIntent pendingIntent = notification.contentIntent;
                    Log.e("pendingintent", pendingIntent.getCreatorPackage().toString());
                    if (pendingIntent.getCreatorPackage().toString().equals("com.tencent.mm")) {
                        if ((curStatus != Status.ON_CHAT_ROOM || curStatus != Status.ON_LUCKY_MONEY_RECEIVED) &&
                                untreatedLuckyMoneyList.size() == 0) {
                            openNotification(pendingIntent);
                        } else {
                            untreatedLuckyMoneyList.add(0, pendingIntent);
                        }
                    }
                }
            }
        }
    }

    /**
     * 开启Notification中所指向的微信聊天页面
     *
     * @param pendingIntent
     */
    public void openNotification(PendingIntent pendingIntent) {
        if (pendingIntent == null) return;
        // 模拟点击了notification
        try {
            pendingIntent.send();
            curStatus = Status.ON_CHAT_ROOM;
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理未处理的红包列表Notification列表上的
     */
    public void disposeLuckyMoneyList() {
        if (untreatedLuckyMoneyList.size() != 0) {
            PendingIntent pendingIntent = untreatedLuckyMoneyList.remove(0);
            openNotification(pendingIntent);
        }
    }

    /**
     * 当窗口发生变化时触发的方法,也就是activity
     *
     * @param event
     */
    public void handlerWindowStateChange(AccessibilityEvent event) {
        if (event == null || event.getSource() == null) return;
        String className = event.getClassName().toString();

        // 微信主界面 com.tencent.mm.ui.LauncherUI
        if (className.equals("com.tencent.mm.ui.LauncherUI")) {
            curStatus = Status.ON_CHAT_ROOM;
            if (canOpenNode == null) {
                canOpenNode = event.getSource().findAccessibilityNodeInfosByText("领取红包");
                trashOpenNode = new ArrayList<AccessibilityNodeInfo>();
            }
            openLuckyMoney();
            // 红包接收界面 com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI
        } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI")) {
            curStatus = Status.ON_LUCKY_MONEY_RECEIVED;
            unpackLuckyMoney(event.getSource());
            // 红包的detail界面 com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI
        } else if (className.equals("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI")) {
            curStatus = Status.ON_LUCKY_MONEY_DETAIL;
            back();
        }
    }

    /**
     * 在红包接收界面拆开红包
     *
     * @param nodeInfo
     */
    public void unpackLuckyMoney(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo == null) {
            back();
            return;
        }
        // 打开红包，如果红包已被抢完，遍历节点, 如果匹配“红包详情”、“手慢了”和“过期”,则返回
        // 继续打开其他红包
        List<AccessibilityNodeInfo> packedLists = new ArrayList<>();
        packedLists.addAll(nodeInfo.findAccessibilityNodeInfosByText("红包详情"));
        packedLists.addAll(nodeInfo.findAccessibilityNodeInfosByText("手慢了"));
        packedLists.addAll(nodeInfo.findAccessibilityNodeInfosByText("过期"));

        if (!packedLists.isEmpty()) {
            back();
            return;
        }

        List<AccessibilityNodeInfo> unPackedLists = nodeInfo.findAccessibilityNodeInfosByText("拆红包");
        if (unPackedLists.isEmpty()) {
            back();
        } else {
            AccessibilityNodeInfo node = unPackedLists.get(0);
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }

    /**
     * 在聊天界面中打开红包
     */
    public void openLuckyMoney() {
        if (canOpenNode != null && canOpenNode.size() == 0) {
            backToHome();
            disposeLuckyMoneyList();
            canOpenNode = null;
            trashOpenNode = null;
        }

        if (canOpenNode == null) {
            backToHome();
            return;
        }

        AccessibilityNodeInfo node = canOpenNode.remove(0);
        trashOpenNode.add(node);
        if (node.getParent() != null && node.getParent().isClickable()) {
            node.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }

    }

    /**
     * 当在聊天页面有人继续发了红包,通过一下逻辑添加到准备要打开的红包列表中
     * @param event
     */
    public void handlerWindowContentChange(AccessibilityEvent event) {
        if (event == null || event.getSource() == null) return;
        if (curStatus == Status.ON_CHAT_ROOM) {
            List<AccessibilityNodeInfo> newList = event.getSource().findAccessibilityNodeInfosByText("领取红包");
            if (newList != null && canOpenNode != null && !canOpenNode.isEmpty()) {
                for (AccessibilityNodeInfo nodeInfo : newList) {
                    boolean isInCanOpenNode = false;
                    for (AccessibilityNodeInfo coNode : canOpenNode) {
                        if (nodeInfo.equals(coNode)) {
                            isInCanOpenNode = true;
                            break;
                        }
                    }
                    if (isInCanOpenNode) continue;
                    boolean isInTrash = false;
                    for (AccessibilityNodeInfo coNode : trashOpenNode) {
                        if (nodeInfo.equals(coNode)) {
                            isInTrash = true;
                            break;
                        }
                    }
                    if (isInTrash) continue;
                    canOpenNode.add(nodeInfo);
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void back() {
        this.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void backToHome() {
        curStatus = Status.OUT_WE_CHAT;
        this.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    @Override
    public void onInterrupt() {

    }
}
