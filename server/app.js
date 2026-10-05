const texts = {
  en: {
    private: "YOUR MUSIC, YOUR DEVICES",
    pairTitle: "Connect your Mac",
    pairHint: "Open this page on your Mac to get its connection key. Paste that key on your phone.",
    key: "Connection key",
    connect: "Connect",
    library: "Mac library",
    refresh: "Rescan",
    openApp: "Connect Resonance App",
    connection: "Connection details",
    address: "Mac address",
    reveal: "Show / hide",
    lanHint:
      "Use the same trusted local network. Downloads on your phone keep playing when the Mac is off.",
    title: "Title",
    length: "Length",
    actions: "Actions",
    empty: "No matching files",
    dedupeHint:
      "Identical files appear once. Download with the app to verify, resume and avoid duplicate storage. Originals stay in place.",
    streamHint: "Mac stream · Browser volume starts at 30%",
    stop: "Close",
    search: "Find music or chapters",
    stream: "Stream",
    download: "Save in app",
    fileDownload: "Download file",
    all: "All folders",
    files: "files",
    sources: "sources",
    chapters: "chapters",
    scanning: "Indexing",
    ready: "Ready",
    error: "Could not connect. Check the Mac and connection key.",
  },
  "zh-Hant": {
    private: "YOUR MUSIC, YOUR DEVICES",
    pairTitle: "連接你的 Mac",
    pairHint: "在 Mac 開啟這個頁面取得連線金鑰，再於手機貼上。",
    key: "連線金鑰",
    connect: "連接",
    library: "Mac 媒體庫",
    refresh: "重新掃描",
    openApp: "連接 Resonance App",
    connection: "連線資訊",
    address: "Mac 位址",
    reveal: "顯示／隱藏",
    lanHint: "手機與 Mac 需在同一個受信任的區域網路。Mac 關閉後，手機已下載的檔案仍可播放。",
    title: "名稱",
    length: "長度",
    actions: "操作",
    empty: "沒有符合的檔案",
    dedupeHint:
      "相同檔案只顯示一次。使用 App 下載可核對指紋、續傳並避免重複儲存；來源檔案保留原處。",
    streamHint: "Mac 串流 · 瀏覽器音量預設 30%",
    stop: "關閉",
    search: "搜尋音樂或章節",
    stream: "串流",
    download: "存入 App",
    fileDownload: "下載原檔",
    all: "全部資料夾",
    files: "個檔案",
    sources: "個來源",
    chapters: "個章節",
    scanning: "建立索引中",
    ready: "就緒",
    error: "無法連接，請檢查 Mac 與連線金鑰。",
  },
  "zh-Hans": {
    private: "YOUR MUSIC, YOUR DEVICES",
    pairTitle: "连接你的 Mac",
    pairHint: "在 Mac 打开此页面取得连接密钥，再在手机粘贴。",
    key: "连接密钥",
    connect: "连接",
    library: "Mac 媒体库",
    refresh: "重新扫描",
    openApp: "连接 Resonance App",
    connection: "连接信息",
    address: "Mac 地址",
    reveal: "显示／隐藏",
    lanHint: "手机与 Mac 需在同一个受信任的局域网。Mac 关闭后，手机已下载的文件仍可播放。",
    title: "名称",
    length: "时长",
    actions: "操作",
    empty: "没有匹配的文件",
    dedupeHint: "相同文件只显示一次。使用 App 下载可校验指纹、续传并避免重复存储；源文件保留原处。",
    streamHint: "Mac 串流 · 浏览器音量默认 30%",
    stop: "关闭",
    search: "搜索音乐或章节",
    stream: "串流",
    download: "存入 App",
    fileDownload: "下载原文件",
    all: "全部文件夹",
    files: "个文件",
    sources: "个来源",
    chapters: "个章节",
    scanning: "正在索引",
    ready: "就绪",
    error: "无法连接，请检查 Mac 与连接密钥。",
  },
};
const $ = (id) => document.getElementById(id);
let language = localStorage.getItem("resonance-language") || "zh-Hant",
  items = [],
  key = "",
  remoteBase = location.origin,
  timer;
const audio = $("audio");
audio.volume = .3;
function text(id) {
  return texts[language][id] || id;
}
function languageChanged() {
  document.documentElement.lang = language;
  for (const node of document.querySelectorAll("[data-i18n]")) {
    node.textContent = text(node.dataset.i18n);
  }
  $("search").placeholder = text("search");
  $("folder").options[0].text = text("all");
  render();
}
for (const name of ["language", "theme", "font"]) {
  const element = $(name);
  element.value = localStorage.getItem(`resonance-${name}`) ||
    (name === "language" ? language : name === "font" ? "nothing" : "system");
  if (name !== "language") document.documentElement.dataset[name] = element.value;
  element.onchange = () => {
    localStorage.setItem(`resonance-${name}`, element.value);
    if (name === "language") {
      language = element.value;
      languageChanged();
    } else document.documentElement.dataset[name] = element.value;
  };
}
function deepLink(id) {
  const link = new URL("resonance://connect");
  link.searchParams.set("base", remoteBase);
  link.searchParams.set("key", key);
  if (id) link.searchParams.set("download", id);
  return link.href;
}
async function request(path, options) {
  const response = await fetch(path, options);
  if (!response.ok) throw new Error(text("error"));
  return response.json();
}
function showError(error) {
  $("error").textContent = error.message;
  $("error").hidden = false;
}
async function load() {
  try {
    const catalog = await request("/api/catalog");
    const connection = await request("/api/connection");
    key = connection.key;
    remoteBase = connection.base;
    items = catalog.items;
    $("pairing").hidden = true;
    $("library").hidden = false;
    $("error").hidden = true;
    $("copyKey").value = key;
    $("address").value = remoteBase;
    $("connectApp").href = deepLink();
    $("status").textContent = catalog.status.active
      ? `${text("scanning")} · ${catalog.status.completed} / ${catalog.status.total}`
      : `${text("ready")} · ${items.length} ${text("files")}`;
    if (catalog.status.failures.length) showError(new Error(catalog.status.failures.join("\n")));
    render();
    clearTimeout(timer);
    if (catalog.status.active) timer = setTimeout(load, 1500);
  } catch (error) {
    showError(error);
  }
}
function duration(ms) {
  const s = Math.floor(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}
function render() {
  const q = $("search").value.trim().toLowerCase(), folder = $("folder").value;
  const visible = items.filter((item) =>
    (!folder || item.group === folder) &&
    (!q ||
      `${item.displayName?.title ?? ""} ${
        item.displayName?.subtitle ?? ""
      } ${item.title} ${item.filename} ${item.chapters.map((c) => c.title).join(" ")}`.toLowerCase()
        .includes(q))
  );
  $("count").textContent = `${visible.length} ${text("files")}`;
  $("items").replaceChildren();
  $("empty").hidden = visible.length > 0;
  for (const item of visible) {
    const row = document.createElement("tr"),
      title = document.createElement("td"),
      name = document.createElement("strong"),
      detail = document.createElement("p"),
      length = document.createElement("td"),
      actions = document.createElement("td"),
      buttons = document.createElement("div"),
      play = document.createElement("button"),
      download = document.createElement("a"),
      fileDownload = document.createElement("a");
    name.textContent = item.displayName?.title ?? item.title;
    name.title = `${item.title}\n${item.filename}`;
    detail.textContent = `${
      item.displayName?.subtitle ? item.displayName.subtitle + " · " : ""
    }${item.group} · ${(item.bytes / 1048576).toFixed(1)} MB${
      item.chapters.length ? ` · ${item.chapters.length} ${text("chapters")}` : ""
    }${item.sourceCount > 1 ? ` · ${item.sourceCount} ${text("sources")}` : ""}`;
    title.append(name, detail);
    length.textContent = duration(item.durationMs);
    play.textContent = text("stream");
    play.className = "secondary";
    play.onclick = async () => {
      audio.pause();
      audio.volume = Math.min(audio.volume, .3);
      audio.src = `/media/${item.id}`;
      $("playingTitle").textContent = item.displayName?.title ?? item.title;
      $("player").hidden = false;
      try {
        await audio.play();
      } catch (error) {
        showError(error);
      }
    };
    download.textContent = text("download");
    download.className = "button";
    download.href = deepLink(item.id);
    fileDownload.textContent = text("fileDownload");
    fileDownload.className = "button secondary";
    fileDownload.href = `/media/${item.id}?download`;
    fileDownload.download = item.filename;
    buttons.append(play, download, fileDownload);
    actions.append(buttons);
    row.append(title, length, actions);
    $("items").append(row);
  }
}
$("pairForm").onsubmit = async (event) => {
  event.preventDefault();
  try {
    await request("/api/pair", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ key: $("key").value.trim() }),
    });
    await load();
  } catch (error) {
    showError(error);
  }
};
$("reveal").onclick = () => {
  $("copyKey").type = $("copyKey").type === "password" ? "text" : "password";
};
$("search").oninput = render;
$("folder").onchange = render;
$("refresh").onclick = async () => {
  try {
    await request("/api/refresh", { method: "POST" });
    await load();
  } catch (error) {
    showError(error);
  }
};
$("stop").onclick = () => {
  audio.pause();
  audio.removeAttribute("src");
  audio.load();
  $("player").hidden = true;
};
audio.addEventListener("volumechange", () => {
  if (audio.volume > .3) audio.volume = .3;
});
audio.addEventListener("error", () => showError(new Error(text("error"))));
languageChanged();
(async () => {
  try {
    if (location.hostname === "localhost" || location.hostname === "127.0.0.1") {
      const pair = await request("/api/local-pairing");
      await request("/api/pair", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(pair),
      });
    }
    await load();
  } catch (error) {
    showError(error);
  }
})();
