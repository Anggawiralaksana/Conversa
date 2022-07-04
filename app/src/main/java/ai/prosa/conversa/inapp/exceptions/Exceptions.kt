package ai.prosa.conversa.inapp.exceptions

class ConversaInvalidCredentialsException: Exception("Invalid Credentials")
class ConversaCannotConnectException(val host: String, val port: Int): Exception("Can't connect to HOST: $host, PORT: $port")
class RoomNotFoundException : Exception("Room Not Found")
class RoomMembershipRequiredException : Exception("Not a Member of This Room")