import { pathToFileURL } from "node:url";
import { FileCatalogStore } from "./catalog-store.ts";
import { loadOrCreateKey, lockProfile, requireLockSupport } from "./profile.ts";

Deno.test("missing lock capability fails without touching profile", async () => {
  const path = await Deno.makeTempDir({ prefix: "shelf-old-runtime-" });
  const prototype = Deno.FsFile.prototype;
  const descriptor = Object.getOwnPropertyDescriptor(prototype, "tryLock");
  if (!descriptor) throw new Error("Test requires current lock capability");
  try {
    await Deno.writeTextFile(path + "/sentinel", "synthetic existing data");
    Reflect.deleteProperty(prototype, "tryLock");
    let blocked = false;
    try {
      await lockProfile(pathToFileURL(path + "/"));
    } catch (error) {
      if (!(error instanceof Error) || !error.message.includes("Deno 2.7")) throw error;
      blocked = true;
    }
    const entries = [];
    for await (const entry of Deno.readDir(path)) entries.push(entry.name);
    if (
      !blocked || entries.join() !== "sentinel" ||
      await Deno.readTextFile(path + "/sentinel") !== "synthetic existing data"
    ) {
      throw new Error("Unsupported runtime modified the profile");
    }
  } finally {
    Object.defineProperty(prototype, "tryLock", descriptor);
    await Deno.remove(path, { recursive: true });
  }
  requireLockSupport();
});

Deno.test("profile preserves key and serializes owners", async () => {
  const path = await Deno.makeTempDir({ prefix: "shelf-lock-test-" });
  const dir = pathToFileURL(path + "/");
  try {
    const first = await lockProfile(dir);
    let blocked = false;
    try {
      await lockProfile(dir);
    } catch (error) {
      if (!(error instanceof Error) || !error.message.includes("already in use")) throw error;
      blocked = true;
    }
    if (!blocked) throw new Error("Second profile owner was accepted");
    const [key, concurrent] = await Promise.all([loadOrCreateKey(dir), loadOrCreateKey(dir)]);
    if (!/^[a-f0-9]{48}$/.test(key) || key !== concurrent) throw new Error("Key mismatch");
    first.close();
    const reopened = await lockProfile(dir);
    if (await loadOrCreateKey(dir) !== key) throw new Error("Key changed on reopen");
    reopened.close();
    await Deno.writeTextFile(new URL("access-key", dir), "  existing-key  \n");
    if (await loadOrCreateKey(dir) !== "existing-key") throw new Error("Existing key changed");
    if (await Deno.readTextFile(new URL("access-key", dir)) !== "  existing-key  \n") {
      throw new Error("Existing key bytes changed");
    }
    await Deno.writeTextFile(new URL("access-key", dir), "  \n");
    let invalid = false;
    try {
      await loadOrCreateKey(dir);
    } catch (error) {
      if (!(error instanceof Error) || !error.message.includes("empty")) throw error;
      invalid = true;
    }
    if (!invalid || await Deno.readTextFile(new URL("access-key", dir)) !== "  \n") {
      throw new Error("Empty key was accepted or replaced");
    }
  } finally {
    await Deno.remove(path, { recursive: true });
  }
});

Deno.test("catalog parallel saves do not collide or leave partial JSON", async () => {
  const path = await Deno.makeTempDir({ prefix: "shelf-catalog-test-" });
  try {
    const dir = pathToFileURL(path + "/");
    const store = new FileCatalogStore(dir);
    const sample = [{
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
    }];
    await store.save(sample);
    if (JSON.stringify(await new FileCatalogStore(dir).load()) !== JSON.stringify(sample)) {
      throw new Error("Catalog did not survive reopening");
    }
    await Promise.all(Array.from({ length: 20 }, () => store.save([])));
    if ((await store.load()).length !== 0) throw new Error("Catalog readback differs");
    const names = [];
    for await (const entry of Deno.readDir(path)) names.push(entry.name);
    if (names.join() !== "catalog.json") throw new Error("Temporary file remains");
  } finally {
    await Deno.remove(path, { recursive: true });
  }
});

Deno.test("linked or non-regular lock is rejected without modifying its target", async () => {
  const path = await Deno.makeTempDir({ prefix: "shelf-lock-links-" });
  try {
    const dir = pathToFileURL(path + "/");
    const target = new URL("target", dir);
    const lock = new URL("shelf.lock", dir);
    await Deno.writeTextFile(target, "synthetic target");
    await Deno.symlink(target, lock);
    let blocked = false;
    try {
      await lockProfile(dir);
    } catch (error) {
      if (!(error instanceof Error) || !error.message.includes("regular file")) throw error;
      blocked = true;
    }
    if (!blocked || await Deno.readTextFile(target) !== "synthetic target") {
      throw new Error("Linked lock accepted or target changed");
    }
    await Deno.remove(lock);
    await Deno.mkdir(lock);
    blocked = false;
    try {
      await lockProfile(dir);
    } catch (error) {
      if (!(error instanceof Error) || !error.message.includes("regular file")) throw error;
      blocked = true;
    }
    if (!blocked || !(await Deno.lstat(lock)).isDirectory) {
      throw new Error("Directory lock accepted");
    }
  } finally {
    await Deno.remove(path, { recursive: true });
  }
});

Deno.test("existing key symlink reads its target without changing either entry", async () => {
  const temp = await Deno.makeTempDir();
  try {
    const root = new URL("file://" + temp + "/"),
      profile = new URL("profile/", root),
      target = new URL("target", root);
    await Deno.mkdir(profile);
    const value = "a".repeat(48);
    await Deno.writeTextFile(target, value);
    const link = new URL("access-key", profile);
    await Deno.symlink(target, link);
    const actual = await loadOrCreateKey(profile);
    if (
      actual !== value || await Deno.readTextFile(target) !== value ||
      !(await Deno.lstat(link)).isSymlink
    ) throw Error("Existing synthetic key link or target changed");
  } finally {
    await Deno.remove(temp, { recursive: true });
  }
});
