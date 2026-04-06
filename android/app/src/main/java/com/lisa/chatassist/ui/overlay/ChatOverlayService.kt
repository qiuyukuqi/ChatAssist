package com.lisa.chatassist.ui.overlay

import android.annotation.SuppressLint
import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.lisa.chatassist.ChatAssistApp
import com.lisa.chatassist.MainActivity
import com.lisa.chatassist.R
import com.lisa.chatassist.data.AppPreferences
import com.lisa.chatassist.data.ReplySuggestion

/**
 * 悬浮窗服务
 * 在屏幕上层显示AI回复建议
 */
@SuppressLint("InflateParams", "ClickableViewAccessibility")
class ChatOverlayService : Service() {

    companion object {
        // 当前显示的建议
        var currentSuggestions: List<ReplySuggestion> = emptyList()
        var currentConversation: String = ""
        var lastMessage: com.lisa.chatassist.data.ChatMessage? = null
        var shouldShow: Boolean = false
        
        // 悬浮窗参数
        const val OVERLAY_WIDTH = 320 // dp
        const val OVERLAY_HEIGHT = 200 // dp
        const val OVERLAY_MARGIN = 16 // dp
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    
    // 拖拽相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMoving = false
    
    // 动画
    private var isExpanded = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        println("ChatOverlayService: Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_SUGGESTIONS -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            else -> {
                if (shouldShow && currentSuggestions.isNotEmpty()) {
                    showOverlay()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        println("ChatOverlayService: Destroyed")
    }

    /**
     * 显示悬浮窗
     */
    private fun showOverlay() {
        if (floatingView != null) {
            updateSuggestions()
            return
        }

        // 创建悬浮窗布局
        floatingView = createFloatingView()
        
        // 设置窗口参数
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            dpToPx(OVERLAY_WIDTH),
            dpToPx(OVERLAY_HEIGHT),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(OVERLAY_MARGIN)
            y = dpToPx(100)
        }

        try {
            windowManager?.addView(floatingView, params)
            println("ChatOverlayService: Overlay added")
        } catch (e: Exception) {
            println("ChatOverlayService: Failed to add overlay: ${e.message}")
        }
    }

    /**
     * 隐藏悬浮窗
     */
    private fun hideOverlay() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // View might already be removed
            }
            floatingView = null
        }
    }

    /**
     * 创建悬浮窗视图
     */
    private fun createFloatingView(): View {
        val density = resources.displayMetrics.density
        
        // 根布局
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.overlay_background)
            elevation = 8 * density
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        }

        // 标题栏 (可拖拽)
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(32)
            )
        }

        val titleText = TextView(this).apply {
            text = "Lisa 💬"
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(32), 1f)
        }
        titleBar.addView(titleText)

        // 最小化按钮
        val minimizeBtn = Button(this).apply {
            text = "−"
            textSize = 18f
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { toggleMinimize() }
        }
        titleBar.addView(minimizeBtn)

        // 关闭按钮
        val closeBtn = Button(this).apply {
            text = "×"
            textSize = 18f
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { 
                hideOverlay()
                shouldShow = false
            }
        }
        titleBar.addView(closeBtn)
        root.addView(titleBar)

        // 会话名称
        val conversationText = TextView(this).apply {
            text = currentConversation
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(20)
            )
        }
        root.addView(conversationText)

        // 建议列表容器
        val suggestionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            id = R.id.suggestions_container
        }
        root.addView(suggestionsContainer)

        // 底部操作栏
        val actionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(36)
            )
        }

        val openAppBtn = Button(this).apply {
            text = "打开App"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#E8E8E8"))
            setOnClickListener { openMainApp() }
        }
        actionBar.addView(openAppBtn)
        root.addView(actionBar)

        // 设置触摸监听 (拖拽)
        titleBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isMoving = true
                    }
                    
                    if (isMoving) {
                        params?.x = initialX + deltaX
                        params?.y = initialY + deltaY
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        // 点击了标题栏，可以展开/收起
                        toggleExpand()
                    }
                    true
                }
                else -> false
            }
        }

        // 更新建议显示
        updateSuggestions()

        return root
    }

    /**
     * 更新建议列表
     */
    private fun updateSuggestions() {
        if (floatingView == null) return

        val container = floatingView?.findViewById<LinearLayout>(R.id.suggestions_container)
        container?.removeAllViews()

        val density = resources.displayMetrics.density

        currentSuggestions.take(3).forEachIndexed { index, suggestion ->
            val btn = Button(this).apply {
                text = if (AppPreferences.showReasoning && suggestion.reasoning != null) {
                    "${suggestion.text}\n(${String.format("%.0f", suggestion.confidence * 100)}%)"
                } else {
                    suggestion.text
                }
                textSize = 13f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(40)
                ).apply {
                    topMargin = dpToPx(4)
                }
                setBackgroundResource(R.drawable.suggestion_button_background)
                setPadding(dpToPx(8), 0, dpToPx(8), 0)
                maxLines = 2

                setOnClickListener {
                    onSuggestionClicked(index)
                }

                setOnLongClickListener {
                    // 长按复制
                    copyToClipboard(suggestion.text)
                    Toast.makeText(this@ChatOverlayService, "已复制", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            container?.addView(btn)
        }
    }

    /**
     * 处理点击建议
     */
    private fun onSuggestionClicked(index: Int) {
        if (index in currentSuggestions.indices) {
            val suggestion = currentSuggestions[index]
            copyToClipboard(suggestion.text)
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            
            // 震动反馈
            if (AppPreferences.hapticFeedback) {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator?.vibrate(30)
                }
            }
            
            // 隐藏悬浮窗
            hideOverlay()
        }
    }

    /**
     * 复制到剪贴板
     */
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("reply", text)
        clipboard.setPrimaryClip(clip)
    }

    /**
     * 切换最小化状态
     */
    private fun toggleMinimize() {
        isExpanded = !isExpanded
        
        params?.let { p ->
            p.height = if (isExpanded) {
                dpToPx(OVERLAY_HEIGHT)
            } else {
                dpToPx(60)
            }
            windowManager?.updateViewLayout(floatingView, p)
        }
    }

    /**
     * 切换展开状态
     */
    private fun toggleExpand() {
        toggleMinimize()
    }

    /**
     * 打开主App
     */
    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    /**
     * dp转px
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // Action constants
    private val ACTION_SHOW_SUGGESTIONS = "com.lisa.chatassist.SHOW_SUGGESTIONS"
    private val ACTION_UPDATE_CONVERSATION = "com.lisa.chatassist.UPDATE_CONVERSATION"
    private val ACTION_HIDE = "com.lisa.chatassist.HIDE"
}
