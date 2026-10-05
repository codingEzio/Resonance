package dev.resonance

/** One stable ID per UI string. Languages can be added without changing callers. */
class Copy(val language: String) {
    private val index =
        when (language) {
            "zh-Hant" -> 1
            "zh-Hans" -> 2
            else -> 0
        }

    internal val languageIndex: Int
        get() = index

    internal val locale: java.util.Locale
        get() =
            when (index) {
                1 -> java.util.Locale.TAIWAN
                2 -> java.util.Locale.SIMPLIFIED_CHINESE
                else -> java.util.Locale.ENGLISH
            }

    operator fun get(id: String): String =
        strings[id]?.get(index) ?: featureStrings[id]?.get(index) ?: id

    companion object {
        private val strings =
            mapOf(
                "library" to listOf("Library", "媒體庫", "媒体库"),
                "offline" to listOf("ON DEVICE", "手機本機", "手机本机"),
                "import" to listOf("Add files", "加入檔案", "添加文件"),
                "search" to listOf("Find music or chapters", "搜尋音樂或章節", "搜索音乐或章节"),
                "empty" to listOf("Your music.\nYour space.", "你的音樂。\n你的空間。", "你的音乐。\n你的空间。"),
                "empty_hint" to
                    listOf(
                        "Choose audio or video files stored on this phone. Originals stay where they are.",
                        "選取已存於手機的音訊或影片。原始檔案會留在原處。",
                        "选择已存于手机的音频或视频。原始文件会留在原处。",
                    ),
                "settings" to listOf("Settings", "設定", "设置"),
                "back" to listOf("Back", "返回", "返回"),
                "play" to listOf("Play", "播放", "播放"),
                "pause" to listOf("Pause", "暫停", "暂停"),
                "playback_position" to listOf("Playback position", "播放位置", "播放位置"),
                "previous" to listOf("Previous track", "上一首", "上一首"),
                "next" to listOf("Next track", "下一首", "下一首"),
                "play_all" to listOf("Play all", "播放全部", "播放全部"),
                "queue" to listOf("Queue", "播放佇列", "播放队列"),
                "enqueue" to listOf("Add to queue", "加入播放佇列", "加入播放队列"),
                "now" to listOf("Now playing", "正在播放", "正在播放"),
                "chapters" to listOf("Chapters", "章節", "章节"),
                "no_chapters" to listOf("No chapter times found", "尚無章節時間", "尚无章节时间"),
                "no_chapters_hint" to
                    listOf(
                        "This file can play in full. Song names alone cannot establish chapter times.",
                        "可以完整播放此檔案；只有曲名時，無法確定章節時間。",
                        "可以完整播放此文件；只有曲名时，无法确定章节时间。",
                    ),
                "chapter" to listOf("Chapter", "章節", "章节"),
                "add_chapter" to listOf("Mark a chapter", "新增章節標記", "添加章节标记"),
                "title" to listOf("Title", "名稱", "名称"),
                "time" to
                    listOf(
                        "Start time (m:ss or h:mm:ss)",
                        "開始時間（m:ss 或 h:mm:ss）",
                        "开始时间（m:ss 或 h:mm:ss）",
                    ),
                "invalid_time" to
                    listOf(
                        "Enter a valid time within this file.",
                        "請輸入此檔案範圍內的有效時間。",
                        "请输入此文件范围内的有效时间。",
                    ),
                "save" to listOf("Save", "儲存", "保存"),
                "cancel" to listOf("Cancel", "取消", "取消"),
                "queued" to listOf("Waiting to read metadata", "等待讀取中繼資料", "等待读取元数据"),
                "running" to listOf("Reading metadata", "讀取中繼資料中", "正在读取元数据"),
                "failed" to listOf("Could not read this file", "無法讀取此檔案", "无法读取此文件"),
                "cancelled" to listOf("Import cancelled", "已取消匯入", "已取消导入"),
                "retry" to listOf("Retry", "重試", "重试"),
                "importing" to listOf("Indexing files", "正在建立索引", "正在建立索引"),
                "cancel_import" to listOf("Cancel pending", "取消待處理項目", "取消待处理项目"),
                "permission" to
                    listOf(
                        "Access expired. Add this file again to grant access.",
                        "存取權限已失效，請重新加入此檔案以授權。",
                        "访问权限已失效，请重新添加此文件以授权。",
                    ),
                "unreadable" to
                    listOf(
                        "The file is missing, unsupported, or did not respond. Check the original, then retry.",
                        "檔案遺失、格式不支援或讀取逾時。請檢查原檔後重試。",
                        "文件丢失、格式不支持或读取超时。请检查原文件后重试。",
                    ),
                "capacity" to
                    listOf(
                        "Up to 10,000 pending files or queue tracks. Finish or cancel the current batch first.",
                        "最多可有 10,000 個待處理檔案或播放項目。請先完成或取消目前批次。",
                        "最多可有 10,000 个待处理文件或播放项目。请先完成或取消当前批次。",
                    ),
                "local_only" to
                    listOf("Choose files stored on this phone.", "請選取已存於手機的檔案。", "请选择已存于手机的文件。"),
                "import_failed" to
                    listOf(
                        "Import was not accepted. Check file access and try again.",
                        "未接受此次匯入，請檢查檔案存取權限後重試。",
                        "未接受此次导入，请检查文件访问权限后重试。",
                    ),
                "playback_error" to
                    listOf(
                        "Playback failed. Check file access or audio format.",
                        "播放失敗，請檢查檔案存取權限或音訊格式。",
                        "播放失败，请检查文件访问权限或音频格式。",
                    ),
                "theme" to listOf("Appearance", "外觀", "外观"),
                "system" to listOf("System", "跟隨系統", "跟随系统"),
                "light" to listOf("Light", "淺色", "浅色"),
                "dark" to listOf("Dark", "深色", "深色"),
                "font" to listOf("Typography", "字型", "字体"),
                "language" to listOf("Language", "語言", "语言"),
                "system_font" to listOf("System Sans + Mono", "系統無襯線＋等寬", "系统无衬线＋等宽"),
                "source_tag" to listOf("Embedded creator tag", "檔案內的創作者標記", "文件内的创作者标记"),
                "source_note" to
                    listOf(
                        "This may name the uploader, not the composer.",
                        "此標記可能是上傳者，未必是作曲家。",
                        "此标记可能是上传者，未必是作曲家。",
                    ),
                "details" to listOf("File details", "檔案資訊", "文件信息"),
                "embedded" to listOf("From file", "檔案內嵌", "文件内嵌"),
                "description" to listOf("From timestamps", "描述時間碼", "描述时间码"),
                "manual" to listOf("Your marker", "手動標記", "手动标记"),
                "licenses" to listOf("Font licenses", "字型授權", "字体许可"),
                "about" to
                    listOf(
                        "Resonance ${BuildConfig.VERSION_NAME} · Offline audio",
                        "Resonance ${BuildConfig.VERSION_NAME} · 離線音樂",
                        "Resonance ${BuildConfig.VERSION_NAME} · 离线音乐",
                    ),
                "nothing_hint" to
                    listOf(
                        "Nothing uses Geist, Geist Mono and Doto. Compact includes pixel Chinese; other presets use the phone’s Chinese font.",
                        "Nothing 使用 Geist、Geist Mono 與 Doto。Compact 內含像素中文字型；其他選項使用手機中文字型。",
                        "Nothing 使用 Geist、Geist Mono 与 Doto。Compact 内含像素中文字体；其他选项使用手机中文字体。",
                    ),
                "empty_queue" to listOf("Choose something to play", "選取想播放的音樂", "选择想播放的音乐"),
                "close" to listOf("Close", "關閉", "关闭"),
                "files" to listOf("files", "個檔案", "个文件"),
                "matches" to listOf("No matches", "沒有符合的項目", "没有符合的项目"),
            )
    }
}
