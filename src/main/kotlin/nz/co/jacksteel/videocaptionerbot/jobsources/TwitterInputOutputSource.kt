package nz.co.jacksteel.videocaptionerbot.jobsources

import io.ktor.http.Cookie
import jp.nephy.jsonkt.string
import jp.nephy.penicillin.PenicillinClient
import jp.nephy.penicillin.core.session.config.account
import jp.nephy.penicillin.core.session.config.application
import jp.nephy.penicillin.core.session.config.token
import jp.nephy.penicillin.endpoints.media
import jp.nephy.penicillin.endpoints.media.*
import jp.nephy.penicillin.endpoints.statuses
import jp.nephy.penicillin.endpoints.statuses.create
import jp.nephy.penicillin.endpoints.statuses.lookup
import jp.nephy.penicillin.endpoints.timeline
import jp.nephy.penicillin.endpoints.timeline.mentionsTimeline
import jp.nephy.penicillin.extensions.await
import jp.nephy.penicillin.extensions.complete
import jp.nephy.penicillin.models.Media
import jp.nephy.penicillin.models.Status
import jp.nephy.penicillin.models.entities.MediaEntity
import kotlinx.coroutines.delay
import nz.co.jacksteel.videocaptionerbot.TwitterCredentials
import nz.co.jacksteel.videocaptionerbot.jobsources.penicillin.StatusAuthenticationResponse
import nz.co.jacksteel.videocaptionerbot.jobsources.penicillin.conversation
import nz.co.jacksteel.videocaptionerbot.util.SharedConstants
import nz.co.jacksteel.videocaptionerbot.util.forEachBlockIndexed
import org.springframework.stereotype.Service
import java.io.File
import java.net.URL

@Service
class TwitterInputOutputSource(
        twitterCredentials: TwitterCredentials
) : InputOutputSource {

    private val FOUR_MEGABYTES_IN_BYTES = 4 * 1048576
    private val TWO_SECONDS_IN_MS = 2000L

    private var lastReadId: Long = 1
    private val tweetIdsInProgress = mutableSetOf<Long>()

    /**
     * TODO: Consider if this is worth long term caching or not
     */
    private val tweetIdsDone = mutableSetOf<Long>()

    private val client = PenicillinClient {
        account {
            application(twitterCredentials.consumerKey, twitterCredentials.consumerSecret)
            token(twitterCredentials.accessToken, twitterCredentials.accessTokenSecret)
        }
    }

    private val authenticationData = StatusAuthenticationResponse(
            cookies = listOf(
                    Cookie("auth_token", twitterCredentials.statusCredentials.auth_token),
                    Cookie("ct0", twitterCredentials.statusCredentials.ct0),
                    Cookie("guest_id", twitterCredentials.statusCredentials.guest_id),
                    Cookie("personalization_id", twitterCredentials.statusCredentials.personalization_id),
                    Cookie("_twitter_sess", twitterCredentials.statusCredentials.twitter_sess)
            ),
            xCsrfToken = twitterCredentials.statusCredentials.ct0,
            authorization = SharedConstants.TWITTER_AUTH_TOKEN
    )
    private val botUid = twitterCredentials.botUid


    override suspend fun queryForJobs(): List<VideoCaptionJob> {
        val allStatuses = client.timeline.mentionsTimeline(sinceId = lastReadId).await().results
        if (allStatuses.isEmpty()) {
            println("No new statuses found")
            return emptyList()
        }
        lastReadId = allStatuses.first().id
        val statuses = allStatuses
                .filter { it.inReplyToStatusId != null }
                .filter { it.id !in tweetIdsDone }
                .filter { it.id !in tweetIdsInProgress }
        val parents = client.statuses.lookup(statuses.map { it.inReplyToStatusId!! }).await().results
        return statuses.zip(parents).mapNotNull {
            val video = it.second.getVideo()
            if (video == null) {
                println("${it.first.id}: Parent is not a twitter video")
                //TODO: Reply telling them only works on videos?
                tweetIdsDone.add(it.first.id)
                return@mapNotNull null
            }
            val children = client.statuses.conversation(it.first.id, authenticationData).await().result
            //TODO: Consider removing this part in favour of a persistent store of tweetIdsDone
            if (children.globalObjects.statuses.any { botUid == it.json["user_id_str"]?.string }) {
                println("${it.first.id}: Already replied to this tweet")
                //TODO: Log this as it's an error if this happens after start
                tweetIdsDone.add(it.first.id)
                return@mapNotNull null
            }
            val mediaUrl = URL(video.url)
            println("${it.first.id}: Found video in parent and URL is: $mediaUrl")
            tweetIdsInProgress.add(it.first.id)
            return@mapNotNull VideoCaptionJob(::processSuccessfulJob, ::processFailedJob, mediaUrl, JobMetadata(it.first.id))
        }
    }

    suspend fun processSuccessfulJob(jobMetadata: JobMetadata, file: File) {
        println("${jobMetadata.id}: Uploading video!")
        val uploadResult = uploadVideo(file)
        println("${jobMetadata.id}: Uploaded video, replying now!")
        client.statuses.create(
                status = "Captioned version!",
                inReplyToStatusId = jobMetadata.id,
                mediaIds = listOf(uploadResult.mediaId)
        )
        println("${jobMetadata.id}: Replied!")
    }

    suspend fun uploadVideo(file: File): Media {
        val init = client.media.uploadInit(
                mediaType = MediaType.MP4,
                mediaCategory = MediaCategory.TweetVideo,
                totalBytes = file.length().toInt()
        ).await().result
        val mediaId = init.mediaId
        println("Did upload init, mediaId: $mediaId")
        file.forEachBlockIndexed(blockSize = FOUR_MEGABYTES_IN_BYTES) { chunk, segment ->
            println("Uploading chunk")
            client.media.uploadAppend(
                    mediaId = mediaId,
                    segmentIndex = segment,
                    media = MediaComponent(
                            data = chunk,
                            type = MediaType.MP4,
                            category = MediaCategory.TweetVideo
                    )
            ).complete()
            println("Uploaded chunk")
        }
        println("Done chunks, finalizing")
        val uploadResult = client.media.uploadFinalize(mediaId = mediaId).await().result
        println("Done uploadResult")
        println(uploadResult)
        awaitUploadResult(uploadResult)
        println("Done await")
        return uploadResult
    }

    suspend fun awaitUploadResult(media: Media) {
        var processingInfo = media.processingInfo
        while (true) {
            if (processingInfo == null || processingInfo.state == Media.ProcessingInfo.State.Succeeded) {
                break
            }
            if (processingInfo.state == Media.ProcessingInfo.State.Failed) {
                throw UploadFailedException("Media processing failed after upload, ${processingInfo.error}")
            }
            println("Processing, progress: ${processingInfo.progressPercent}")
            delay(processingInfo.checkAfterSecs?.times(1000L)?: TWO_SECONDS_IN_MS)
            processingInfo = client.media.uploadStatus(media.mediaId).await().result.processingInfo
        }
    }

    suspend fun processFailedJob(jobMetadata: JobMetadata) {
        println("${jobMetadata.id}: Error! Replying to tweet telling the user something went wrong")
        client.statuses.create(
                status = "Something went wrong when trying to caption this video. I'm working on it!",
                inReplyToStatusId = jobMetadata.id
        )
    }
}

class UploadFailedException(message: String): Exception(message)

/**
 * Return the highest quality media variant, or null if no video present
 */
fun Status.getVideo(): MediaEntity.VideoInfo.Variant? {
    val extendedEntities = extendedEntities
    if (extendedEntities == null || extendedEntities.media.size != 1 || extendedEntities.media[0].type != "video") {
        return null
    }
    val mediaEntity = extendedEntities.media[0]
    return mediaEntity.videoInfo?.variants?.maxBy { variant -> variant.bitrate ?: -1 }
}