package com.lisa.chatassist

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lisa.chatassist.data.AppPreferences
import com.lisa.chatassist.services.NotificationService
import com.lisa.chatassist.ui.overlay.ChatOverlayService
import com.lisa.chatassist.ui.settings.SettingsActivity

/**
 * 主界面
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val NOTIFICATION_ACCESS_REQUEST = 1001
        private const val OVERLAY_PERMISSION_REQUEST = 1002
    }

    // Views
    private lateinit var statusText: TextView
    private lateinit var notificationStatusText: TextView
    private lateinit var overlayStatusText: TextView
    private lateinit var enableSwitch: Button
    private lateinit var settingsBtn: Button
    private lateinit var testBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun initViews() {
        statusText = findViewById(R.id.status_text)
        notificationStatusText = findViewById(R.id.notification_status)
        overlayStatusText = findViewById(R.id.overlay_status)
        enableSwitch = findViewById(R.id.enable_switch)
        settingsBtn = findViewById(R.id.settings_button)
        testBtn = findViewById(R.id.test_button)
    }

    private fun setupListeners() {
        // 启用/禁用开关
        enableSwitch.setOnClickListener {
            if (AppPreferences.isEnabled) {
                AppPreferences.isEnabled = false
                Toast.makeText(this, "Chat Assist 已禁用", Toast.LENGTH_SHORT).show()
            } else {
                // 检查权限
                if (!checkPermissions()) {
                    requestPermissions()
                    return@setOnClickListener
                }
                AppPreferences.isEnabled = true
                Toast.makeText(this, "Chat Assist 已启用", Toast.LENGTH_SHORT).show()
            }
            updateStatus()
            restartServices()
        }

        // 设置按钮
        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 测试按钮
        testBtn.setOnClickListener {
            testOverlay()
        }
    }

    private fun updateStatus() {
        // 更新服务状态
        val isEnabled = AppPreferences.isEnabled
        statusText.text = if (isEnabled) "✅ 运行中" else "⏸️ 已停止"
        statusText.setTextColor(
            if (isEnabled) ContextCompat.getColor(this, android.R.color.holo_green_dark)
            else ContextCompat.getColor(this, android.R.color.darker_gray)
        )

        // 检查通知访问权限
        val hasNotificationAccess = isNotificationAccessGranted()
        notificationStatusText.text = if (hasNotificationAccess) {
            "✅ 通知访问权限已授权"
        } else {
            "❌ 通知访问权限未授权"
        }

        // 检查悬浮窗权限
        val hasOverlayPermission = Settings.canDrawOverlays(this)
        overlayStatusText.text = if (hasOverlayPermission) {
            "✅ 悬浮窗权限已授权"
        } else {
            "❌ 悬浮窗权限未授权"
        }

        // 更新按钮文字
        enableSwitch.text = if (AppPreferences.isEnabled) "禁用" else "启用"
        enableSwitch.isEnabled = hasNotificationAccess && hasOverlayPermission
    }

    private fun checkPermissions(): Boolean {
        val notificationAccess = isNotificationAccessGranted()
        val overlayPermission = Settings.canDrawOverlays(this)
        return notificationAccess && overlayPermission
    }

    private fun requestPermissions() {
        // 请求通知访问权限
        if (!isNotificationAccessGranted()) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivityForResult(intent, NOTIFICATION_ACCESS_REQUEST)
            return
        }

        // 请求悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val componentName = ComponentName(this, NotificationService::class.java)
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(componentName.flattenToString()) == true
    }

    private fun restartServices() {
        // 停止服务
        val nlServiceIntent = Intent(this, NotificationService::class.java)
        stopService(nlServiceIntent)

        val overlayIntent = Intent(this, ChatOverlayService::class.java)
        stopService(overlayIntent)

        if (AppPreferences.isEnabled) {
            // 重启服务
            if (Settings.canDrawOverlays(this)) {
                startService(overlayIntent)
            }
            // NotificationListenerService 会自动重启
        }
    }

    private fun testOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }

        // 测试显示
        val intent = Intent(this, ChatOverlayService::class.java).apply {
            action = "com.lisa.chatassist.SHOW_SUGGESTIONS"
        }
        startService(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            NOTIFICATION_ACCESS_REQUEST, OVERLAY_PERMISSION_REQUEST -> {
                updateStatus()
                if (checkPermissions() && !AppPreferences.isEnabled) {
                    AppPreferences.isEnabled = true
                    Toast.makeText(this, "已启用 Chat Assist", Toast.LENGTH_SHORT).show()
                    restartServices()
                }
            }
        }
    }
}
