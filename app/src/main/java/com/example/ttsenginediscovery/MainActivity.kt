package com.example.ttsenginediscovery

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ttsenginediscovery.databinding.ActivityMainBinding
import java.util.Locale

/** 选择系统已暴露的 TTS 引擎（[TextToSpeech.getEngines]），试听朗读与语速。 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var frameworkEngines: List<TextToSpeech.EngineInfo> = emptyList()
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonRefresh.setOnClickListener { loadEngines() }
        binding.buttonTtsSettings.setOnClickListener { openTtsSettings() }

        binding.seekSpeechRate.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    updateRateLabel(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            },
        )
        updateRateLabel(binding.seekSpeechRate.progress)

        binding.buttonSpeak.setOnClickListener { speakWithSelectedEngine() }
        binding.buttonStopSpeak.setOnClickListener { stopSpeaking() }

        loadEngines()
    }

    override fun onDestroy() {
        releaseTts()
        super.onDestroy()
    }

    private fun loadEngines() {
        frameworkEngines = queryFrameworkEngines().sortedBy { it.label?.toString() ?: it.name }
        populateEngineSpinner()
    }

    private fun progressToSpeechRate(progress: Int): Float =
        0.5f + (progress.coerceIn(0, 100) / 100f) * 1.5f

    private fun updateRateLabel(progress: Int) {
        val rate = progressToSpeechRate(progress)
        binding.textRateLabel.text = getString(R.string.label_speech_rate, rate)
    }

    private fun populateEngineSpinner() {
        val labels = frameworkEngines.map { info ->
            val label = info.label?.toString().orEmpty()
            if (label.isNotEmpty()) {
                "$label (${info.name})"
            } else {
                info.name
            }
        }
        val adapterSpinner = ArrayAdapter(
            this,
            R.layout.spinner_item_large,
            labels,
        ).also { it.setDropDownViewResource(R.layout.spinner_dropdown_item_large) }
        binding.spinnerEngine.adapter = adapterSpinner
        val hasEngines = frameworkEngines.isNotEmpty()
        binding.spinnerEngine.isEnabled = hasEngines
        binding.buttonSpeak.isEnabled = hasEngines
    }

    private fun speakWithSelectedEngine() {
        if (frameworkEngines.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_engines, Toast.LENGTH_LONG).show()
            return
        }
        val text = binding.editSampleText.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.toast_empty_text, Toast.LENGTH_SHORT).show()
            return
        }
        val pos = binding.spinnerEngine.selectedItemPosition
        val engine = frameworkEngines.getOrNull(pos)?.name
        val rate = progressToSpeechRate(binding.seekSpeechRate.progress)

        releaseTts()
        tts = if (engine.isNullOrEmpty()) {
            TextToSpeech(this) { status -> onTtsReady(status, text, rate) }
        } else {
            TextToSpeech(this, { status -> onTtsReady(status, text, rate) }, engine)
        }
    }

    private fun onTtsReady(status: Int, text: String, rate: Float) {
        if (status != TextToSpeech.SUCCESS) {
            Toast.makeText(this, R.string.toast_tts_init_failed, Toast.LENGTH_LONG).show()
            return
        }
        val engine = tts ?: return
        engine.stop()
        var lang = engine.setLanguage(Locale.forLanguageTag("zh-CN"))
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            lang = engine.setLanguage(Locale.CHINESE)
        }
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.getDefault())
        }
        engine.setSpeechRate(rate)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts-demo")
    }

    private fun stopSpeaking() {
        tts?.stop()
    }

    private fun releaseTts() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun queryFrameworkEngines(): List<TextToSpeech.EngineInfo> {
        val tts = TextToSpeech(this) { }
        return try {
            tts.engines
        } finally {
            tts.shutdown()
        }
    }

    private fun openTtsSettings() {
        val intents = listOf(
            Intent("android.settings.TTS_SETTINGS"),
            Intent("com.android.settings.TTS_SETTINGS"),
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(
            this,
            "无法打开专用界面，请在系统设置里搜索「文字转语音」或「TTS」",
            Toast.LENGTH_LONG,
        ).show()
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) { }
    }
}
