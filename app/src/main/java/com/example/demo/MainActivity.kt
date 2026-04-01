package com.example.demo

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.demo.databinding.ActivityMainBinding
import java.util.Locale

/** 本地 TTS：多段文本 QUEUE_FLUSH + QUEUE_ADD 排队，与 Legado [TTSReadAloudService] 思路一致。 */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.switchFollowSystem.isChecked = true
        updateSpeedUiEnabled()
        updateSpeedLabel(binding.seekSpeechRate.progress)

        textToSpeech = TextToSpeech(this, this)

        binding.buttonSpeak.setOnClickListener { speakQueued() }
        binding.buttonStop.setOnClickListener { stopSpeaking() }

        binding.switchFollowSystem.setOnCheckedChangeListener { _, isChecked ->
            updateSpeedUiEnabled()
            if (isChecked) {
                restartTtsEngine()
            } else {
                applySpeechRate()
            }
        }

        binding.seekSpeechRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateSpeedLabel(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (!binding.switchFollowSystem.isChecked) {
                    applySpeechRate()
                }
            }
        })
    }

    private fun updateSpeedUiEnabled() {
        binding.seekSpeechRate.isEnabled = !binding.switchFollowSystem.isChecked
    }

    /** Legado 映射：ttsSpeechRate 整数 progress，speechRate = (progress + 5) / 10f */
    private fun updateSpeedLabel(progress: Int) {
        val rate = (progress + 5) / 10f
        binding.textSpeedValue.text = String.format(Locale.getDefault(), "%.1f×", rate)
    }

    override fun onInit(status: Int) {
        // Callback may run off the main thread on some devices; all UI + typical TTS setup on UI thread.
        runOnUiThread {
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(this, R.string.tts_init_failed, Toast.LENGTH_LONG).show()
                binding.textStatus.setText(R.string.status_init_failed)
                return@runOnUiThread
            }
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread {
                        binding.textStatus.text = getString(R.string.status_speaking, utteranceId.orEmpty())
                    }
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        binding.textStatus.text = getString(R.string.status_done_chunk, utteranceId.orEmpty())
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    runOnUiThread {
                        binding.textStatus.text = getString(R.string.status_error, errorCode)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {}
            })

            val tts = textToSpeech ?: return@runOnUiThread
            val langResult = tts.setLanguage(Locale.getDefault())
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, R.string.lang_missing, Toast.LENGTH_LONG).show()
            }
            ttsReady = true
            applySpeechRate()
            binding.textStatus.setText(R.string.status_ready)
        }
    }

    /** 勾选「跟随系统」时重启引擎，避免沿用上一次 setSpeechRate。 */
    private fun restartTtsEngine() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        ttsReady = false
        textToSpeech = TextToSpeech(this, this)
    }

    private fun applySpeechRate() {
        val tts = textToSpeech ?: return
        if (binding.switchFollowSystem.isChecked) {
            return
        }
        val rate = (binding.seekSpeechRate.progress + 5) / 10f
        tts.setSpeechRate(rate)
    }

    private fun speakQueued() {
        val segments = binding.editText.text?.toString()
            .orEmpty()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (segments.isEmpty()) {
            Toast.makeText(this, R.string.empty_input, Toast.LENGTH_SHORT).show()
            return
        }
        if (!ttsReady) {
            Toast.makeText(this, R.string.tts_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val tts = textToSpeech ?: return
        tts.stop()
        applySpeechRate()
        var first = true
        for ((i, segment) in segments.withIndex()) {
            val utteranceId = "${UTTERANCE_PREFIX}_$i"
            val queueMode = if (first) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            first = false
            val result = tts.speak(segment, queueMode, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                Toast.makeText(this, R.string.speak_error, Toast.LENGTH_SHORT).show()
                break
            }
        }
        binding.textStatus.text = getString(R.string.status_queued, segments.size)
    }

    private fun stopSpeaking() {
        textToSpeech?.stop()
        binding.textStatus.setText(R.string.status_stopped)
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }

    companion object {
        private const val UTTERANCE_PREFIX = "demo_tts"
    }
}
