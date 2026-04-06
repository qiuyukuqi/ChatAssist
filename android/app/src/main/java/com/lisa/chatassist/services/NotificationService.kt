package com.lisa.chatassist.services

import android.annotation.SuppressLint
import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.lisa.chatassist.ChatAssistApp
import com.lisa.chatassist.ai.AiService
import com.lisa.chatassist.data.AppPreferences
import com.lisa.chatassist.data.ChatMessage
import com.lisa.chatassist.data.ChatSession
import com.lisa.chatassist.ui.overlay.OverlayController
import kotlinx.coroutines.*

/**
 * 通知监听服务
 * 核心功能：监听所有App的通知，提取聊天消息，触发AI生成回复建议
 */
class NotificationService : NotificationListenerService() {

    companion object {
        // 聊天App包名列表 (常见的即时通讯App)
        val CHAT_APP_PACKAGES = setOf(
            "com.tencent.mm",           // 微信
            "com.tencent.mobileqq",      // QQ
            "com.aliqq",                 // 阿里旺旺
            "com.alibaba.android.rimp",  // 钉钉
            "com.slack",                 // Slack
            "com.discord",               // Discord
            "org.telegram.messenger",    // Telegram
            "com.whatsapp",              // WhatsApp
            "com.facebook.orca",         // Messenger
            "com.snapchat.android",      // Snapchat
            "com.instagram.android",      // Instagram DM
            "com.twitter.android",       // Twitter DM
            "com.skype.rovers",          // Skype
            "com.microsoft.teams",        // Microsoft Teams
            "com.google.android.gm",      // Gmail (可能)
            "jp.naver.line.android",      // LINE
            "com.kakao.talk",            // KakaoTalk
            "com.viber.voip",            // Viber
        )
        
        const val TAG = "NotificationService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeSessions = mutableMapOf<String, ChatSession>()
    private var aiService: AiService? = null
    
    // 用于防抖：当短时间内收到多个通知时，等待一段时间再处理
    private var pendingProcessJob: Job? = null
    private val debounceDelay = 500L // ms

    override fun onCreate() {
        super.onCreate()
        aiService = AiService.getInstance(this)
        println("$TAG: Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        println("$TAG: Service destroyed")
    }

    /**
     * 收到通知时调用
     */
    @SuppressLint("WrongThread")
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        
        // 检查服务是否启用
        if (!AppPreferences.isEnabled) return
        
        val packageName = sbn.packageName
        
        // 检查是否应该监控这个App
        if (!AppPreferences.shouldMonitorApp(packageName)) return
        
        // 检查是否是聊天App
        if (!isChatApp(packageName)) return
        
        try {
            val notification = sbn.notification ?: return
            
            // 提取通知内容
            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT) ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT) ?: ""
            
            // 忽略空消息
            if (text.isNullOrBlank()) return
            
            // 生成会话ID (基于包名+会话标题)
            val conversationId = "${packageName}_${title}"
            
            // 判断是否是我发送的消息 (根据notification的发送来源)
            val isOutgoing = isOutgoingNotification(notification)
            
            // 创建消息对象
            val message = ChatMessage(
                id = ChatMessage.generateId(),
                sender = title.toString(),
                senderIsMe = isOutgoing,
                content = text.toString(),
                timestamp = sbn.postTime,
                appPackage = packageName,
                conversationId = conversationId,
                conversationName = title.toString()
            )
            
            // 添加到会话
            val session = activeSessions.getOrPut(conversationId) {
                ChatSession(
                    id = conversationId,
                    appPackage = packageName,
                    conversationId = conversationId,
                    conversationName = title.toString()
                )
            }
            session.addMessage(message)
            
            println("$TAG: Received message from ${message.conversationName}: ${message.content}")
            
            // 防抖处理：如果我发送了消息，触发AI生成回复
            if (isOutgoing) {
                triggerAiResponse(session, message)
            }
            
            // 更新悬浮窗显示最新消息
            updateOverlay(session)
            
        } catch (e: Exception) {
            println("$TAG: Error processing notification: ${e.message}")
        }
    }

    /**
     * 通知被移除时调用
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 可以在这里处理通知被清除的情况
    }

    /**
     * 判断是否是聊天App
     */
    private fun isChatApp(packageName: String): Boolean {
        return CHAT_APP_PACKAGES.any { 
            packageName.startsWith(it) || packageName == it 
        }
    }

    /**
     * 判断是否是发出的通知 (我发送的消息)
     */
    private fun isOutgoingNotification(notification: Notification): Boolean {
        // 检查notification的flags
        val flags = notification.flags
        val isOngoing = (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        val isLocal = (flags and Notification.FLAG_LOCAL_ONLY) != 0
        
        // 对于聊天App，通常发出的消息会有不同的extras
        // 这里可以通过额外检查来区分
        // 简化处理：如果消息以"我:"或"发送了"开头，可能是发出的
        return false // 需要根据实际情况调整
    }

    /**
     * 触发AI生成回复
     */
    private fun triggerAiResponse(session: ChatSession, latestMessage: ChatMessage) {
        // 防抖：等待debounceDelay毫秒，确保收集完整上下文
        pendingProcessJob?.cancel()
        pendingProcessJob = scope.launch {
            delay(debounceDelay)
            
            println("$TAG: Triggering AI response for conversation: ${session.conversationName}")
            
            // 获取上下文文本
            val contextText = session.getContextText(maxMessages = 10)
            
            // 调用AI服务生成回复
            aiService?.generateReply(
                context = contextText,
                style = AppPreferences.replyStyle,
                maxTokens = AppPreferences.maxTokens,
                minConfidence = AppPreferences.minConfidence,
                showReasoning = AppPreferences.showReasoning
            ) { suggestions ->
                if (suggestions.isNotEmpty()) {
                    // 发送广播更新UI
                    broadcastSuggestions(session, suggestions)
                    // 更新悬浮窗
                    showSuggestionsOnOverlay(session, suggestions)
                    
                    // 震动/声音反馈
                    if (AppPreferences.hapticFeedback) {
                        triggerHaptic()
                    }
                }
            }
        }
    }

    /**
     * 广播AI建议给UI
     */
    private fun broadcastSuggestions(session: ChatSession, suggestions: List<com.lisa.chatassist.data.ReplySuggestion>) {
        // 通过OverlayController直接更新
        OverlayController.showSuggestions(session.conversationName, suggestions)
    }

    /**
     * 更新悬浮窗
     */
    private fun updateOverlay(session: ChatSession) {
        OverlayController.updateConversation(session.conversationName, session.messages.lastOrNull())
    }

    /**
     * 在悬浮窗显示建议
     */
    private fun showSuggestionsOnOverlay(session: ChatSession, suggestions: List<com.lisa.chatassist.data.ReplySuggestion>) {
        OverlayController.showSuggestions(session.conversationName, suggestions)
    }

    /**
     * 震动反馈
     */
    private fun triggerHaptic() {
        try {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }

    /**
     * 检查是否已授权通知访问
     */
    fun isNotificationAccessGranted(): Boolean {
        val enabledListeners = android.provider.Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(packageName) == true
    }
}
