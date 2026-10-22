package com.cyberalarm.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class TagKind { CODE, NFC }

/**
 * A registered jack-in point. [fingerprint] is a SHA-256 of the QR/barcode text or of the NFC tag
 * id, so the raw value (which might be a URL or something private) is never stored.
 */
data class JackInTag(val kind: TagKind, val fingerprint: String, val name: String) {
    fun encode() = "${kind.name}|$fingerprint|$name"

    companion object {
        fun decode(raw: String): JackInTag? {
            val parts = raw.split("|", limit = 3)
            if (parts.size < 3) return null
            val kind = TagKind.entries.find { it.name == parts[0] } ?: return null
            return JackInTag(kind, parts[1], parts[2])
        }

        fun fingerprint(kind: TagKind, raw: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("${kind.name}:$raw".toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

@Singleton
class JackInStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("jack_in", Context.MODE_PRIVATE)
    private val _tags = MutableStateFlow(read())
    val tags: StateFlow<List<JackInTag>> = _tags.asStateFlow()

    fun matches(kind: TagKind, raw: String): Boolean {
        val fp = JackInTag.fingerprint(kind, raw)
        return _tags.value.any { it.kind == kind && it.fingerprint == fp }
    }

    /** Returns false if that tag is already registered. */
    fun add(kind: TagKind, raw: String): Boolean {
        val fp = JackInTag.fingerprint(kind, raw)
        if (_tags.value.any { it.fingerprint == fp }) return false
        val name = "NODE_%02d".format(_tags.value.size + 1)
        save(_tags.value + JackInTag(kind, fp, name))
        return true
    }

    fun remove(tag: JackInTag) = save(_tags.value.filterNot { it.fingerprint == tag.fingerprint })

    fun replaceAll(list: List<JackInTag>) = save(list)

    private fun save(list: List<JackInTag>) {
        prefs.edit(commit = true) { putStringSet(KEY, list.map { it.encode() }.toSet()) }
        _tags.value = list.sortedBy { it.name }
    }

    private fun read(): List<JackInTag> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { JackInTag.decode(it) }.sortedBy { it.name }

    private companion object {
        const val KEY = "tags"
    }
}
