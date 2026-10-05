package dev.resonance

/** Supplemental screen copy. Copy owns the language choice and existing IDs remain unchanged. */
fun designText(copy: Copy, key: String): String {
    val index = copy.languageIndex
    return designStrings[key]?.get(index) ?: key
}

private val designStrings =
    mapOf(
        "empty_downloads" to listOf("No offline downloads yet", "尚無離線下載", "尚无离线下载"),
        "playback" to listOf("Playback & library", "播放與媒體庫", "播放与媒体库"),
        "font_hint" to
            listOf(
                "Geist Pixel text with Doto display accents. Chinese uses the bundled pixel font. Your choice is saved on this phone.",
                "Geist Pixel 文字搭配 Doto 大型標示；中文使用內建像素字型。選擇會儲存在手機上。",
                "Geist Pixel 文字搭配 Doto 大型标示；中文使用内置像素字体。选择会保存在手机上。",
            ),
        "selected_files" to listOf("Selected files", "已選檔案", "已选文件"),
        "network" to listOf("Local network", "區域網路", "局域网"),
        "background" to listOf("Background playback", "背景播放", "后台播放"),
        "gain" to listOf("Signal gain", "訊號增益", "信号增益"),
        "animate" to listOf("Animate during playback", "播放時顯示動態", "播放时显示动态"),
        "preview" to listOf("Motion preview", "動態預覽", "动态预览"),
        "available" to listOf("Available", "目前功能", "现有功能"),
        "wishlist" to listOf("Wishlist", "願望清單", "愿望清单"),
        "sources" to listOf("File identity", "檔案識別", "文件识别"),
        "connection" to listOf("Connection", "連線", "连接"),
    )
