package com.zerostudio.cloudreve.feature.preview

import android.os.Bundle
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap

private const val EXTRA_LYRIC_STARTS = "cloudreve.music.live_update.lyric.STARTS"
private const val EXTRA_LYRIC_TEXTS = "cloudreve.music.live_update.lyric.TEXTS"

internal data class MusicLiveUpdateLyricLine(
    val startMs: Int,
    val text: String,
)

internal object MusicLiveUpdateLyricsRegistry {
    private val lyricsByMediaId = ConcurrentHashMap<String, List<MusicLiveUpdateLyricLine>>()

    fun put(mediaId: String, lyrics: SyncedLyrics?) {
        val lines = lyrics.toMusicLiveUpdateLyricLines()
        if (lines.isEmpty()) {
            lyricsByMediaId.remove(mediaId)
        } else {
            lyricsByMediaId[mediaId] = lines
        }
    }

    fun get(mediaId: String?): List<MusicLiveUpdateLyricLine> =
        mediaId?.let(lyricsByMediaId::get).orEmpty()
}

internal fun SyncedLyrics?.toMusicLiveUpdateLyricLines(): List<MusicLiveUpdateLyricLine> {
    if (this == null || lines.isEmpty()) return emptyList()
    return lines
        .asSequence()
        .map { line ->
            MusicLiveUpdateLyricLine(
                startMs = line.start.coerceAtLeast(0),
                text = line.toMusicLiveUpdatePlainText().toLiveUpdateCleanText(),
            )
        }
        .filter { it.text.isNotBlank() }
        .distinctBy { it.startMs }
        .sortedBy { it.startMs }
        .take(MAX_LIVE_UPDATE_LYRIC_LINES)
        .toList()
}

internal fun SyncedLyrics?.toMusicLiveUpdateExtras(): Bundle {
    val compactLines = toMusicLiveUpdateLyricLines()
    if (compactLines.isEmpty()) return Bundle()
    return Bundle().apply {
        putIntArray(EXTRA_LYRIC_STARTS, compactLines.map { it.startMs }.toIntArray())
        putStringArrayList(EXTRA_LYRIC_TEXTS, ArrayList(compactLines.map { it.text }))
    }
}

internal fun Bundle?.readMusicLiveUpdateLyrics(): List<MusicLiveUpdateLyricLine> {
    if (this == null || isEmpty) return emptyList()
    val starts = getIntArray(EXTRA_LYRIC_STARTS) ?: return emptyList()
    val texts = getStringArrayList(EXTRA_LYRIC_TEXTS) ?: return emptyList()
    val count = minOf(starts.size, texts.size)
    if (count <= 0) return emptyList()
    return List(count) { index ->
        MusicLiveUpdateLyricLine(
            startMs = starts[index].coerceAtLeast(0),
            text = texts[index].orEmpty().toLiveUpdateCleanText(),
        )
    }.filter { it.text.isNotBlank() }
}

internal fun List<MusicLiveUpdateLyricLine>.currentLiveUpdateLyric(positionMs: Long): String? {
    if (isEmpty()) return null
    val position = positionMs.coerceAtLeast(0L).toInt()
    var low = 0
    var high = lastIndex
    var match = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (this[mid].startMs <= position) {
            match = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return getOrNull(match)?.text ?: firstOrNull()?.text
}

private fun ISyncedLine.toMusicLiveUpdatePlainText(): String = when (this) {
    is SyncedLine -> content
    is KaraokeLine -> syllables.joinToString(separator = "") { it.content }
    else -> ""
}

private fun String.toLiveUpdateCleanText(): String =
    replace(whitespaceRegex, " ")
        .replace('\n', ' ')
        .trim()

private val whitespaceRegex = Regex("\\s+")
private const val MAX_LIVE_UPDATE_LYRIC_LINES = 360
