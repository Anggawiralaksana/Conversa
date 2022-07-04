package ai.prosa.conversa.common.utils

object Random {
    fun randomInteger(): String {
        return ((0..1000000).random() + 1).toString()
    }
}