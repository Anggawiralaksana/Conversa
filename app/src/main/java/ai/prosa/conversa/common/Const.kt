package ai.prosa.conversa.common

import ai.prosa.conversa.R

const val GALLERY_INTENT_ID = 1
const val CAMERA_INTENT_ID = 2

val EXTENSION_ICON_MAPPING = mapOf(
    ".docx" to R.drawable.conversa_ic_word,
    ".doc" to R.drawable.conversa_ic_docs,
    ".xls" to R.drawable.conversa_ic_excel,
    ".xlsx" to R.drawable.conversa_ic_excel,
    ".ppt" to R.drawable.conversa_ic_ppt,
    ".pptx" to R.drawable.conversa_ic_ppt,
    ".pdf" to R.drawable.conversa_ic_pdf,
    ".mp3" to R.drawable.conversa_ic_audio
)

val EXTENSION_MIME_MAPPING = mapOf(
    ".docx" to "application/msword",
    ".doc" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",

    ".xlsx" to "application/vnd.ms-excel",
    ".xls" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",

    ".pptx" to "application/vnd.ms-powerpoint",
    ".ppt" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",

    ".pdf" to "application/pdf",
    ".mp3" to "audio/*"
)