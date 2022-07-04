package ai.prosa.conversa.inapp.utils

object CreateTemplateMessage {
    fun withImage(src: String): String {
        return """
            <div class="is-image"><img src="$src" class="cursor-pointer is-image" style="max-width: 200px;">
              <div class="row items-center display-flex is-image">
                <div class="bb-paragraph q-ml-sm row items-end display-block">
                  <div class="col-12 text-white" style="max-width: 160px">bluebird.png</div>
                  <div class="bb-stamp text-white" style="opacity: 0.5;">1kb</div>
                </div>
              </div>
            </div>
        """.trimIndent()
    }

    fun withAttachment(url: String, name: String): String {
        return """
            <div class="row items-center is-document">
              <img src=statics/icons/icon_pdf.png class="q-mr-sm" style="max-width: 50px">
              <span url="$url" class="is-document">$name</span>
            </div>
        """.trimIndent()
    }
}