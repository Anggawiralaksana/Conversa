package ai.prosa.conversa.common.utils

object HtmlParser {
    private val LINK_REGEX =
        ".*?((https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]).*$".toRegex()

    fun isImage(msg: String): Boolean {
        return if (msg.contains("is-image")) {
            true
        } else {
            val regex = """.*?<img """.toRegex()
            regex.containsMatchIn(msg)
        }
    }

    fun isAttachment(msg: String): Boolean {
        return if (msg.contains("is-document")) {
            true
        } else {
            val regex = """url=""".toRegex()
            regex.containsMatchIn(msg)
        }
    }

    fun doesContainUrl(msg: String): Boolean {
        return LINK_REGEX.containsMatchIn(msg)
    }

    fun extractImageSrc(msg: String): String {
        val regex = """<img src="(.*?)".*>""".toRegex()
        val result = regex.find(msg)
        return if (result != null) {
            result.groupValues[1]
        } else {
            ""
        }
    }

    fun extractAttachmentUrl(msg: String): String {
        val regex = """.*?url="(.*?)".*""".toRegex()
        val result = regex.find(msg)
        return if (result != null) {
            result.groupValues[1]
        } else {
            ""
        }
    }

    fun extractAttachmentName(msg: String): String {
        val regex = """.*?url=".*".*>(.*?)</span>""".toRegex()
        val result = regex.find(msg)
        return if (result != null) {
            result.groupValues[1]
        } else {
            ""
        }
    }

    fun extractExtension(msg: String): String {
        return "." + msg.substringAfterLast(".")
    }

    fun extractUrl(msg: String): String {
        val result = LINK_REGEX.find(msg)
        return if (result != null) {
            result.groupValues[1]
        } else {
            ""
        }
    }
}