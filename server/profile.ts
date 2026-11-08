import { fileURLToPath } from "node:url";

export function requireLockSupport(prototype: { tryLock?: unknown } = Deno.FsFile.prototype): void {
  if (typeof prototype.tryLock !== "function") {
    throw new Error("Shelf requires Deno 2.7 or newer with non-blocking file locks");
  }
}

/** Hold the advisory lock until this process exits; never remove the lock file. */
export async function lockProfile(directory: URL): Promise<Deno.FsFile> {
  requireLockSupport();
  const path = new URL("shelf.lock", directory);
  let file: Deno.FsFile;
  try {
    file = await Deno.open(path, { read: true, write: true, createNew: true, mode: 0o600 });
  } catch (error) {
    if (!(error instanceof Deno.errors.AlreadyExists)) throw error;
    const before = await Deno.lstat(path);
    if (!before.isFile || before.isSymlink || (before.nlink !== null && before.nlink !== 1)) {
      throw new Error("Shelf lock must be a regular file without links");
    }
    file = await Deno.open(path, { read: true, write: true });
  }
  try {
    const opened = await file.stat();
    const current = await Deno.lstat(path);
    if (
      !opened.isFile || !current.isFile || current.isSymlink ||
      (current.nlink !== null && current.nlink !== 1) ||
      (opened.ino !== null && current.ino !== null &&
        (opened.ino !== current.ino || opened.dev !== current.dev))
    ) {
      throw new Error("Shelf lock changed or is not a regular file");
    }
    if (!await file.tryLock(true)) throw new Error("Shelf profile is already in use");
    return file;
  } catch (error) {
    file.close();
    throw error;
  }
}

/** Publish a complete key without overwriting a concurrently established key. */
export async function loadOrCreateKey(directory: URL): Promise<string> {
  const destination = new URL("access-key", directory);
  async function readKey(): Promise<string> {
    const key = (await Deno.readTextFile(destination)).trim();
    if (!key) {
      throw new Error("Shelf access key is empty; restore the existing key before starting");
    }
    return key;
  }
  try {
    return await readKey();
  } catch (error) {
    if (!(error instanceof Deno.errors.NotFound)) throw error;
  }
  const value = Array.from(
    crypto.getRandomValues(new Uint8Array(24)),
    (b) => b.toString(16).padStart(2, "0"),
  ).join("");
  const temporary = await Deno.makeTempFile({ dir: fileURLToPath(directory), prefix: ".key-" });
  try {
    const file = await Deno.open(temporary, { write: true, truncate: true });
    try {
      const bytes = new TextEncoder().encode(value);
      let offset = 0;
      while (offset < bytes.length) {
        const count = await file.write(bytes.subarray(offset));
        if (!count) throw new Error("Could not write shelf key");
        offset += count;
      }
      await file.sync();
    } finally {
      file.close();
    }
    try {
      await Deno.link(temporary, fileURLToPath(destination));
    } catch (error) {
      if (!(error instanceof Deno.errors.AlreadyExists)) throw error;
    }
    return await readKey();
  } finally {
    await Deno.remove(temporary);
  }
}
