package dev.resonance

internal fun libraryPreferenceText(copy: Copy, key: String): String {
    val language = copy.languageIndex
    return when (key) {
        "library" -> listOf("Library", "媒體庫", "媒体库")
        "search" -> listOf("Show search button", "顯示搜尋按鈕", "显示搜索按钮")
        "legacy" -> listOf("Show Legacy playlist", "顯示 Legacy 播放清單", "显示 Legacy 播放列表")
        "duration" -> listOf("Show duration and chapter count", "顯示時長與章節數", "显示时长与章节数")
        else -> listOf(key, key, key)
    }[language]
}
