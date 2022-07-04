package ai.prosa.conversa.inapp.utils

import ai.prosa.conversa.common.api.ConversaApi
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.github.squti.androidwaverecorder.WaveRecorder
import java.io.File


interface AudioProcessingInterface {
    fun onStart()
    fun onSuccess(transcript: String)
    fun onFinishedRecording(filePath: String, millisecondsDuration: Int)
    fun onError(t: Throwable)
}

open class AudioProcessingBase(
    private val context: Context,
    private val process: AudioProcessingInterface
) {
    protected val cacheAudioFile = File(context.cacheDir, "audio.wav")
    private var recorder: WaveRecorder? = null

    private var _isRecording = false
    val isRecording get() = _isRecording

    open fun start() {
        process.onStart()

        _isRecording = true
        recorder = WaveRecorder(cacheAudioFile.absolutePath)
        recorder!!.startRecording()
    }

    open fun end() {
        if (_isRecording) {
            recorder!!.stopRecording()
            _isRecording = false
        }

        val duration = getMillisecondsDuration(context, cacheAudioFile.absolutePath)
        process.onFinishedRecording(cacheAudioFile.absolutePath, duration)
    }

    fun cancel() {
        if (_isRecording) {
            recorder!!.stopRecording()
            _isRecording = false
        }

        cacheAudioFile.delete()
    }

    private fun getMillisecondsDuration(context: Context, filePath: String): Int {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, Uri.parse(filePath))
        val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

        return durationStr!!.toInt()
    }
}

class Asr(private val context: Context, private val process: AudioProcessingInterface) :
    AudioProcessingBase(context, process) {

    override fun end() {
        try {
            super.end()
            ConversaApi.transcript(cacheAudioFile.absolutePath) { it ->
                val transcript = it?.flatten()?.joinToString(".") { it.text }
                process.onSuccess(transcript!!)
            }
        } catch (e: Throwable) {
            process.onError(e)
        }
    }

    companion object {
        private const val TAG = "Asr"
    }
}

class AudioRecording(private val context: Context, private val process: AudioProcessingInterface) :
    AudioProcessingBase(context, process)