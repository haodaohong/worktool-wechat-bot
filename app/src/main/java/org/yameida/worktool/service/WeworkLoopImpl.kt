package org.yameida.worktool.service

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.text.isDigitsOnly
import com.blankj.utilcode.util.LogUtils
import org.yameida.worktool.Demo
import org.yameida.worktool.model.WeworkMessageBean
import org.yameida.worktool.service.WeworkController.mainLoopRunning
import org.yameida.worktool.utils.*
import java.lang.Exception
import java.lang.StringBuilder

/**
 * 获取数据类型 201 202 主循环
 */
object WeworkLoopImpl {

    var logIndex = 0

//    @Synchronized
//    fun mainLoop() {
//        mainLoopRunning = true
//        if (!threadStart) {
//            threadStart = true
//            thread {
//                while (true) {
//                    try {
//                        if (mainLoopRunning) {
//                            goHomeTab("消息")
//                            //todo 下上滚动
//                            if (mainLoopRunning && getChatroomList()) {
//                                if (mainLoopRunning)
//                                    getChatMessageList()
//                                mainLoopRunning = false
//                            }
//                            if (mainLoopRunning) {
//                                getFriendRequest()
//                            }
//                        }
//                        sleep(1000)
//                    } catch (e: Exception) {
//                        LogUtils.e(e)
//                        error(e.printStackTrace())
//                        mainLoopRunning = false
//                    }
//                }
//            }
//        }
//    }

    fun mainLoop() {
        mainLoopRunning = true
        try {
            while (mainLoopRunning) {
                goHomeTab("消息")
                //todo 下上滚动
                if (getChatroomList() && getChatMessageList()) {
                    mainLoopRunning = false
                    break
                }
                if (getFriendRequest()) {
                    mainLoopRunning = false
                    break
                }
                sleep(1000)
            }
        } catch (e: Exception) {
            mainLoopRunning = false
            error("ERROR mainLoop: " + e.message)
        }
    }

    /**
     * 读取通讯录好友请求
     */
    fun getFriendRequest(): Boolean {
        val list = getRoot().findAccessibilityNodeInfosByText("通讯录")
        for (item in list) {
            if (item.parent.parent.parent.childCount == 5) {
                if (item.parent.childCount > 1) {
                    LogUtils.d("通讯录有红点")
                    AccessibilityUtil.performClick(item)
                    sleep(500)
                    val addButton = AccessibilityUtil.findOneByText(getRoot(), "添加客户")
                    val backNode = AccessibilityUtil.findBackNode(addButton)
                    LogUtils.d(backNode?.className)
                    if (backNode?.className == Views.TextView) {
                        LogUtils.d("有待添加客户")
                        AccessibilityUtil.performClick(backNode)
                        sleep(2000)
                        AccessibilityUtil.findTextAndClick(getRoot(), "新的客户")
                        sleep(500)
                        var retry = 5
                        while (retry-- > 0) {
                            if (!AccessibilityUtil.findTextAndClick(getRoot(), "查看"))
                                break
                            sleep(2000)
                            val nameList = passFriendRequest()
                            if (nameList.isEmpty())
                                break
                            //TODO nameList 通过的好友加入演示脚本
                            Demo.test2(nameList[0])
                        }
                        return true
                    } else {
                        LogUtils.d("未发现待添加客户")
                    }
                } else {
                    LogUtils.v("通讯录无红点")
                }
            }
        }
        return false
    }

    /**
     * 查看好友请求并通过
     */
    private fun passFriendRequest(): List<String> {
        val nameList = arrayListOf<String>()
        val imageView = AccessibilityUtil.findOneByClazz(getRoot(), Views.ImageView)
        if (imageView != null) {
            val textViewList = AccessibilityUtil.findAllByClazz(imageView.parent, Views.TextView)
            val filter = textViewList.filter { it.text != null && it.text.toString() != "微信" }
            if (filter.isNotEmpty()) {
                val tvNick = filter[0]
                LogUtils.d("好友请求: " + tvNick.text)
                AccessibilityUtil.findTextAndClick(getRoot(), "通过验证")
                sleep(1000)
                AccessibilityUtil.findTextAndClick(getRoot(), "完成")
                sleep(5000)
                if (AccessibilityUtil.findTextAndClick(getRoot(), "确定")) {
                    sleep(500)
                    LogUtils.d("添加好友失败")
                } else {
                    val weworkMessageBean = WeworkMessageBean()
                    weworkMessageBean.type = WeworkMessageBean.GET_FRIEND_INFO
                    weworkMessageBean.nameList = arrayListOf(tvNick.text.toString())
                    WeworkController.weworkService.webSocketManager.send(weworkMessageBean)
                    nameList.add(tvNick.text.toString())
                }
                //回到上一页
                var retry = 5
                while (retry-- > 0 && !isAtHome()) {
                    val textView = AccessibilityUtil.findOneByText(getRoot(), "新的客户")
                    if (textView == null) {
                        backPress()
                    }
                }
            }
        }
        return nameList
    }

    /**
     * 读取聊天列表
     */
    private fun getChatroomList(): Boolean {
        if (logIndex++ % 15 == 0) {
            LogUtils.i("读取首页聊天列表")
            log("读取首页聊天列表")
        }
        val listview = AccessibilityUtil.findOneByClazz(getRoot(), Views.ListView)
        if (listview != null) {
            if (listview.childCount >= 2) {
                if (checkUnreadChatRoom(listview)) {
                    //进入聊天页
                    return true
                }
            } else {
                LogUtils.e("读取聊天列表失败")
                error("读取聊天列表失败")
            }
        } else {
            LogUtils.e("读取聊天列表失败")
            error("读取聊天列表失败")
        }
        return false
    }

    /**
     * 检查首页-聊天列表是否有未读红点并点击进入
     * 获取红点
     */
    private fun checkUnreadChatRoom(list: AccessibilityNodeInfo): Boolean {
        val spotNodeList = arrayListOf<AccessibilityNodeInfo>()
        for (i in 0 until list.childCount) {
            val item = list.getChild(i)
            if (item != null && Views.RelativeLayout.equals(item.className)) {
                if (item.childCount >= 2) {
                    val spotNode = item.getChild(1)
                    if (spotNode != null
                        && Views.TextView.equals(spotNode.className)
                        && spotNode.text != null
                        && spotNode.text.toString().replace("+", "").isDigitsOnly()
                    ) {
                        spotNodeList.add(spotNode)
                    }
                }
            }
        }
        if (spotNodeList.size > 0) {
            LogUtils.i("发现未读消息: " + spotNodeList.size + "条")
            log("发现未读消息: " + spotNodeList.size + "条")
            for (spotNode in spotNodeList) {
                if (AccessibilityUtil.performClick(spotNode)) {
                    //进入聊天页 下一步 getChatMessageList
                    break
                }
            }
            sleep(1000)
            return true
        } else {
            return false
        }
    }

    /**
     * 聊天页
     * 1.获取群名
     * 2.获取消息列表
     */
    private fun getChatMessageList(): Boolean {
        AccessibilityUtil.performScrollDown(getRoot(), 0)
        val roomType = WeworkRoomUtil.getRoomType(getRoot())
        var titleList = WeworkRoomUtil.getRoomTitle(getRoot())
        if (titleList.contains("对方正在输入…")) {
            titleList = WeworkRoomUtil.getFriendName()
        }
        if (titleList.size > 0) {
            val title = titleList.joinToString()
            LogUtils.i("聊天: $title")
            log("聊天: $title")
            val list = AccessibilityUtil.findOneByClazz(getRoot(), Views.ListView)
            if (list != null) {
                LogUtils.d("消息条数: " + list.childCount)
                val messageList = arrayListOf<WeworkMessageBean.SubMessageBean>()
                for (i in 0 until list.childCount) {
                    val item = list.getChild(i)
                    if (item != null && item.childCount > 0) {
                        messageList.add(parseChatMessageItem(item, roomType))
                    }
                }
                WeworkController.weworkService.webSocketManager.send(
                    WeworkMessageBean(
                        null, null,
                        WeworkMessageBean.TYPE_RECEIVE_MESSAGE_LIST,
                        roomType,
                        titleList,
                        messageList,
                        null
                    )
                )
                return true
            } else {
                LogUtils.e("未找到聊天消息列表")
                error("未找到聊天消息列表")
            }
        }
        return false
    }

    /**
     * 解析消息列表里的一条消息
     */
    private fun parseChatMessageItem(
        node: AccessibilityNodeInfo,
        roomType: Int
    ): WeworkMessageBean.SubMessageBean {
        val message: WeworkMessageBean.SubMessageBean
        val nameList = arrayListOf<String>()
        val itemMessageList = arrayListOf<WeworkMessageBean.ItemMessageBean>()
        LogUtils.d("开始解析一条消息...")
        //消息头(在消息主体上方 如时间信息)
        val linearLayoutItem = AccessibilityUtil.findOneByClazz(node, Views.LinearLayout, 1)
        if (linearLayoutItem != null) {
            val sb = StringBuilder("消息头: ")
            val tvList = AccessibilityUtil.findAllByClazz(linearLayoutItem, Views.TextView)
            for (item in tvList.filter { it.text != null && !it.text.isNullOrBlank() }) {
                val text = item.text.toString()
                val itemMessage = WeworkMessageBean.ItemMessageBean(0, text)
                sb.append(text).append("\t")
                itemMessageList.add(itemMessage)
            }
            LogUtils.d(sb.toString())
        }
        //消息主体
        val relativeLayoutItem = AccessibilityUtil.findOneByClazz(node, Views.RelativeLayout, 1)
        if (relativeLayoutItem != null && relativeLayoutItem.childCount >= 2) {
            if (Views.ImageView.equals(relativeLayoutItem.getChild(0).className)) {
                LogUtils.v("头像在左边 本条消息发送者为其他联系人")
                nameList.addAll(WeworkTextUtil.getNameList(node))
                var textType = WeworkMessageBean.TEXT_TYPE_UNKNOWN
                val relativeLayoutContent =
                    AccessibilityUtil.findOneByClazz(relativeLayoutItem, Views.RelativeLayout, 2)
                if (relativeLayoutContent != null) {
//                    AccessibilityUtil.printNodeClazzTree(relativeLayoutContent)
                    textType = WeworkTextUtil.getTextType(relativeLayoutContent)
                    LogUtils.v("textType: $textType")
                    val tvList =
                        AccessibilityUtil.findAllByClazz(relativeLayoutContent, Views.TextView)
                    for (item in tvList.filter { it.text != null && !it.text.isNullOrBlank() }) {
                        val text = item.text.toString()
                        LogUtils.d(text)
                        val itemMessage = WeworkMessageBean.ItemMessageBean(2, text)
                        itemMessageList.add(itemMessage)
                    }
                }
                message = WeworkMessageBean.SubMessageBean(0, textType, itemMessageList, nameList)
            } else if (Views.ImageView.equals(relativeLayoutItem.getChild(1).className)) {
                LogUtils.v("头像在右边 本条消息发送者为自己")
                val tvList = AccessibilityUtil.findAllByClazz(relativeLayoutItem, Views.TextView)
                for (item in tvList.filter { it.text != null && !it.text.isNullOrBlank() }) {
                    val text = item.text.toString()
                    LogUtils.d(text)
                    val itemMessage = WeworkMessageBean.ItemMessageBean(2, text)
                    itemMessageList.add(itemMessage)
                }
                message = WeworkMessageBean.SubMessageBean(1, 0, itemMessageList, nameList)
            } else {
                // 没有头像的消息（撤销消息、其他可能的系统消息）
                val tvList = AccessibilityUtil.findAllByClazz(node, Views.TextView)
                for (item in tvList.filter { it.text != null && !it.text.isNullOrBlank() }) {
                    val text = item.text.toString()
                    LogUtils.d(text)
                    val itemMessage = WeworkMessageBean.ItemMessageBean(1, text)
                    itemMessageList.add(itemMessage)
                }
                message = WeworkMessageBean.SubMessageBean(2, 0, itemMessageList, nameList)
                LogUtils.e("消息解析异常 未知异常")
            }
        } else {
            // 没有头像的消息（撤销消息、其他可能的系统消息）
            val sb = StringBuilder("未发现头像 本条消息发送者未知")
            val tvList = AccessibilityUtil.findAllByClazz(node, Views.TextView)
            for (item in tvList.filter { it.text != null && !it.text.isNullOrBlank() }) {
                val text = item.text.toString()
                sb.append(text).append("/t")
                val itemMessage = WeworkMessageBean.ItemMessageBean(0, text)
                itemMessageList.add(itemMessage)
            }
            LogUtils.d(sb.toString())
            message = WeworkMessageBean.SubMessageBean(2, 0, itemMessageList, nameList)
        }
        return message
    }

}