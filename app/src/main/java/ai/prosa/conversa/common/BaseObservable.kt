package ai.prosa.conversa.common

import java.util.*
import java.util.concurrent.ConcurrentHashMap

open class BaseObservable<LISTENER_CLASS> {
    private val mListeners = Collections.newSetFromMap(
        ConcurrentHashMap<LISTENER_CLASS, Boolean>(1)
    )

    fun registerListener(listener: LISTENER_CLASS) {
        mListeners.add(listener)
    }

    fun unregisterListener(listener: LISTENER_CLASS) {
        mListeners.remove(listener)
    }

    protected val listeners: Set<LISTENER_CLASS>
        get() = Collections.unmodifiableSet(mListeners)
}