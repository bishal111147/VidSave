package com.vidsave.app

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Status { RUNNING, DONE, FAILED }

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val progress: Float = 0f,
    val status: Status = Status.RUNNING,
    val note: String = "Starting…",
    val uri: Uri? = null,
)

/** In-memory list of downloads shared between the service and the UI. */
object Downloads {
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    fun add(item: DownloadItem) = _items.update { it + item }

    fun update(id: String, change: (DownloadItem) -> DownloadItem) =
        _items.update { list -> list.map { if (it.id == id) change(it) else it } }

    fun clearFinished() = _items.update { list -> list.filter { it.status == Status.RUNNING } }
}

object Links {
    private val rx = Regex("""https?://[^\s<>"']+""")

    /** Pulls the first http(s) link out of any shared text ("Check this out! https://…"). */
    fun extract(text: String?): String? =
        text?.let { rx.find(it)?.value?.trimEnd('.', ',', ')', ']', '!', '?') }
}
