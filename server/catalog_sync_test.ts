import { type CatalogSource, ShelfCatalog } from "./catalog.ts";
import type { SourceRecord } from "./core.ts";
import { discoverMedia } from "./media-source.ts";

const record = (name: string): SourceRecord => ({
  id: name.padEnd(64, "0"),
  path: `/fixture/${name}.mp4`,
  filename: `${name}.mp4`,
  bytes: 10,
  modified: 1,
  group: "collection",
  title: name,
  creator: "",
  durationMs: 1,
  chapters: [],
});
function assert(value: unknown, message: string) {
  if (!value) throw new Error(message);
}
Deno.test("failed and changing scans preserve the last complete snapshot", async () => {
  const original = [record("a"), record("b")];
  let saved = original.slice();
  let failures = false;
  let changed = false;
  let reads = 0;
  const source: CatalogSource = {
    discover: async () => {
      reads++;
      return (changed && reads % 2 === 0 ? original.slice(0, 1) : original).map((
        { path, group },
      ) => ({ path, group }));
    },
    info: async (path) => {
      if (failures && path.endsWith("b.mp4")) throw new Error("unreadable");
      return { size: 10, modified: 1 };
    },
    inspect: async () => {
      throw new Error("unexpected inspection");
    },
  };
  const store = {
    load: async () => saved.slice(),
    save: async (rows: SourceRecord[]) => {
      saved = rows.slice();
    },
  };
  const catalog = new ShelfCatalog(
    [{ name: "collection", path: "/fixture", role: "collections" }],
    100,
    source,
    store,
  );
  await catalog.restore();
  await catalog.scan();
  assert(JSON.parse(catalog.text).items.length === 2, "baseline missing");
  const explicitlyRetired = "f".repeat(64);
  catalog.updateDisplayNames(new Map(), "source-id", {}, [explicitlyRetired]);
  assert(
    JSON.parse(catalog.text).retiredHashes[0] === explicitlyRetired,
    "retirement list missing",
  );
  failures = true;
  await catalog.scan();
  assert(
    saved.length === 2 && JSON.parse(catalog.text).items.length === 2,
    "failure published a partial catalog",
  );
  assert(catalog.status.failures.length === 1 && !catalog.status.active, "failure not surfaced");
  failures = false;
  changed = true;
  reads = 0;
  await catalog.scan();
  assert(
    saved.length === 2 && catalog.status.failures.length === 1,
    "changing scan became authoritative",
  );
});
Deno.test("discovery includes only MP4 files", async () => {
  const root = await Deno.makeTempDir();
  try {
    await Deno.writeTextFile(`${root}/a.MP4`, "x");
    await Deno.writeTextFile(`${root}/b.mp3`, "x");
    await Deno.writeTextFile(`${root}/README.md`, "x");
    const found = await discoverMedia(root, "collection", 10);
    assert(found.length === 1 && found[0].path.endsWith("a.MP4"), "unexpected source files");
  } finally {
    await Deno.remove(root, { recursive: true });
  }
});
