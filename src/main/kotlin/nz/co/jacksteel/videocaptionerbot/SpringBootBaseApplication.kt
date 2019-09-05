package nz.co.jacksteel.videocaptionerbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(TwitterCredentials::class)
class SpringBootBaseApplication

fun main(args: Array<String>) {
	runApplication<SpringBootBaseApplication>(*args)
}
