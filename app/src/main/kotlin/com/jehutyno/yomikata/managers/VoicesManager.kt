package com.jehutyno.yomikata.managers

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.COMMAND_RELEASE
import androidx.media3.exoplayer.ExoPlayer
import com.jehutyno.yomikata.R
import com.jehutyno.yomikata.model.Sentence
import com.jehutyno.yomikata.model.Word
import com.jehutyno.yomikata.util.*


/**
 * Created by valentinlanfranchi on 01/09/2017.
 */
/**
 * Voices manager
 *
 * Used to play audio of sentences or words. Uses either google tts (text-to-speech),
 * or downloaded voices, depending on which is available.
 *
 * This should always be called from the Main Thread.
 *
 * Remember to call releasePlayer() in the onDestroy method of an activity/fragment!
 *
 * @property context Context
 * @constructor Create Voices manager
 */
class VoicesManager(private val context: Context) {

    private val exoPlayer = ExoPlayer.Builder(context).build()
    private val volumeWarning: Toast = Toast.makeText(context, R.string.message_adjuste_volume, Toast.LENGTH_LONG)

    private fun playUriWhenReady(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun warningLowVolume() {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            volumeWarning.cancel()
            volumeWarning.show()
        }
    }

    /**
     * Speak exo
     *
     * Speaks the voice file stored in the voices folder, e.g s_20.mp3 is sentence with id = 20.
     *
     * @param prefix "s" for sentence, "w" for word
     * @param id Id of the word/sentence, should correspond to the voice file
     * @param downloadLevel Used if voices not downloaded, to get the specific level to download
     */
    private fun speakExo(prefix: String, id: Long, downloadLevel: Int) {
        try {
            val uri = Uri.parse(
                "${FileUtils.getDataDir(context, "Voices").absolutePath}/${prefix}_${id}.mp3"
            )
            playUriWhenReady(uri)
        } catch (e: Exception) {
            speechNotSupportedAlert(context, downloadLevel) {}
        }
    }

    /**
     * Speak sentence
     *
     * Will stop any previous sound and play the sentence.
     *
     * @param sentence Sentence to play
     * @param ttsSupported
     * @param tts TextToSpeech instance to use
     * @param userAction True if the user chose to play this, false otherwise
     */
    fun speakSentence(sentence: Sentence, ttsSupported: Int, tts: TextToSpeech?, userAction: Boolean) {
        if (userAction) // don't display warning if the audio is automatic (in case user doesn't want volume)
            warningLowVolume()

        when (checkSpeechAvailability(context, ttsSupported, sentence.level)) {
            SpeechAvailability.VOICES_AVAILABLE -> {
                speakExo("s", sentence.id, sentence.level)
            }
            SpeechAvailability.TTS_AVAILABLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts?.speak(sentenceNoFuri(sentence), TextToSpeech.QUEUE_FLUSH, null, null)
                } else {    // remove this if minBuildVersion >= 21 (LOLLIPOP)
                    @Suppress("DEPRECATION")
                    tts?.speak(sentenceNoFuri(sentence), TextToSpeech.QUEUE_FLUSH, null)
                }
            }
            else -> speechNotSupportedAlert(context, sentence.level) {}
        }
    }

    /**
     * Speak word
     *
     * Will stop any previous sound and play the word.
     *
     * @param word Word to play
     * @param ttsSupported
     * @param tts TextToSpeech instance to use
     * @param userAction True if the user chose to play this, false otherwise
     */
    fun speakWord(word: Word, ttsSupported: Int, tts: TextToSpeech?, userAction: Boolean) {
        if (userAction) // don't display warning if the audio is automatic (in case user doesn't want volume)
            warningLowVolume()

        val level = getCategoryLevel(word.baseCategory)

        when (checkSpeechAvailability(context, ttsSupported, level)) {
            SpeechAvailability.VOICES_AVAILABLE -> {
                speakExo("w", word.id, level)
            }
            SpeechAvailability.TTS_AVAILABLE -> {
                val say = if (word.isKana >= 1)
                        // if not kanji, take the japanese
                    word.japanese.split("/")[0].split(";")[0]
                else    // if kanji, use the reading
                    word.reading.split("/")[0].split(";")[0]

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    tts?.speak(say, TextToSpeech.QUEUE_FLUSH, null,null)
                } else {    // remove this if minBuildVersion >= 21 (LOLLIPOP)
                    @Suppress("DEPRECATION")
                    tts?.speak(say, TextToSpeech.QUEUE_FLUSH, null)
                }
            }
            else -> speechNotSupportedAlert(context, level) {}
        }
    }

    /**
     * Release player
     *
     * Call this when you no longer need the ExoPlayer.
     * Do not use the VoicesManager after calling this.
     */
    fun releasePlayer() {
        if (exoPlayer.isCommandAvailable(COMMAND_RELEASE)) {
            exoPlayer.release()
        }
    }

}
