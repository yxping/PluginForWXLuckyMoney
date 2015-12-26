# PluginForWXLuckyMoney
抢微信红包的工具，通过实例来进行学习和分享

## 原理
  原理主要是通过辅助功能AccessibilityService来完成的，AccessibilityService是Google用来帮助肢体不便的人所开发的一个功能，能够触发相应的用户事件比如点击，滑动等等。

  抢红包的功能也是基于此能力之上，去进行模拟点击事件。当然，AccessibilityService的功能需要得到用户的许可，通过它的功能的介绍，已经可以知道它的强大，其实这个的能力和root相当，甚至更强，毕竟可以模拟一切的用户事件。

  所以，在实现的方法前，需要了解<br>
  1. 如何获取特定能够点击的view的节点 <br>
  2. 触发点击事件，进行判断

## 实现逻辑
  需要注意：保持屏幕常亮，需要把每个群的消息免打扰取消，主要为了能够接收到通知Notification，在一开始请回到Home

1. 接收到Notification之后，存入List中作为待处理红包.

2. 逐个处理List中的PendingIntent，开启微信的聊天界面,获取可以点击的红包列表，逐个点击。

3. 进入红包处理,两种情况, 
一、进入接收红包的界面，获取抢红包的按钮，触发点击。进入红包detail界面，结束。 
二、已经抢过，进入红包detail界面,或者被抢完了，停留在receive界面，结束，回到主界面。

4. 回到步骤1

### 参考
[参考文档网址](http://blog.csdn.net/jiangwei0910410003/article/details/48895153) <br>
[参考工程](https://github.com/geeeeeeeeek/WeChatLuckyMoney?utm_source=tuicool&utm_medium=referral)
