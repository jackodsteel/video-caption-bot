package nz.co.jacksteel.videocaptionerbot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import nz.co.jacksteel.videocaptionerbot.model.SpeechToTextTranslator
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.client.RestTemplate
import java.io.File


@Configuration
class SpringConfiguration {

    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate {
        return builder.build()
    }

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().registerModule(KotlinModule())
    }

    @Bean
    fun dummySTT(): SpeechToTextTranslator {
        return object : SpeechToTextTranslator {
            override suspend fun getTextFromSpeech(videoFile: File): File {
                return File("/var/folders/16/21885tzj6wd0b_h3sq1j7s880000gn/T/video_captioner_bot_srt.srt")
            }
        }
    }

}
