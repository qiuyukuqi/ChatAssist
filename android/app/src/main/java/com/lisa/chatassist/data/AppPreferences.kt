package com.lisa.chatassist.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用偏好设置
 */
object AppPreferences {
    
    private lateinit var prefs: SharedPreferences
    
    // Keys
    private const val KEY_ENABLED = "enabled"
    private const val KEY_AI_PROVIDER = "ai_provider"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_API_ENDPOINT = "api_endpoint"
    private const val KEY_REPLY_STYLE = "reply_style"
    private const val KEY_MAX_TOKENS = "max_tokens"
    private const val KEY_MIN_CONFIDENCE = "min_confidence"
    private const val KEY_AUTO_PASTE = "auto_paste"
    private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
    private const val KEY_SOUND_EFFECT = "sound_effect"
    private const val KEY_WHITE_LIST_APPS = "white_list_apps"
    private const val KEY_BLACK_LIST_APPS = "black_list_apps"
    private const val KEY_SHOW_REASONING = "show_reasoning"
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences("chat_assist_prefs", Context.MODE_PRIVATE)
    }
    
    // 服务开关
    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    
    // AI提供商: "minimax" / "openai" / "claude" / "custom"
    var aiProvider: String
        get() = prefs.getString(KEY_AI_PROVIDER, "minimax") ?: "minimax"
        set(value) = prefs.edit().putString(KEY_AI_PROVIDER, value).apply()
    
    // API密钥
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()
    
    // API端点 (自定义)
    var apiEndpoint: String
        get() = prefs.getString(KEY_API_ENDPOINT, "https://api.minimax.chat/v1") ?: ""
        set(value) = prefs.edit().putString(KEY_API_ENDPOINT, value).apply()
    
    // 默认回复风格
    var replyStyle: ReplyStyle
        get() {
            val styleStr = prefs.getString(KEY_REPLY_STYLE, "NORMAL") ?: "NORMAL"
            return try { ReplyStyle.valueOf(styleStr) } catch (e: Exception) { ReplyStyle.NORMAL }
        }
        set(value) = prefs.edit().putString(KEY_REPLY_STYLE, value.name).apply()
    
    // 最大生成Token数
    var maxTokens: Int
        get() = prefs.getInt(KEY_MAX_TOKENS, 200)
        set(value) = prefs.edit().putInt(KEY_MAX_TOKENS, value).apply()
    
    // 最低置信度阈值
    var minConfidence: Float
        get() = prefs.getFloat(KEY_MIN_CONFIDENCE, 0.3f)
        set(value) = prefs.edit().putFloat(KEY_MIN_CONFIDENCE, value).apply()
    
    // 自动粘贴到剪贴板
    var autoPaste: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PASTE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PASTE, value).apply()
    
    // 震动反馈
    var hapticFeedback: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, value).apply()
    
    // 提示音
    var soundEffect: Boolean
        get() = prefs.getBoolean(KEY_SOUND_EFFECT, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_EFFECT, value).apply()
    
    // 白名单App (包名列表,逗号分隔)
    var whiteListApps: Set<String>
        get() = prefs.getStringSet(KEY_WHITE_LIST_APPS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_WHITE_LIST_APPS, value).apply()
    
    // 黑名单App
    var blackListApps: Set<String>
        get() = prefs.getStringSet(KEY_BLACK_LIST_APPS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_BLACK_LIST_APPS, value).apply()
    
    // 显示AI推理过程
    var showReasoning: Boolean
        get() = prefs.getBoolean(KEY_SHOW_REASONING, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_REASONING, value).apply()
    
    // 是否监控某个App
    fun shouldMonitorApp(packageName: String): Boolean {
        // 如果有白名单，只监控白名单中的App
        if (whiteListApps.isNotEmpty()) {
            return whiteListApps.contains(packageName)
        }
        // 否则排除黑名单
        return !blackListApps.contains(packageName)
    }
    
    // 获取MiniMax API密钥 (如果使用MiniMax)
    fun getMiniMaxApiKey(): String? {
        return if (aiProvider == "minimax" && apiKey.isNotEmpty()) apiKey else null
    }
}
