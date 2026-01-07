package com.studiokei.walkaround.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * TextToSpeech (TTS) の初期化と読み上げを管理する共通ヘルパークラス。
 */
class TextToSpeechHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 日本語に設定
            val result = tts?.setLanguage(Locale.JAPANESE)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isReady = true
                Log.d("TextToSpeechHelper", "TTS: 日本語の準備が完了しました。")
            } else {
                Log.e("TextToSpeechHelper", "TTS: 日本語がサポートされていないか、データが不足しています。")
            }
        } else {
            Log.e("TextToSpeechHelper", "TTS: 初期化に失敗しました。ステータス: $status")
        }
    }

    /**
     * 指定されたテキストを音声で読み上げます。
     */
    fun speak(text: String) {
        if (isReady && text.isNotBlank()) {
            // QUEUE_FLUSH を使用して、現在の読み上げを中断して新しいテキストを読み上げる
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WalkAroundSpeech")
        }
    }

    /**
     * 現在の読み上げを停止します。
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * リソースを解放します。
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
