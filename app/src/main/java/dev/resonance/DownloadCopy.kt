package dev.resonance

/** Batch download labels in the same three languages as the existing Copy surface. */
fun downloadText(copy: Copy, key: String): String {
    val index = copy.languageIndex
    return downloadStrings[key]?.get(index) ?: copy[key]
}

private val downloadStrings =
    mapOf(
        "download_all" to listOf("Download all", "下載全部", "下载全部"),
        "resume_all" to listOf("Resume all", "繼續全部下載", "继续全部下载"),
        "batch_intro" to
            listOf(
                "Save every Mac file in app storage. Originals stay on the Mac. Completed files are kept when you resume.",
                "將所有 Mac 檔案儲存到 App 私有空間。Mac 原檔保留；續傳不會重做已完成的檔案。",
                "将所有 Mac 文件保存到 App 私有空间。Mac 原文件保留；续传不会重做已完成的文件。",
            ),
        "batch_complete" to listOf("Complete", "已完成", "已完成"),
        "batch_waiting" to listOf("Waiting / active", "等待／下載中", "等待／下载中"),
        "batch_failed" to listOf("Failed", "失敗", "失败"),
        "batch_paused" to listOf("Paused", "已暫停", "已暂停"),
        "batch_bytes" to listOf("Received / total", "已接收／總量", "已接收／总量"),
        "batch_accepted" to listOf("Queue saved", "佇列已儲存", "队列已保存"),
        "batch_limit" to
            listOf(
                "Up to 10,000 waiting files; one transfer at a time. A connection failure stops the batch. Use Resume all after reconnecting.",
                "最多 10,000 個等待檔案，逐一傳輸。連線失敗會停止整批；恢復連線後按「繼續全部下載」。",
                "最多 10,000 个等待文件，逐一传输。连接失败会停止整批；恢复连接后按“继续全部下载”。",
            ),
        "download_timeout" to
            listOf(
                "Android stopped the transfer time window. Files and progress are kept. Tap Resume all to continue.",
                "Android 已結束傳輸時段。檔案與進度保留，請按「繼續全部下載」。",
                "Android 已结束传输时段。文件与进度保留，请按“继续全部下载”。",
            ),
        "download_interrupted" to
            listOf(
                "Transfer stopped. Progress is kept. Tap Resume all to continue.",
                "傳輸已停止，進度保留。請按「繼續全部下載」。",
                "传输已停止，进度保留。请按“继续全部下载”。",
            ),
        "download_start_failed" to
            listOf(
                "Queue saved, but Android could not start the transfer. Keep this page open and tap Resume all.",
                "佇列已儲存，但 Android 無法啟動傳輸。請保持此頁開啟並按「繼續全部下載」。",
                "队列已保存，但 Android 无法启动传输。请保持此页开启并按“继续全部下载”。",
            ),
        "batch_done" to
            listOf(
                "All Mac files are saved in app storage",
                "所有 Mac 檔案已儲存到 App 私有空間",
                "所有 Mac 文件已保存到 App 私有空间",
            ),
    )
