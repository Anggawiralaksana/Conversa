package ai.prosa.conversa.inapp.data.model

data class InappCache(
    var userid: String,
    var name: String,
    var roomid: String,
    var password: String,
    var host: String,
    var port: Int,
    var domain: String
) {
    fun containsEmpty(): Boolean {
        return userid.isNullOrEmpty() || roomid.isNullOrEmpty() || password.isNullOrEmpty() || host.isNullOrEmpty() || domain.isNullOrEmpty()
    }
}