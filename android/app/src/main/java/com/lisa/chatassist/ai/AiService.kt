package com.lisa.chatassist.ai

import android.content.Context
import com.lisa.chatassist.data.AppPreferences
import com.lisa.chatassist.data.ReplySuggestion
import com.lisa.chatassist.data.ReplyStyle
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AI服务 - 调用大模型生成回复建议
 * 支持多种Provider: MiniMax, OpenAI, Claude等
 */
class AiService private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: AiService? = null
        
        fun getInstance(context: Context): AiService {
            return instance ?: synchronized(this) {
                instance ?: AiService(context.applicationContext).also { instance = it }
            }
        }
        
        // 请求超时
        private const val TIMEOUT_SECONDS = 30L
        
        // 系统提示词
        private const val SYSTEM_PROMPT = """你是一个智能聊天助手，名为Lisa。你的任务是根据聊天上下文，生成3个合适的回复建议。

要求：
1. 回复要自然、符合对话情境
2. 考虑不同的回复风格（正式、随意、幽默等）
3. 回复长度适中（5-30字）
4. 如果是多轮对话，注意上下文连贯性

请生成3个不同风格的回复建议，每个回复需要包含：
- text: 回复文本
- confidence: 置信度(0-1)
- style: 风格(normal/casual/polite/emoji/short/humor)

以JSON数组格式返回，例如：
[
  {"text": "好的，我知道了", "confidence": 0.9, "style": "normal"},
  {"text": "没问题~", "confidence": 0.8, "style": "casual"},
  {"text": "哈哈，这个有趣", "confidence": 0.7, "style": "humor"}
]"""
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var httpClient: OkHttpClient? = null

    init {
        initHttpClient()
    }

    private fun initHttpClient() {
        httpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 生成回复建议
     * @param context 聊天上下文
     * @param style 首选回复风格
     * @param maxTokens 最大token数
     * @param minConfidence 最低置信度
     * @param showReasoning 是否显示AI推理过程
     * @param callback 回调，传递建议列表
     */
    fun generateReply(
        context: String,
        style: ReplyStyle,
        maxTokens: Int,
        minConfidence: Float,
        showReasoning: Boolean,
        callback: (List<ReplySuggestion>) -> Unit
    ) {
        // 检查API配置
        val apiKey = AppPreferences.apiKey
        if (apiKey.isBlank()) {
            println("AiService: No API key configured")
            callback(emptyList())
            return
        }

        scope.launch {
            try {
                val result = when (AppPreferences.aiProvider) {
                    "minimax" -> callMiniMax(context, apiKey, maxTokens)
                    "openai" -> callOpenAI(context, apiKey, maxTokens)
                    "claude" -> callClaude(context, apiKey, maxTokens)
                    "custom" -> callCustomAPI(context, apiKey, maxTokens)
                    else -> {
                        println("AiService: Unknown provider ${AppPreferences.aiProvider}")
                        emptyList()
                    }
                }

                withContext(Dispatchers.Main) {
                    // 过滤低置信度结果
                    val filtered = result.filter { it.confidence >= minConfidence }
                    callback(filtered)
                }
            } catch (e: Exception) {
                println("AiService: Error generating reply: ${e.message}")
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }

    /**
     * 调用MiniMax API
     */
    private suspend fun callMiniMax(apiKey: String, context: String, maxTokens: Int): List<ReplySuggestion> {
        val endpoint = AppPreferences.apiEndpoint.ifBlank { "https://api.minimax.chat/v1" }
        
        val url = "$endpoint/text/chatcompletion_v2"
        
        val jsonBody = JSONObject().apply {
            put("model", "MiniMax-Text-01")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "聊天上下文：\n$context\n\n请生成回复建议：")
                })
            })
            put("max_tokens", maxTokens)
            put("temperature", 0.7)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequest(request)
    }

    /**
     * 调用OpenAI API
     */
    private suspend fun callOpenAI(apiKey: String, context: String, maxTokens: Int): List<ReplySuggestion> {
        val endpoint = AppPreferences.apiEndpoint.ifBlank { "https://api.openai.com/v1" }
        
        val url = "$endpoint/chat/completions"
        
        val jsonBody = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "聊天上下文：\n$context\n\n请生成回复建议：")
                })
            })
            put("max_tokens", maxTokens)
            put("temperature", 0.7)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequest(request)
    }

    /**
     * 调用Claude API
     */
    private suspend fun callClaude(apiKey: String, context: String, maxTokens: Int): List<ReplySuggestion> {
        val endpoint = AppPreferences.apiEndpoint.ifBlank { "https://api.anthropic.com/v1" }
        
        val url = "$endpoint/messages"
        
        val jsonBody = JSONObject().apply {
            put("model", "claude-sonnet-4-20250514")
            put("max_tokens", maxTokens)
            put("system", SYSTEM_PROMPT)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "聊天上下文：\n$context\n\n请生成回复建议：")
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeClaudeRequest(request)
    }

    /**
     * 调用自定义API (OpenAI兼容格式)
     */
    private suspend fun callCustomAPI(apiKey: String, context: String, maxTokens: Int): List<ReplySuggestion> {
        val endpoint = AppPreferences.apiEndpoint.ifBlank { 
            return emptyList() 
        }
        
        val url = "$endpoint/chat/completions"
        
        val jsonBody = JSONObject().apply {
            put("model", "custom")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "聊天上下文：\n$context\n\n请生成回复建议：")
                })
            })
            put("max_tokens", maxTokens)
            put("temperature", 0.7)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeRequest(request)
    }

    /**
     * 执行HTTP请求 (通用)
     */
    private suspend fun executeRequest(request: Request): List<ReplySuggestion> = suspendCancellableCoroutine { continuation ->
        httpClient?.newCall(request)?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("AiService: Request failed: ${e.message}")
                continuation.resume(emptyList()) {}
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()
                    if (body == null) {
                        continuation.resume(emptyList()) {}
                        return
                    }

                    val json = JSONObject(body)
                    
                    // 解析OpenAI/MiniMax格式的响应
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val message = choices.getJSONObject(0).optJSONObject("message")
                        val content = message?.optString("content") ?: ""
                        val suggestions = parseSuggestionsFromText(content)
                        continuation.resume(suggestions) {}
                    } else {
                        // 尝试解析error
                        val error = json.optString("error", "")
                        println("AiService: API error: $error")
                        continuation.resume(emptyList()) {}
                    }
                } catch (e: Exception) {
                    println("AiService: Parse error: ${e.message}")
                    continuation.resume(emptyList()) {}
                }
            }
        })
    }

    /**
     * 执行Claude API请求
     */
    private suspend fun executeClaudeRequest(request: Request): List<ReplySuggestion> = suspendCancellableCoroutine { continuation ->
        httpClient?.newCall(request)?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("AiService: Claude request failed: ${e.message}")
                continuation.resume(emptyList()) {}
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()
                    if (body == null) {
                        continuation.resume(emptyList()) {}
                        return
                    }

                    val json = JSONObject(body)
                    val content = json.optString("content", "")
                    
                    // Claude返回的是content数组
                    if (content.isNotBlank()) {
                        // 尝试直接解析JSON数组
                        try {
                            val suggestions = parseSuggestionsFromText(content)
                            continuation.resume(suggestions) {}
                            return
                        } catch (e: Exception) {
                            // Ignore parse error
                        }
                    }
                    
                    continuation.resume(emptyList()) {}
                } catch (e: Exception) {
                    println("AiService: Claude parse error: ${e.message}")
                    continuation.resume(emptyList()) {}
                }
            }
        })
    }

    /**
     * 从AI返回的文本中解析JSON建议列表
     */
    private fun parseSuggestionsFromText(text: String): List<ReplySuggestion> {
        // 尝试提取JSON数组
        val jsonStr = extractJsonArray(text)
        if (jsonStr.isBlank()) return emptyList()

        return try {
            val jsonArray = JSONArray(jsonStr)
            val suggestions = mutableListOf<ReplySuggestion>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val suggestion = ReplySuggestion(
                    id = "suggestion_$i",
                    text = obj.optString("text", ""),
                    confidence = obj.optDouble("confidence", 0.5).toFloat(),
                    style = parseStyle(obj.optString("style", "normal"))
                )
                if (suggestion.text.isNotBlank()) {
                    suggestions.add(suggestion)
                }
            }
            suggestions
        } catch (e: Exception) {
            println("AiService: Failed to parse suggestions: ${e.message}")
            emptyList()
        }
    }

    /**
     * 从文本中提取JSON数组
     */
    private fun extractJsonArray(text: String): String {
        // 尝试找到 ```json ... ``` 包裹的JSON
        val codeBlockRegex = Regex("```(?:json)?\\s*(\\[[\\s\\S]*?\\])\\s*```")
        val codeBlockMatch = codeBlockRegex.find(text)
        if (codeBlockMatch != null) {
            return codeBlockMatch.groupValues[1]
        }

        // 尝试直接找第一个 [ 和最后一个 ]
        val firstBracket = text.indexOf('[')
        val lastBracket = text.lastIndexOf(']')
        if (firstBracket >= 0 && lastBracket > firstBracket) {
            return text.substring(firstBracket, lastBracket + 1)
        }

        return text.trim()
    }

    /**
     * 解析回复风格
     */
    private fun parseStyle(styleStr: String): ReplyStyle {
        return when (styleStr.lowercase()) {
            "casual" -> ReplyStyle.CASUAL
            "polite" -> ReplyStyle.POLITE
            "emoji" -> ReplyStyle.EMOJI
            "short" -> ReplyStyle.SHORT
            "humor", "humour" -> ReplyStyle.HUMOR
            else -> ReplyStyle.NORMAL
        }
    }

    /**
     * 停止服务
     */
    fun shutdown() {
        scope.cancel()
    }
}
