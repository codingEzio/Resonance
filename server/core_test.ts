import { parseRange, type SourceRecord, uniqueCatalog } from "./core.ts";
function equal(a: unknown, b: unknown) {
  if (JSON.stringify(a) !== JSON.stringify(b)) {
    throw new Error(`Expected ${JSON.stringify(b)}, got ${JSON.stringify(a)}`);
  }
}
Deno.test("range reads preserve exact inclusive byte boundaries and reject invalid requests", () => {
  equal(parseRange(null, 100), null);
  equal(parseRange("bytes=10-19", 100), { start: 10, end: 19 });
  equal(parseRange("bytes=80-", 100), { start: 80, end: 99 });
  equal(parseRange("bytes=-7", 100), { start: 93, end: 99 });
  for (const value of ["bytes=100-", "bytes=20-10", "bytes=-0", "bytes=1-2,4-5", "nonsense"]) {
    let failed = false;
    try {
      parseRange(value, 100);
    } catch {
      failed = true;
    }
    equal(failed, true);
  }
});
Deno.test("one and 10,000 retransmitted source identities produce one content item", () => {
  const source: SourceRecord = {
    id: "a".repeat(64),
    path: "/fixture/one",
    filename: "one.mp4",
    bytes: 3,
    modified: 1,
    group: "Music",
    title: "Song",
    creator: "",
    durationMs: 1000,
    chapters: [],
  };
  equal(uniqueCatalog([source]).length, 1);
  const copies = Array.from(
    { length: 10000 },
    (_, i) => ({ ...source, path: `/fixture/${i}`, filename: `copy-${i}.mp4` }),
  );
  equal(uniqueCatalog(copies).length, 1);
  equal(uniqueCatalog([...copies, { ...source, id: "b".repeat(64) }]).length, 2);
});

Deno.test("LAN host checks reject public names that resemble a private address", async () => {
  const { privateHost } = await import("./core.ts");
  for (
    const host of [
      "10.evil.example",
      "192.168.evil.example",
      "10.999.1.2",
      "8.8.8.8",
      "example.com",
    ]
  ) equal(privateHost(host), false);
  for (const host of ["192.168.1.2", "10.0.0.1", "172.16.1.2", "127.0.0.1", "localhost"]) {
    equal(privateHost(host), true);
  }
});

Deno.test("folder membership survives hash dedup without exposing absolute paths", async () => {
  const { catalogMemberships } = await import("./core.ts");
  const record = {
    id: "same",
    path: "/media/sets/Coast/one.mp4",
    group: "Collections",
  } as SourceRecord;
  const roots = [{ name: "Collections", path: "/media/sets" }, {
    name: "Media",
    path: "/media/singles",
  }];
  const result = catalogMemberships([
    record,
    record,
    { ...record, path: "/media/sets/Night/two.mp4" },
    { ...record, path: "/media/singles/one.mp4", group: "Media" },
    { ...record, path: "/media/sets-other/private.mp4" },
  ], roots);
  equal(result.get("same"), [
    { group: "Collections", folder: "Coast" },
    { group: "Collections", folder: "Night" },
    { group: "Media", folder: "" },
  ]);
});
