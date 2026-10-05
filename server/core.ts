export type ByteRange = { start: number; end: number };
export function parseRange(header: string | null, size: number): ByteRange | null {
  if (!header) return null;
  const match = /^bytes=(\d*)-(\d*)$/.exec(header);
  if (!match || size <= 0 || (!match[1] && !match[2])) throw new RangeError("range");
  let start: number, end: number;
  if (!match[1]) {
    const suffix = Number(match[2]);
    if (!Number.isSafeInteger(suffix) || suffix <= 0) throw new RangeError("range");
    start = Math.max(0, size - suffix);
    end = size - 1;
  } else {
    start = Number(match[1]);
    end = match[2] ? Math.min(Number(match[2]), size - 1) : size - 1;
  }
  if (
    !Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start < 0 || start >= size ||
    end < start
  ) throw new RangeError("range");
  return { start, end };
}
export type SourceRecord = {
  id: string;
  path: string;
  filename: string;
  bytes: number;
  modified: number;
  group: string;
  title: string;
  creator: string;
  durationMs: number;
  chapters: { title: string; startMs: number; endMs: number }[];
};
export function uniqueCatalog(records: SourceRecord[]): SourceRecord[] {
  return [...new Map(records.map((record) => [record.id, record])).values()];
}
export function privateHost(host: string): boolean {
  if (host === "localhost" || host === "::1" || host === "[::1]") return true;
  const ip = host.split(".");
  if (ip.length === 4 && ip.every((n) => /^\d{1,3}$/.test(n) && Number(n) <= 255)) {
    const [a, b] = ip.map(Number);
    return a === 127 || a === 10 || (a === 192 && b === 168) || (a === 172 && b >= 16 && b <= 31) ||
      (a === 169 && b === 254);
  }
  return /^[a-f0-9:]+$/i.test(host) && /^(fc|fd|fe80:)/i.test(host) && host.includes(":");
}

/** Add all source memberships before collapsing identical content; publish relative folders only. */
export function catalogMemberships(
  records: SourceRecord[],
  libraries: { name: string; path: string }[],
): Map<string, { group: string; folder: string }[]> {
  const result = new Map<string, { group: string; folder: string }[]>();
  const seen = new Map<string, Set<string>>();
  for (const record of records) {
    const root = libraries.find((library) => library.name === record.group)?.path.replace(
      /\/$/,
      "",
    );
    if (!root || !record.path.startsWith(root + "/")) continue;
    const relative = record.path.slice(root.length + 1);
    if (relative.split("/").some((part) => part === ".." || part === ".")) continue;
    const folder = relative.includes("/") ? relative.slice(0, relative.lastIndexOf("/")) : "";
    const membership = { group: record.group, folder };
    const key = JSON.stringify(membership);
    const keys = seen.get(record.id) ?? new Set<string>();
    if (keys.has(key)) continue;
    keys.add(key);
    seen.set(record.id, keys);
    const memberships = result.get(record.id) ?? [];
    memberships.push(membership);
    result.set(record.id, memberships);
  }
  return result;
}
