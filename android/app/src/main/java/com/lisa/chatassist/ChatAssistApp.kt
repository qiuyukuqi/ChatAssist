package com.lisa.chatassist

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.lisa.chatassist.data.AppPreferences

/**
 * ChatAssist Application
 * AI聊天助手 - 任意App中智能辅助回复
 */
class ChatAssistApp : Application() {

    companion object {
        const val CHANNEL_ID = "chat_assist_service"
        const val CHANNEL_NAME = "Chat Assist Service"
        
        lateinit var instance: ChatAssistApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化偏好设置
        AppPreferences.init(this)
        
        // 创建通知渠道
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Chat Assist background service"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
