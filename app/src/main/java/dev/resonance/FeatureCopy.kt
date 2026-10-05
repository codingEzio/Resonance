package dev.resonance

internal val featureStrings =
    mapOf(
        "original_metadata" to listOf("Original title & file", "原始標題與檔案", "原始标题与文件"),
        "paused" to listOf("PAUSED", "已暫停", "已暂停"),
        "playing_state" to listOf("PLAYING", "播放中", "播放中"),
        "up_next" to listOf("Up next", "接著播放", "接下来播放"),
        "hide_queue" to listOf("Hide queue", "收合佇列", "收起队列"),
        "personal_library" to listOf("PERSONAL LIBRARY", "私人媒體庫", "私人媒体库"),
        "mac" to listOf("Mac library", "Mac 媒體庫", "Mac 媒体库"),
        "mac_address" to
            listOf("Enter a private LAN address and port.", "請輸入區域網路位址與連接埠。", "请输入局域网地址与端口。"),
        "mac_key" to listOf("Check the Mac connection key.", "請檢查 Mac 連線金鑰。", "请检查 Mac 连接密钥。"),
        "mac_unavailable" to
            listOf(
                "Mac is unavailable. Check power, Wi-Fi and local network access. Offline files still work.",
                "無法連接 Mac。請檢查電源、Wi-Fi 與區域網路權限；離線檔案仍可播放。",
                "无法连接 Mac。请检查电源、Wi-Fi 与局域网权限；离线文件仍可播放。",
            ),
        "mac_connected" to listOf("Mac catalog is up to date", "Mac 目錄已更新", "Mac 目录已更新"),
        "mac_intro" to
            listOf(
                "Connect to the Mac on the same trusted Wi-Fi. Your catalog stays here; streaming needs the Mac, offline copies do not.",
                "在同一個受信任的 Wi-Fi 連接 Mac。目錄會留在手機；串流需要 Mac，離線檔案不需要。",
                "在同一个受信任的 Wi-Fi 连接 Mac。目录会留在手机；串流需要 Mac，离线文件不需要。",
            ),
        "address" to listOf("Mac address", "Mac 位址", "Mac 地址"),
        "connection_key" to listOf("Connection key", "連線金鑰", "连接密钥"),
        "connect" to listOf("Connect and read catalog", "連接並讀取目錄", "连接并读取目录"),
        "connecting" to listOf("Connecting…", "連接中…", "正在连接…"),
        "open_shelf" to listOf("Open Mac web page", "開啟 Mac 網頁", "打开 Mac 网页"),
        "refresh_mac" to listOf("Refresh Mac catalog", "更新 Mac 目錄", "更新 Mac 目录"),
        "permissions" to listOf("Access and permissions", "存取與權限", "访问与权限"),
        "permissions_intro" to
            listOf(
                "Check access here before using a feature. Android asks only when that feature needs permission.",
                "使用功能前，可在這裡檢查存取狀態。需要權限時才會提出 Android 系統請求。",
                "使用功能前，可在这里检查访问状态。需要权限时才会提出 Android 系统请求。",
            ),
        "file_access" to listOf("Local files", "本機檔案", "本地文件"),
        "file_access_hint" to
            listOf(
                "The system picker grants access only to files you select. No access to all files is needed.",
                "系統選擇器只授權你選取的檔案，不需要「所有檔案」權限。",
                "系统选择器只授权你选择的文件，不需要“所有文件”权限。",
            ),
        "lan_access" to listOf("Local network", "區域網路", "局域网"),
        "lan_permission" to
            listOf(
                "Allow local network access before connecting to your Mac.",
                "連接 Mac 前，請先允許區域網路存取。",
                "连接 Mac 前，请先允许局域网访问。",
            ),
        "notifications" to listOf("Download notifications", "下載通知", "下载通知"),
        "notification_hint" to
            listOf(
                "Shows download progress outside the app. Media playback controls have their own Android exemption.",
                "離開 App 時顯示下載進度；媒體播放控制適用 Android 的獨立例外規則。",
                "离开 App 时显示下载进度；媒体播放控制适用 Android 的独立豁免规则。",
            ),
        "granted" to listOf("Allowed", "已允許", "已允许"),
        "not_granted" to listOf("Not allowed", "尚未允許", "尚未允许"),
        "request" to listOf("Request access", "要求存取權", "请求访问权"),
        "system_settings" to listOf("Open Android settings", "開啟 Android 設定", "打开 Android 设置"),
        "battery" to listOf("Background playback", "背景播放", "后台播放"),
        "battery_hint" to
            listOf(
                "Playback uses a foreground media service. If this phone stops background audio, review the app battery setting in Android.",
                "播放使用前景媒體服務。若手機中止背景音訊，可在 Android 檢查此 App 的電池設定。",
                "播放使用前台媒体服务。若手机中止后台音频，可在 Android 检查此 App 的电池设置。",
            ),
        "volume" to listOf("Maximum volume", "最大音量", "最大音量"),
        "output_limit" to listOf("Maximum app volume", "App 最大音量", "App 最大音量"),
        "volume_hint" to
            listOf(
                "Applied before playback, on resume and to media-controller requests. Percentage is signal gain, not a guarantee of sound pressure; headphones and source levels still matter.",
                "播放前、恢復播放及媒體控制請求都會受限制。百分比表示訊號增益，不能保證實際音壓；耳機與來源音量仍會影響大小。",
                "播放前、恢复播放及媒体控制请求均受限制。百分比表示信号增益，不能保证实际声压；耳机与源音量仍会影响大小。",
            ),
        "system_guard" to
            listOf("Also lower Android media volume", "同時調低 Android 媒體音量", "同时调低 Android 媒体音量"),
        "system_guard_hint" to
            listOf(
                "While playing, lower shared media volume to this ceiling. This also affects other apps. Android cannot lock hardware volume keys; this extra guard is best effort. Volume is never raised automatically.",
                "播放時將共用媒體音量調低至上限，也會影響其他 App。Android 無法鎖住硬體音量鍵，因此這層保護只能盡力限制；不會自動調高音量。",
                "播放时将共享媒体音量调低至上限，也会影响其他 App。Android 无法锁住硬件音量键，因此这层保护只能尽力限制；不会自动调高音量。",
            ),
        "motion" to listOf("Playback motion", "播放動態", "播放动态"),
        "motion_hint" to
            listOf(
                "Music energy shapes the flowing dots. Quiet music stays fluid; silence fades to stillness. Playback motion pauses when music is paused or the app is hidden.",
                "音樂能量帶動點陣起伏，輕柔段落持續流動，靜音逐漸停下。暫停播放或 App 在背景時，播放動態保持靜止。",
                "音乐能量带动点阵起伏，轻柔段落持续流动，静音逐渐停下。暂停播放或 App 在后台时，播放动态保持静止。",
            ),
        "features" to listOf("Features and wishlist", "功能與願望清單", "功能与愿望清单"),
        "available_features" to listOf("Available here", "目前功能", "现有功能"),
        "wish" to listOf("Something you want next", "下一個想要的功能", "下一个想要的功能"),
        "add_wish" to listOf("Add to wishlist", "加入願望清單", "添加到愿望清单"),
        "feature_local" to listOf("Local audio and video playback", "本機音訊與影片播放", "本地音频与视频播放"),
        "feature_chapters" to listOf("Metadata and chapter navigation", "中繼資料與章節跳轉", "元数据与章节跳转"),
        "feature_dedupe" to
            listOf("One media item for identical files", "相同檔案共用一筆媒體", "相同文件共用一条媒体"),
        "feature_mac" to
            listOf(
                "Mac streaming and verified offline downloads",
                "Mac 串流與驗證後離線儲存",
                "Mac 串流与验证后离线保存",
            ),
        "feature_safety" to
            listOf("Output ceiling and permission status", "音量上限與權限狀態", "音量上限与权限状态"),
        "feature_style" to
            listOf("Fonts, themes and optional dot motion", "字型、主題與可選點陣動態", "字体、主题与可选点阵动态"),
        "on_device" to listOf("On device", "已在手機", "已在手机"),
        "on_mac" to listOf("Mac stream", "Mac 串流", "Mac 串流"),
        "all_sources" to listOf("All", "全部", "全部"),
        "source_count" to listOf("sources · one media item", "個來源 · 一筆媒體", "个来源 · 一条媒体"),
        "download" to listOf("Save offline", "儲存供離線播放", "保存供离线播放"),
        "downloads" to listOf("Offline downloads", "離線下載", "离线下载"),
        "download_queued" to listOf("Download queued", "下載已排入佇列", "下载已排入队列"),
        "download_running" to listOf("Downloading and verifying", "下載與驗證中", "正在下载和验证"),
        "download_failed" to
            listOf(
                "Download stopped. Retry resumes the partial file.",
                "下載中斷，重試會從暫存進度繼續。",
                "下载中断，重试会从临时进度继续。",
            ),
        "download_paused" to listOf("Download paused", "下載已暫停", "下载已暂停"),
        "download_complete" to listOf("Verified on this phone", "已在手機完成驗證", "已在手机完成验证"),
        "integrity" to
            listOf(
                "File verification failed. Retry downloads a fresh copy.",
                "檔案驗證失敗，重試會重新下載。",
                "文件校验失败，重试将重新下载。",
            ),
        "storage_full" to listOf("Not enough free phone storage.", "手機可用儲存空間不足。", "手机可用存储空间不足。"),
        "no_remote" to listOf("This file has no Mac source.", "此檔案沒有 Mac 來源。", "此文件没有 Mac 来源。"),
        "identity_hint" to
            listOf(
                "SHA-256 combines byte-identical files across sources. Names alone never merge files. Originals and differently encoded versions are kept.",
                "以 SHA-256 合併各來源中位元完全相同的檔案，不會只靠檔名合併。原檔與不同編碼版本會保留。",
                "通过 SHA-256 合并各来源中字节完全相同的文件，不会只按文件名合并。原文件与不同编码版本会保留。",
            ),
        "verify_local" to listOf("Verifying file identity", "核對檔案指紋", "核对文件指纹"),
    )
