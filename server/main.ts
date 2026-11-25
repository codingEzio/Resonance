import { pathToFileURL } from "node:url";
import { parseRange, privateHost, type SourceRecord } from "./core.ts";
import { loadShelfConfig, projectFile } from "./config.ts";
import { ShelfCatalog } from "./catalog.ts";
import { FileCatalogStore } from "./catalog-store.ts";
import { discoverMedia, fileIdentity, inspectMedia } from "./media-source.ts";
import { loadOrCreateKey, lockProfile, requireLockSupport } from "./profile.ts";

requireLockSupport();
const config = await loadShelfConfig();
const port = config.port;
const stateDirectory = pathToFileURL(config.stateDirectory + "/");
await Deno.mkdir(stateDirectory, { recursive: true, mode: 0o700 });
// Keep the open lock alive for the lifetime of the server process.
const profileLock = await lockProfile(stateDirectory);
const secret = await loadOrCreateKey(stateDirectory);
const identityKey = await crypto.subtle.importKey(
  "raw",
  new TextEncoder().encode(secret),
  { name: "HMAC", hash: "SHA-256" },
  false,
  ["sign"],
);
const lanAddresses = Deno.networkInterfaces().filter((n) =>
  n.family === "IPv4" && privateHost(n.address) && !n.address.startsWith("127.") &&
  !n.address.startsWith("169.254.")
).sort((a, b) => Number(b.name.startsWith("en")) - Number(a.name.startsWith("en"))).map((n) =>
  `http://${n.address}:${port}`
);
let activeStreams = 0;
const html = await Deno.readTextFile(new URL("./index.html", import.meta.url));
const css = await Deno.readTextFile(new URL("./style.css", import.meta.url));
const script = await Deno.readTextFile(new URL("./app.js", import.meta.url));
async function readDisplayNames() {
  const library = config.libraries.length === 1 && config.libraries[0].role === "collections"
    ? config.libraries[0]
    : null;
  let content: string | null = null;
  let sourceSidecarLoaded = false;
  if (library) {
    try {
      content = await Deno.readTextFile(`${library.path}/.resonance/display-names.json`);
      sourceSidecarLoaded = true;
    } catch (error) {
      if (!(error instanceof Deno.errors.NotFound)) throw error;
    }
  }
  if (content === null && config.displayNames) {
    content = await Deno.readTextFile(projectFile(config.displayNames));
  }
  const data = content === null ? { items: [] } : JSON.parse(content);
  const rows = data.items;
  if (!Array.isArray(rows)) throw new Error("Invalid display name catalog");
  const names = new Map<string, { title: string; subtitle: string }>();
  for (const row of rows) {
    if (
      !/^[a-f0-9]{64}$/.test(row.sha256) || typeof row.title !== "string" ||
      typeof (row.subtitle ?? "") !== "string"
    ) throw new Error("Invalid display name entry");
    names.set(row.sha256, { title: row.title, subtitle: row.subtitle ?? "" });
  }
  const sourceId = sourceSidecarLoaded && typeof data.sourceId === "string" &&
      /^[a-f0-9-]{36}$/.test(data.sourceId)
    ? data.sourceId
    : null;
  const collectionNames: Record<string, string> = {};
  if (
    data.collections && typeof data.collections === "object" && !Array.isArray(data.collections)
  ) {
    for (const [name, title] of Object.entries(data.collections)) {
      if (typeof title === "string") collectionNames[name] = title;
    }
  }
  const retiredHashes = sourceSidecarLoaded ? data.retiredHashes ?? [] : [];
  if (
    !Array.isArray(retiredHashes) || retiredHashes.length > config.maxFiles ||
    !retiredHashes.every((hash: unknown) => typeof hash === "string" && /^[a-f0-9]{64}$/.test(hash))
  ) {
    throw new Error("Invalid retired hash list");
  }
  return { names, sourceId, collectionNames, retiredHashes: [...new Set<string>(retiredHashes)] };
}
const displayNames = await readDisplayNames();
const fonts = new Map<string, Uint8Array>();
for (const name of ["geist", "geist_mono", "doto", "compact"]) {
  fonts.set(name, await Deno.readFile(projectFile(`app/src/main/res/font/${name}.ttf`)));
}
const catalog = new ShelfCatalog(
  config.libraries,
  config.maxFiles,
  {
    discover: discoverMedia,
    info: async (path) => {
      const stat = await Deno.stat(path);
      return {
        size: stat.size,
        modified: stat.mtime?.getTime() ?? 0,
        fileIdentity: fileIdentity(stat),
      };
    },
    inspect: (path, group) => inspectMedia(path, group, config),
  },
  new FileCatalogStore(stateDirectory),
  displayNames.names,
);
await catalog.restore();
catalog.updateDisplayNames(
  displayNames.names,
  displayNames.sourceId,
  displayNames.collectionNames,
  displayNames.retiredHashes,
);
function authenticated(req: Request) {
  const bearer = req.headers.get("authorization");
  const cookie = req.headers.get("cookie")?.split(";").map((s) => s.trim()).find((s) =>
    s.startsWith("resonance=")
  )?.slice(10);
  return bearer === `Bearer ${secret}` || cookie === secret;
}
function json(data: unknown, code = 200, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(data), {
    status: code,
    headers: { "content-type": "application/json", "cache-control": "no-store", ...headers },
  });
}
function sameOrigin(req: Request, url: URL) {
  const origin = req.headers.get("origin");
  return !origin || origin === url.origin;
}
async function media(req: Request, record: SourceRecord) {
  if (await Deno.realPath(record.path) !== record.path) {
    return json({ error: "Source path changed. Refresh the library." }, 409);
  }
  const stat = await Deno.stat(record.path);
  if (
    stat.size !== record.bytes || stat.mtime?.getTime() !== record.modified ||
    fileIdentity(stat) !== record.fileIdentity
  ) {
    void catalog.scan();
    return json({ error: "Source changed. Refresh the library." }, 409);
  }
  const etag = `"${record.id}"`;
  let range;
  try {
    range = parseRange(
      req.headers.get("if-range") && req.headers.get("if-range") !== etag
        ? null
        : req.headers.get("range"),
      record.bytes,
    );
  } catch {
    return new Response(null, {
      status: 416,
      headers: { "content-range": `bytes */${record.bytes}` },
    });
  }
  const start = range?.start ?? 0, end = range?.end ?? record.bytes - 1;
  const headers: Record<string, string> = {
    "accept-ranges": "bytes",
    "content-length": String(end - start + 1),
    etag,
    "content-type": /\.mp4$/i.test(record.filename) ? "video/mp4" : "application/octet-stream",
    "cache-control": "private, no-cache",
  };
  if (new URL(req.url).searchParams.has("download")) {
    headers["content-disposition"] = `attachment; filename*=UTF-8''${
      encodeURIComponent(record.filename)
    }`;
  }
  if (range) headers["content-range"] = `bytes ${start}-${end}/${record.bytes}`;
  if (req.method === "HEAD") return new Response(null, { status: range ? 206 : 200, headers });
  if (activeStreams >= config.maxStreams) {
    return json(
      { error: `The configured ${config.maxStreams} transfers are already active. Retry shortly.` },
      429,
      {
        "retry-after": "3",
      },
    );
  }
  const file = await Deno.open(record.path, { read: true });
  await file.seek(start, Deno.SeekMode.Start);
  let remaining = end - start + 1, closed = false;
  activeStreams++;
  function close() {
    if (!closed) {
      closed = true;
      activeStreams--;
      try {
        file.close();
      } catch { /* already closed */ }
    }
  }
  const body = new ReadableStream<Uint8Array>({
    async pull(controller) {
      try {
        const buffer = new Uint8Array(Math.min(65536, remaining));
        const count = await file.read(buffer);
        if (count === null || count === 0) {
          close();
          controller.close();
          return;
        }
        remaining -= count;
        controller.enqueue(buffer.subarray(0, count));
        if (remaining <= 0) {
          close();
          controller.close();
        }
      } catch (error) {
        close();
        controller.error(error);
      }
    },
    cancel() {
      close();
    },
  });
  return new Response(body, { status: range ? 206 : 200, headers });
}
const server = Deno.serve({
  hostname: config.bind,
  port,
  onListen: ({ hostname }) => console.log(`Resonance shelf listening on ${hostname}:${port}`),
}, async (req, info) => {
  const url = new URL(req.url);
  if (!privateHost(url.hostname) || !privateHost(info.remoteAddr.hostname)) {
    return json({ error: "Local network only" }, 403);
  }
  if (!sameOrigin(req, url)) return json({ error: "Origin not allowed" }, 403);
  try {
    if (req.method === "GET" && url.pathname === "/") {
      return new Response(html, {
        headers: {
          "content-type": "text/html; charset=utf-8",
          "referrer-policy": "no-referrer",
          "content-security-policy":
            "default-src 'self'; media-src 'self'; style-src 'self'; font-src 'self'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'",
        },
      });
    }
    if (req.method === "GET" && url.pathname === "/style.css") {
      return new Response(css, { headers: { "content-type": "text/css" } });
    }
    if (req.method === "GET" && url.pathname === "/app.js") {
      return new Response(script, { headers: { "content-type": "text/javascript" } });
    }
    if (req.method === "GET" && url.pathname.startsWith("/fonts/")) {
      const font = fonts.get(url.pathname.slice(7).replace(/\.ttf$/, ""));
      return font
        ? new Response(new Uint8Array(font), {
          headers: { "content-type": "font/ttf", "cache-control": "public, max-age=86400" },
        })
        : new Response(null, { status: 404 });
    }
    if (req.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "Resonance", indexing: catalog.status.active });
    }
    if (req.method === "GET" && url.pathname === "/api/identity") {
      const challenge = url.searchParams.get("challenge") ?? "";
      if (!/^[a-f0-9]{32}$/.test(challenge)) return json({ error: "Invalid challenge" }, 400);
      const signed = await crypto.subtle.sign(
        "HMAC",
        identityKey,
        new TextEncoder().encode(challenge),
      );
      const proof = [...new Uint8Array(signed)].map((n) => n.toString(16).padStart(2, "0")).join(
        "",
      );
      return json({ proof }, 200, { "cache-control": "no-store" });
    }
    if (req.method === "GET" && url.pathname === "/api/local-pairing") {
      if (Deno.env.get("RESONANCE_USB_SAFE") === "1") {
        return json({ error: "Pair locally before starting USB sync" }, 403);
      }
      return info.remoteAddr.hostname === "127.0.0.1" || info.remoteAddr.hostname === "::1"
        ? json({ key: secret })
        : json({ error: "Open this page on the Mac first" }, 403);
    }
    if (req.method === "POST" && url.pathname === "/api/pair") {
      if (Number(req.headers.get("content-length") ?? 0) > 1024) {
        return json({ error: "Invalid request" }, 413);
      }
      const reader = req.body?.getReader();
      const bytes = new Uint8Array(1024);
      let length = 0;
      if (reader) {
        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            if (length + value.byteLength > bytes.length) {
              await reader.cancel();
              return json({ error: "Invalid request" }, 413);
            }
            bytes.set(value, length);
            length += value.byteLength;
          }
        } finally {
          reader.releaseLock();
        }
      }
      const body = new TextDecoder().decode(bytes.subarray(0, length));
      const key = JSON.parse(body).key;
      if (key !== secret) return json({ error: "Connection key does not match" }, 401);
      return json({ ok: true }, 200, {
        "set-cookie": `resonance=${secret}; HttpOnly; SameSite=Strict; Path=/; Max-Age=31536000`,
      });
    }
    if (!authenticated(req)) return json({ error: "Pair with this Mac to open its library" }, 401);
    if (req.method === "GET" && url.pathname === "/api/catalog") {
      const names = await readDisplayNames();
      catalog.updateDisplayNames(
        names.names,
        names.sourceId,
        names.collectionNames,
        names.retiredHashes,
      );
      return new Response(catalog.text, {
        headers: { "content-type": "application/json", "cache-control": "no-store" },
      });
    }
    if (req.method === "POST" && url.pathname === "/api/sync-catalog") {
      if (catalog.status.active) return json({ error: "Scan in progress" }, 409);
      await catalog.scan();
      const names = await readDisplayNames();
      catalog.updateDisplayNames(
        names.names,
        names.sourceId,
        names.collectionNames,
        names.retiredHashes,
      );
      if (catalog.status.active || catalog.status.failures.length) {
        return json({ error: "Scan incomplete" }, 409);
      }
      return new Response(catalog.text, {
        headers: { "content-type": "application/json", "cache-control": "no-store" },
      });
    }
    if (req.method === "POST" && url.pathname === "/api/refresh") {
      void catalog.scan();
      return json({ accepted: true }, 202);
    }
    if (req.method === "GET" && url.pathname === "/api/connection") {
      return json({
        key: secret,
        base: (url.hostname === "localhost" || url.hostname === "127.0.0.1")
          ? lanAddresses[0] || url.origin
          : url.origin,
        addresses: lanAddresses,
      });
    }
    const match = /^\/media\/([a-f0-9]{64})$/.exec(url.pathname);
    if (match && (req.method === "GET" || req.method === "HEAD")) {
      const item = catalog.find(match[1]);
      return item
        ? await media(req, item)
        : json({ error: "File is no longer in this library" }, 404);
    }
    return json({ error: "Not found" }, 404);
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    return json({ error: "Request failed. Check the Mac and retry." }, 500);
  }
});
void catalog.scan();
let refresh: ReturnType<typeof setTimeout> | undefined;
const watcher = config.libraries.length ? Deno.watchFs(config.libraries.map((r) => r.path)) : null;
void (async () => {
  if (!watcher) return;
  for await (const _event of watcher) {
    clearTimeout(refresh);
    refresh = setTimeout(() => void catalog.scan(), 600);
  }
})();
for (const signal of ["SIGTERM", "SIGINT"] as const) {
  Deno.addSignalListener(signal, () => {
    // Retain the file until all pending work ends and the process exits.
    void profileLock;
    watcher?.close();
    clearTimeout(refresh);
    void server.shutdown();
  });
}
