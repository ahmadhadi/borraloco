package app.cleanlink.core

enum class CaptionPlatform { INSTAGRAM, TIKTOK }

/** One entry point for "copy the caption": picks Instagram or TikTok from the link. */
class CaptionFetcher(
    private val instagram: InstagramCaptionFetcher = InstagramCaptionFetcher(),
    private val tiktok: TikTokCaptionFetcher = TikTokCaptionFetcher(),
) {
    fun fetch(link: String): CaptionResult = when (platformOf(link)) {
        CaptionPlatform.INSTAGRAM -> instagram.fetch(link)
        CaptionPlatform.TIKTOK -> tiktok.fetch(link)
        null -> CaptionResult.NotAPost
    }

    companion object {
        /** Which platform's post [link] is, or null when it is neither. */
        fun platformOf(link: String): CaptionPlatform? = when {
            InstagramCaptions.parsePost(link) != null -> CaptionPlatform.INSTAGRAM
            TikTokCaptions.parseLink(link) != null -> CaptionPlatform.TIKTOK
            else -> null
        }
    }
}
