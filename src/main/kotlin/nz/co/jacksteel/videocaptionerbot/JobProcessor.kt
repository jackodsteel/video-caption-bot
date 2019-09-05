package nz.co.jacksteel.videocaptionerbot

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nz.co.jacksteel.videocaptionerbot.jobsources.InputOutputSource
import nz.co.jacksteel.videocaptionerbot.jobsources.VideoCaptionJob
import nz.co.jacksteel.videocaptionerbot.model.SpeechToTextTranslator
import nz.co.jacksteel.videocaptionerbot.video.VideoEncoder
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class JobProcessor(
        private val inputOutputSources: List<InputOutputSource> = listOf(),
        private val fileDownloader: FileDownloader,
        private val speechToTextTranslator: SpeechToTextTranslator,
        private val videoEncoder: VideoEncoder
) {

    companion object {
        private const val POLL_RATE_MS = 5000L
    }

    @Scheduled(fixedRate = POLL_RATE_MS)
    fun queryForJobs() {
        println("Running job")
        inputOutputSources
                .flatMap { runBlocking { it.queryForJobs() } }
                .forEachLaunch { processCaptionJob(it) }
    }

    suspend fun processCaptionJob(videoCaptionJob: VideoCaptionJob) {
        val videoFile = fileDownloader.downloadFile(videoCaptionJob.videoDownloadUrl)
        if (videoFile == null) {
            videoCaptionJob.failureCallback(videoCaptionJob.jobMetadata)
            return
        }
        val subtitleInformation = speechToTextTranslator.getTextFromSpeech(videoFile)
        val encodedFile = videoEncoder.addSubtitles(videoFile, subtitleInformation)
        videoCaptionJob.completionCallback(videoCaptionJob.jobMetadata, encodedFile)
    }
}

inline fun <T> Iterable<T>.forEachLaunch(crossinline action: suspend (T) -> Unit) {
    for (element in this) GlobalScope.launch { action(element) }
}