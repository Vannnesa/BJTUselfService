package team.bjtuss.bjtuselfservice.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A small in-process read receipt cache. Coremail already marks a message as
 * read when mbox:readMessage is called, but the inbox screen keeps its old
 * snapshot. This flow lets the opened message update the list immediately,
 * before the next server refresh.
 */
object EmailReadState {
    private val _readIds = MutableStateFlow<Set<String>>(emptySet())
    val readIds = _readIds.asStateFlow()

    fun markRead(mid: String) {
        if (mid.isBlank()) return
        _readIds.value = _readIds.value + mid
    }
}
