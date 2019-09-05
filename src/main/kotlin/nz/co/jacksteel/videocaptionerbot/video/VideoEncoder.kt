package nz.co.jacksteel.videocaptionerbot.video

import java.io.File

interface VideoEncoder {

    /**
     * Adds the given [subtitleInformation], to the [videoFile], and returns the new video [File] that has the subtitles overlaid
     */
    suspend fun addSubtitles(videoFile: File, subtitleInformation: File): File

}