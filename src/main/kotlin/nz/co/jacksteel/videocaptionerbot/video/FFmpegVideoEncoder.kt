package nz.co.jacksteel.videocaptionerbot.video

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.co.jacksteel.videocaptionerbot.util.SharedConstants.FILE_PREFIX
import org.springframework.stereotype.Service
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files

@Service
class FFmpegVideoEncoder(
        private val ffmpegLocation: String = "ffmpeg"
) : VideoEncoder {

    private val FILETYPE = "mp4"

    override suspend fun addSubtitles(videoFile: File, subtitleInformation: File): File = withContext(Dispatchers.IO) {
        val encodedFile = Files.createTempFile(FILE_PREFIX, ".$FILETYPE")
        val builder = ProcessBuilder()
                .command(
                        ffmpegLocation,
                        "-y",
                        "-i", videoFile.absolutePath,
                        "-vf", "subtitles=${subtitleInformation.absolutePath}",
                        encodedFile.toString()
                )
                .directory(videoFile.parentFile)
        val process = builder.start()
        val code = process.waitFor()
        if (code != 0) {
            //TODO: Log properly
            println(process.errorStream.readAllBytes().toString(charset = Charset.defaultCharset()))
            println(process.inputStream.readAllBytes().toString(charset = Charset.defaultCharset()))
            throw Exception("Non zero status code from FFmpeg $code")
        }
        encodedFile.toFile()
    }

}

suspend fun main() {
    val out = FFmpegVideoEncoder().addSubtitles(
            videoFile = File("/var/folders/16/21885tzj6wd0b_h3sq1j7s880000gn/T/video_captioner_bot12507456864501879459.mp4"),
            subtitleInformation = File("/var/folders/16/21885tzj6wd0b_h3sq1j7s880000gn/T/video_captioner_bot_srt.srt")
    )
    println(out)
}