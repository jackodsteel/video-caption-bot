package nz.co.jacksteel.videocaptionerbot.jobsources

import java.io.File
import java.net.URL

interface InputOutputSource {

    /**
     * The [InputOutputSource] must make sure not to return the same [VideoCaptionJob] multiple times,
     * e.g. it should mark messages as read when returned, and only fetch unread messages
     */
    suspend fun queryForJobs(): List<VideoCaptionJob>
}

/**
 * The [InputOutputSource] is responsible for handling the uploading/replying of the returned [File]
 */
typealias CompletionCallback = suspend (JobMetadata, File) -> Unit

data class VideoCaptionJob(
        val completionCallback: CompletionCallback,
        val failureCallback: suspend (JobMetadata) -> Unit,
        val videoDownloadUrl: URL,
        val jobMetadata: JobMetadata
)

data class JobMetadata(
        val id: Long
)