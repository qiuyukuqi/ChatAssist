package com.lisa.chatassist.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.lisa.chatassist.R
import com.lisa.chatassist.data.AppPreferences
import com.lisa.chatassist.data.ReplyStyle

/**
 * 设置页面
 */
class SettingsActivity : AppCompatActivity() {

    // AI设置
    private lateinit var apiKeyInput: EditText
    private lateinit var apiEndpointInput: EditText
    private lateinit var aiProviderSpinner: Spinner
    
    // 回复设置
    private lateinit var replyStyleSpinner: Spinner
    private lateinit var maxTokensSlider: SeekBar
    private lateinit var maxTokensValue: TextView
    private lateinit var minConfidenceSlider: SeekBar
    private lateinit var minConfidenceValue: TextView
    
    // 体验设置
    private lateinit var autoPasteSwitch: Switch
    private lateinit var hapticSwitch: Switch
    private lateinit var soundSwitch: Switch
    private lateinit var showReasoningSwitch: Switch
    
    // 保存按钮
    private lateinit var saveBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadSettings()
        setupListeners()
    }

    private fun initViews() {
        // AI设置
        apiKeyInput = findViewById(R.id.api_key_input)
        apiEndpointInput = findViewById(R.id.api_endpoint_input)
        aiProviderSpinner = findViewById(R.id.ai_provider_spinner)
        
        // 回复设置
        replyStyleSpinner = findViewById(R.id.reply_style_spinner)
        maxTokensSlider = findViewById(R.id.max_tokens_slider)
        maxTokensValue = findViewById(R.id.max_tokens_value)
        minConfidenceSlider = findViewById(R.id.min_confidence_slider)
        minConfidenceValue = findViewById(R.id.min_confidence_value)
        
        // 体验设置
        autoPasteSwitch = findViewById(R.id.auto_paste_switch)
        hapticSwitch = findViewById(R.id.haptic_switch)
        soundSwitch = findViewById(R.id.sound_switch)
        showReasoningSwitch = findViewById(R.id.show_reasoning_switch)
        
        // 保存按钮
        saveBtn = findViewById(R.id.save_button)
    }

    private fun loadSettings() {
        // AI设置
        apiKeyInput.setText(AppPreferences.apiKey)
        apiEndpointInput.setText(AppPreferences.apiEndpoint)
        
        // AI提供商
        val providers = arrayOf("MiniMax", "OpenAI", "Claude", "自定义")
        val providerValues = arrayOf("minimax", "openai", "claude", "custom")
        val providerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)
        aiProviderSpinner.adapter = providerAdapter
        val providerIndex = providerValues.indexOf(AppPreferences.aiProvider)
        if (providerIndex >= 0) aiProviderSpinner.setSelection(providerIndex)
        
        // 回复风格
        val styles = arrayOf("普通", "轻松随意", "礼貌正式", "简短", "幽默")
        val styleValues = arrayOf("NORMAL", "CASUAL", "POLITE", "SHORT", "HUMOR")
        val styleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, styles)
        replyStyleSpinner.adapter = styleAdapter
        val styleIndex = styleValues.indexOf(AppPreferences.replyStyle.name)
        if (styleIndex >= 0) replyStyleSpinner.setSelection(styleIndex)
        
        // Max Tokens
        maxTokensSlider.max = 500
        maxTokensSlider.progress = AppPreferences.maxTokens
        maxTokensValue.text = "${AppPreferences.maxTokens} tokens"
        
        // Min Confidence
        minConfidenceSlider.max = 100
        minConfidenceSlider.progress = (AppPreferences.minConfidence * 100).toInt()
        minConfidenceValue.text = "${(AppPreferences.minConfidence * 100).toInt()}%"
        
        // 体验设置
        autoPasteSwitch.isChecked = AppPreferences.autoPaste
        hapticSwitch.isChecked = AppPreferences.hapticFeedback
        soundSwitch.isChecked = AppPreferences.soundEffect
        showReasoningSwitch.isChecked = AppPreferences.showReasoning
        
        // 更新API端点提示
        updateEndpointHint()
    }

    private fun setupListeners() {
        // AI提供商切换
        aiProviderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateEndpointHint()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Max Tokens滑块
        maxTokensSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                maxTokensValue.text = "${progress} tokens"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Min Confidence滑块
        minConfidenceSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                minConfidenceValue.text = "${progress}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 保存按钮
        saveBtn.setOnClickListener {
            saveSettings()
        }
    }

    private fun updateEndpointHint() {
        val providerValues = arrayOf("minimax", "openai", "claude", "custom")
        val endpoints = arrayOf(
            "https://api.minimax.chat/v1",
            "https://api.openai.com/v1",
            "https://api.anthropic.com/v1",
            "请输入自定义API地址"
        )
        val selectedIndex = aiProviderSpinner.selectedItemPosition
        if (selectedIndex in providerValues.indices) {
            val currentEndpoint = apiEndpointInput.text.toString()
            if (currentEndpoint.isBlank() || providerValues.contains(currentEndpoint)) {
                apiEndpointInput.hint = endpoints[selectedIndex]
            }
        }
    }

    private fun saveSettings() {
        // 保存AI设置
        AppPreferences.apiKey = apiKeyInput.text.toString().trim()
        AppPreferences.apiEndpoint = apiEndpointInput.text.toString().trim()
        
        val providerValues = arrayOf("minimax", "openai", "claude", "custom")
        if (aiProviderSpinner.selectedItemPosition in providerValues.indices) {
            AppPreferences.aiProvider = providerValues[aiProviderSpinner.selectedItemPosition]
        }
        
        // 保存回复设置
        val styleValues = arrayOf("NORMAL", "CASUAL", "POLITE", "SHORT", "HUMOR")
        if (replyStyleSpinner.selectedItemPosition in styleValues.indices) {
            AppPreferences.replyStyle = ReplyStyle.valueOf(styleValues[replyStyleSpinner.selectedItemPosition])
        }
        AppPreferences.maxTokens = maxTokensSlider.progress
        AppPreferences.minConfidence = minConfidenceSlider.progress / 100f
        
        // 保存体验设置
        AppPreferences.autoPaste = autoPasteSwitch.isChecked
        AppPreferences.hapticFeedback = hapticSwitch.isChecked
        AppPreferences.soundEffect = soundSwitch.isChecked
        AppPreferences.showReasoning = showReasoningSwitch.isChecked
        
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }
}
