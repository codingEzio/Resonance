import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { FileCatalogStore } from "./catalog-store.ts";

function equal(actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error("Shelf fixture readback differs");
  }
}

Deno.test("shelf profile keeps settings, key and reopened catalog outside source and rejects linked/traversing roots", async () => {
  const base = await Deno.makeTempDir({ prefix: "resonance-profile-" });
  const checkout = join(base, "Source with spaces");
  const profile = join(base, "Private profile");
  const keys = [
    "RESONANCE_DATA",
    "RESONANCE_CONFIG",
    "RESONANCE_BIND",
    "RESONANCE_PORT",
    "PROJECT_PORT",
  ];
  const original = new Map(keys.map((key) => [key, Deno.env.get(key)]));
  try {
    for (const key of keys) Deno.env.delete(key);
    await Deno.mkdir(join(checkout, "server"), { recursive: true });
    await Deno.mkdir(profile);
    for (const name of ["config.ts", "config.json"]) {
      await Deno.copyFile(new URL("./" + name, import.meta.url), join(checkout, "server", name));
    }
    const { loadShelfConfig } = await import(
      pathToFileURL(join(checkout, "server/config.ts")).href
    );
    Deno.env.set("RESONANCE_DATA", profile);
    const settings = {
      libraries: [
        { name: "Old", path: "/fixture/old" },
        { name: "Sets", path: "/fixture/sets", role: "collections" },
      ],
      maxFiles: 27,
      stateDirectory: ".",
    };
    const settingsPath = join(profile, "config.json");
    await Deno.writeTextFile(settingsPath, JSON.stringify(settings));
    await Deno.writeTextFile(join(profile, "access-key"), "synthetic-key-only", { mode: 0o600 });
    const config = await loadShelfConfig();
    equal([config.stateDirectory, config.maxFiles, config.libraries], [
      profile,
      27,
      settings.libraries,
    ]);
    const record = {
      id: "a".repeat(64),
      path: "/fixture/one",
      filename: "one.mp4",
      bytes: 3,
      modified: 1,
      group: "Synthetic",
      title: "Fixture",
      creator: "",
      durationMs: 1000,
      chapters: [],
    };
    await new FileCatalogStore(pathToFileURL(profile + "/")).save([record]);
    equal(
      await new FileCatalogStore(pathToFileURL((await loadShelfConfig()).stateDirectory + "/"))
        .load(),
      [record],
    );
    equal(await Deno.readTextFile(join(profile, "access-key")), "synthetic-key-only");
    equal(JSON.parse(await Deno.readTextFile(settingsPath)), settings);
    const rejects = async (expected: string) => {
      let failed = false;
      try {
        await loadShelfConfig();
      } catch (error) {
        if (!(error instanceof Error) || !error.message.includes(expected)) throw error;
        failed = true;
      }
      if (!failed) throw new Error("Unsafe fixture profile was accepted");
    };
    await Deno.writeTextFile(
      settingsPath,
      JSON.stringify({
        ...settings,
        libraries: [
          { name: "Invalid", path: "/fixture", role: "typo" },
        ],
      }),
    );
    await rejects("Invalid library role");
    await Deno.writeTextFile(settingsPath, JSON.stringify(settings));
    Deno.env.set("RESONANCE_DATA", join(checkout, "Local"));
    await rejects("outside the source checkout");
    Deno.env.set("RESONANCE_DATA", "relative-data");
    await rejects("absolute private directory");
    const linked = join(base, "Linked profile");
    await Deno.symlink(checkout, linked, { type: "dir" });
    Deno.env.set("RESONANCE_DATA", linked);
    await rejects("through a link");
    Deno.env.set("RESONANCE_DATA", profile);
    await Deno.writeTextFile(settingsPath, JSON.stringify({ stateDirectory: "../escape" }));
    await rejects("Relative shelf state");
    await Deno.writeTextFile(settingsPath, JSON.stringify({ stateDirectory: checkout }));
    await rejects("outside the source checkout");
    await Deno.mkdir(join(checkout, "Local/shelf"), { recursive: true });
    Deno.env.delete("RESONANCE_DATA");
    await rejects("Legacy Local/shelf data remains");
    await Deno.remove(join(checkout, "Local/shelf"));
    await Deno.mkdir(join(checkout, "Local/portable-shelf"));
    Deno.env.set("RESONANCE_CONFIG", join(checkout, "server/config.json"));
    await rejects("Legacy Local/portable-shelf data remains");
    equal(await Deno.readTextFile(join(profile, "access-key")), "synthetic-key-only");
  } finally {
    for (const [key, value] of original) {
      if (value === undefined) Deno.env.delete(key);
      else Deno.env.set(key, value);
    }
    await Deno.remove(base, { recursive: true });
  }
});
