package dev.resonance

import java.text.NumberFormat

internal fun refinementText(copy: Copy, key: String): String {
    val index = copy.languageIndex
    return refinementStrings[key]?.get(index) ?: key
}

internal fun percentLabel(copy: Copy, value: Float): String {
    val locale = copy.locale
    return NumberFormat.getPercentInstance(locale).format(value.coerceIn(0f, 1f).toDouble())
}

private val refinementStrings =
    mapOf(
        "appearance" to listOf("Appearance", "外觀", "外观"),
        "theme" to listOf("Theme", "主題", "主题"),
        "colors" to listOf("Colors", "配色", "配色"),
        "resonance_colors" to listOf("Resonance", "Resonance", "Resonance"),
        "material_colors" to listOf("Material You", "Material You", "Material You"),
        "color_hint" to
            listOf(
                "Use your wallpaper colors with Material You, or keep Resonance's neutral palette.",
                "Material You 使用桌布配色，也可以保留 Resonance 的中性色調。",
                "Material You 使用壁纸配色，也可以保留 Resonance 的中性色调。",
            ),
        "color_older" to
            listOf(
                "Wallpaper colors need Android 12 or later. This phone uses the Material palette.",
                "桌布配色需要 Android 12 以上；此手機會使用 Material 預設配色。",
                "壁纸配色需要 Android 12 及以上；此手机会使用 Material 默认配色。",
            ),
        "language" to listOf("Language", "語言", "语言"),
        "language_system" to listOf("System", "系統", "系统"),
        "playback" to listOf("Playback", "播放", "播放"),
        "connections" to listOf("Files and connections", "檔案與連線", "文件与连接"),
        "more" to listOf("About", "關於", "关于"),
        "volume_summary" to listOf("Maximum app volume", "App 最大音量", "App 最大音量"),
        "current_output" to listOf("Current app output", "目前 App 輸出", "当前 App 输出"),
        "animation_on" to listOf("On · follow Android", "開啟 · 遵循 Android", "开启 · 遵循 Android"),
        "animation_forced" to
            listOf("On · always animate playback", "開啟 · 播放時持續動畫", "开启 · 播放时持续动画"),
        "animation_off" to listOf("Off", "關閉", "关闭"),
        "respect_motion" to
            listOf("Follow Android reduced animations", "遵循 Android 減少動畫設定", "遵循 Android 减少动画设置"),
        "respect_motion_hint" to
            listOf(
                "Turn off to keep the waveform and player transitions animated when Android removes animations. Turning playback motion off always takes priority.",
                "關閉後，即使 Android 停用動畫，波形與播放器轉場仍會動態顯示；關閉播放動態時仍以靜止為優先。",
                "关闭后，即使 Android 停用动画，波形与播放器转场仍会动态显示；关闭播放动态时仍以静止为优先。",
            ),
        "preview_play" to
            listOf("Play music to preview its waveform here.", "播放音樂後即可在此預覽波形。", "播放音乐后即可在此预览波形。"),
        "system_motion_off" to
            listOf("Android has disabled animations.", "Android 目前已停用動畫。", "Android 当前已停用动画。"),
        "permissions_summary" to
            listOf(
                "File access, notifications and background playback",
                "檔案存取、通知與背景播放",
                "文件访问、通知与后台播放",
            ),
        "mac_summary" to
            listOf(
                "Optional · play or save files from your computer",
                "選用 · 播放或儲存電腦上的檔案",
                "可选 · 播放或保存电脑上的文件",
            ),
        "downloads_summary" to
            listOf("Manage files saved for offline playback", "管理已儲存供離線播放的檔案", "管理已保存供离线播放的文件"),
        "features_summary" to listOf("Explore features and save ideas", "查看功能與記錄想法", "查看功能与记录想法"),
        "search_hint" to listOf("Find titles, artists and chapters", "搜尋標題、創作者與章節", "搜索标题、创作者与章节"),
        "duration_hint" to
            listOf("Show more details under each title", "在標題下方顯示更多資訊", "在标题下方显示更多信息"),
        "legacy_hint" to
            listOf("Include the additional Legacy folders", "包含額外的 Legacy 資料夾", "包含额外的 Legacy 文件夹"),
        "font_pixel" to listOf("Pixel", "像素", "像素"),
        "font_clean" to listOf("Clean", "簡潔", "简洁"),
        "font_compact" to listOf("Compact pixel", "緊湊像素", "紧凑像素"),
        "font_dots" to listOf("Dots", "點陣", "点阵"),
        "browse_add" to listOf("Add music", "加入音樂", "加入音乐"),
        "browse_add_hint" to
            listOf(
                "Use + beside a title to add it without interrupting playback.",
                "點選標題旁的 +，即可加入佇列並繼續播放。",
                "点击标题旁的 +，即可加入队列并继续播放。",
            ),
    )
