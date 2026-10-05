import type { SourceRecord } from "./core.ts";

/** Read-only filesystem/tool adapter. The catalog owns scheduling and persistence. */
export type ProbeTools = { ffprobe: string; shasum: string };

export async function discoverMedia(
  root: string,
  group: string,
  limit: number,
): Promise<{ path: string; group: string }[]> {
  const canonical = await Deno.realPath(root);
  const found: { path: string; group: string }[] = [];
  async function walk(path: string) {
    for await (const entry of Deno.readDir(path)) {
      if (entry.isSymlink || entry.name.startsWith(".")) continue;
      const next = `${path}/${entry.name}`;
      if (entry.isDirectory) await walk(next);
      else if (
        entry.isFile && /\.(mp4|m4a|mkv|webm|mp3|flac|ogg|opus|wav|aac)$/i.test(entry.name)
      ) {
        if (found.length >= limit) throw new Error(`Configured ${limit}-file limit reached`);
        const real = await Deno.realPath(next);
        if (!real.startsWith(canonical + "/")) continue;
        found.push({ path: real, group });
      }
    }
  }
  await walk(canonical);
  return found;
}
export async function inspectMedia(
  path: string,
  group: string,
  tools: ProbeTools,
): Promise<SourceRecord> {
  const before = await Deno.stat(path);
  const digest = await new Deno.Command(tools.shasum, {
    args: ["-a", "256", path],
    stdout: "piped",
    stderr: "piped",
  }).output();
  if (!digest.success) throw new Error("Fingerprint failed");
  const id = new TextDecoder().decode(digest.stdout).slice(0, 64);
  if (!/^[a-f0-9]{64}$/.test(id)) throw new Error("Invalid fingerprint");
  const child = new Deno.Command(tools.ffprobe, {
    args: ["-v", "error", "-show_format", "-show_chapters", "-of", "json", path],
    stdout: "piped",
    stderr: "piped",
  }).spawn();
  const timer = setTimeout(() => {
    try {
      child.kill("SIGTERM");
    } catch { /* already done */ }
  }, 20000);
  let out: Deno.CommandOutput;
  try {
    out = await child.output();
  } finally {
    clearTimeout(timer);
  }
  if (!out.success) throw new Error("Metadata read failed");
  const data = JSON.parse(new TextDecoder().decode(out.stdout));
  const after = await Deno.stat(path);
  if (before.size !== after.size || before.mtime?.getTime() !== after.mtime?.getTime()) {
    throw new Error("File changed during scan; refresh to retry");
  }
  const tags = data.format?.tags ?? {};
  const filename = path.split("/").at(-1)!;
  if ((data.chapters?.length ?? 0) > 10000) throw new Error("10,000-chapter limit reached");
  const chapters = (data.chapters ?? []).map((
    c: { tags?: { title?: string }; start_time: string; end_time: string },
    i: number,
  ) => ({
    title: c.tags?.title || `${i + 1}`,
    startMs: Math.round(Number(c.start_time) * 1000),
    endMs: Math.round(Number(c.end_time) * 1000),
  }));
  return {
    id,
    path,
    group,
    filename,
    bytes: after.size,
    modified: after.mtime?.getTime() ?? 0,
    title: tags.title || filename.replace(/\.[^.]+$/, ""),
    creator: tags.artist || "",
    durationMs: Math.round(Number(data.format?.duration || 0) * 1000),
    chapters,
  };
}
