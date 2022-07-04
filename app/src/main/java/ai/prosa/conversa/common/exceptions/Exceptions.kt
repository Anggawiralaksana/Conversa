package ai.prosa.conversa.common.exceptions

class ConversaAPIFailedConnect(url: String, path: String): Exception("Can't connect to $url with PATH = $path")