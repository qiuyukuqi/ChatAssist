package com.lisa.chatassist.ui.overlay

import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.lisa.chatassist.data.ChatMessage
import com.lisa.chatassist.data.ReplySuggestion

/**
 * OverlayController
 * 控制悬浮窗的显示，负责与ChatOverlayService通信
 */
object OverlayController {

    private const val ACTION_SHOW_SUGGESTIONS = "com.lisa.chatassist.SHOW_SUGGESTIONS"
    private const val ACTION_UPDATE_CONVERSATION = "com.lisa.chatassist.UPDATE_CONVERSATION"
    private const val ACTION_HIDE = "com.lisa.chatassist.HIDE"
    private const val ACTION_CLICK_SUGGESTION = "com.lisa.chatassist.CLICK_SUGGESTION"
    
    private const val EXTRA_CONVERSATION_NAME = "conversation_name"
    private const val EXTRA_SUGGESTIONS = "suggestions"
    private const val EXTRA_MESSAGE = "message"
    private const val EXTRA_SUGGESTION_INDEX = "suggestion_index"

    // Listener for suggestion clicks
    var onSuggestionClickListener: ((String) -> Unit)? = null

    /**
     * 显示回复建议
     */
    fun showSuggestions(conversationName: String, suggestions: List<ReplySuggestion>) {
        // 通过EventBus或LocalBroadcast发送事件
        // 这里简化为静态引用
        ChatOverlayService.currentSuggestions = suggestions
        ChatOverlayService.currentConversation = conversationName
        ChatOverlayService.shouldShow = true
        
        // 通知Service更新UI
        notifyOverlayUpdate()
    }

    /**
     * 更新当前会话信息
     */
    fun updateConversation(conversationName: String, latestMessage: ChatMessage?) {
        ChatOverlayService.currentConversation = conversationName
        ChatOverlayService.lastMessage = latestMessage
    }

    /**
     * 隐藏悬浮窗
     */
    fun hide() {
        ChatOverlayService.shouldShow = false
        notifyHide()
    }

    /**
     * 处理点击建议
     */
    fun onSuggestionClicked(index: Int) {
        val suggestions = ChatOverlayService.currentSuggestions
        if (index in suggestions.indices) {
            val suggestion = suggestions[index]
            onSuggestionClickListener?.invoke(suggestion.text)
            // 自动复制到剪贴板
            copyToClipboard(suggestion.text)
        }
    }

    /**
     * 复制文本到剪贴板
     */
    private fun copyToClipboard(text: String) {
        // 实现会在Service中处理
    }

    private fun notifyOverlayUpdate() {
        // 发送广播更新悬浮窗
        val intent = Intent(ACTION_SHOW_SUGGESTIONS).apply {
            setPackage("com.lisa.chatassist")
        }
        // Context将通过Service传递
    }

    private fun notifyHide() {
        val intent = Intent(ACTION_HIDE).apply {
            setPackage("com.lisa.chatassist")
        }
    }

    /**
     * 创建显示Intent
     */
    fun createShowIntent(context: Context): Intent {
        return Intent(context, ChatOverlayService::class.java).apply {
            action = ACTION_SHOW_SUGGESTIONS
        }
    }
}

/**
 * 简化版的ClipboardManager兼容类
 */
object ClipboardManagerCompat {
    fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("reply", text)
        clipboard.setPrimaryClip(clip)
    }
}
