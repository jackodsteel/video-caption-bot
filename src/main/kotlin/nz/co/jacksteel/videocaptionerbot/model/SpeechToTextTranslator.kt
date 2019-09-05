package nz.co.jacksteel.videocaptionerbot.model

import java.io.File

interface SpeechToTextTranslator {

    suspend fun getTextFromSpeech(videoFile: File): File

}

data class SubtitleInformation(
        val subtitles: List<Subtitle>
)

data class Subtitle(
        val timeSinceStart: Float,
        val text: String
)