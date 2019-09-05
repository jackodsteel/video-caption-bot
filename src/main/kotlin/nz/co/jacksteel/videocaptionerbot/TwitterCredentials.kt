package nz.co.jacksteel.videocaptionerbot

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "twitter")
class TwitterCredentials {
    lateinit var consumerKey: String
    lateinit var botUid: String
    lateinit var consumerSecret: String
    lateinit var accessToken: String
    lateinit var accessTokenSecret: String
    var statusCredentials: TwitterStatusCredentials = TwitterStatusCredentials()

    class TwitterStatusCredentials {
        lateinit var auth_token: String
        lateinit var ct0: String
        lateinit var guest_id: String
        lateinit var personalization_id: String
        lateinit var twitter_sess: String
    }
}