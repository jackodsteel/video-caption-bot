package nz.co.jacksteel.videocaptionerbot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.co.jacksteel.videocaptionerbot.util.SharedConstants.FILE_PREFIX
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.io.File
import java.net.URL
import java.nio.channels.WritableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Duration


@Service
class FileDownloader {

    companion object {
        private val DOWNLOAD_TIMEOUT = Duration.ofSeconds(30)

    }

    private val webClient = WebClient.create()

    suspend fun downloadFile(url: URL): File? {
        val data = this.webClient.get()
                .uri(url.toURI())
                .retrieve()
                .bodyToFlux(DataBuffer::class.java)

        return withContext(Dispatchers.IO) {
            val file = Files.createTempFile(FILE_PREFIX, ".mp4")
            val channel: WritableByteChannel = Files.newByteChannel(file, StandardOpenOption.WRITE)
            val result: Mono<File> = DataBufferUtils.write(data, channel)
                    .map(DataBufferUtils::release)
                    .then(Mono.just(file.toFile()))

            result.block(DOWNLOAD_TIMEOUT)
        }
    }

}