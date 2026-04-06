package com.lisa.chatassist.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 聊天消息
 */
@Parcelize
data class ChatMessage(
    val id: String,
    val sender: String,          // 发送者名称
    val senderIsMe: Boolean,     // 是否是我发送的
    val content: String,         // 消息内容
    val timestamp: Long,         // 时间戳
    val appPackage: String,      // 来源App
    val conversationId: String,  // 会话ID (用于区分不同聊天)
    val conversationName: String  // 会话名称 (群名/私聊对象)
) : Parcelable {

    companion object {
        fun generateId(): String = System.currentTimeMillis().toString() + "_" + (Math.random() * 1000).toInt()
    }
}

/**
 * AI生成的回复建议
 */
@Parcelize
data class ReplySuggestion(
    val id: String,
    val text: String,            // 建议的回复文本
    val confidence: Float,       // 置信度 0-1
    val style: ReplyStyle,       // 风格
    val reasoning: String? = null // AI思考过程 (可选)
) : Parcelable

/**
 * 回复风格枚举
 */
enum class ReplyStyle {
    NORMAL,         // 普通回复
    CASUAL,         // 轻松随意
    POLITE,         // 礼貌正式
    EMOJI,          // 带emoji
    SHORT,          // 简短
    HUMOR           // 幽默风趣
}

/**
 * 聊天会话 (一组消息的上下文)
 */
@Parcelize
data class ChatSession(
    val id: String,
    val appPackage: String,
    val conversationId: String,
    val conversationName: String,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    var lastActivity: Long = System.currentTimeMillis()
) : Parcelable {
    
    fun addMessage(message: ChatMessage) {
        messages.add(message)
        lastActivity = System.currentTimeMillis()
    }
    
    fun getContextText(maxMessages: Int = 10): String {
        // 提取最近N条消息作为上下文
        val recentMessages = messages.takeLast(maxMessages)
        return recentMessages.joinToString("\n") { msg ->
            val prefix = if (msg.senderIsMe) "我" else msg.sender
            "[$prefix]: ${msg.content}"
        }
    }
}
