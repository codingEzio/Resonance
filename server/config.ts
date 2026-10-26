/** Machine settings are an adapter input; portable defaults contain no library or identity. */
import { basename, dirname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
export type ShelfConfig = {
  libraries: { name: string; path: string }[];
  maxFiles: number;
  maxStreams: number;
  bind: string;
  port: number;
  displayNames: string | null;
  stateDirectory: string;
  ffprobe: string;
  shasum: string;
};

export const projectRoot = new URL("../", import.meta.url);
export const projectFile = (path: string): URL => new URL(path, projectRoot);

function outsideCheckout(path: string): string {
  if (
    !isAbsolute(path) ||
    Array.from(path).some((char) => char.charCodeAt(0) < 32 || char.charCodeAt(0) === 127)
  ) {
    throw new Error("Shelf data needs an absolute private directory");
  }
  const target = resolve(path);
  const fromSource = relative(fileURLToPath(projectRoot), target);
  if (
    !fromSource ||
    (!isAbsolute(fromSource) && fromSource !== ".." && !fromSource.startsWith(".." + sep))
  ) {
    throw new Error("Shelf data must stay outside the source checkout");
  }
  return target;
}

async function verifyPhysicalDirectory(path: string): Promise<void> {
  const checkout = await Deno.realPath(fileURLToPath(projectRoot));
  let ancestor = path;
  const missing: string[] = [];
  while (true) {
    try {
      const physical = resolve(await Deno.realPath(ancestor), ...missing);
      const fromSource = relative(checkout, physical);
      if (
        !fromSource ||
        (!isAbsolute(fromSource) && fromSource !== ".." && !fromSource.startsWith(".." + sep))
      ) {
        throw new Error("Shelf data cannot resolve into the source checkout through a link");
      }
      return;
    } catch (error) {
      if (!(error instanceof Deno.errors.NotFound)) throw error;
      const parent = dirname(ancestor);
      if (parent === ancestor) throw error;
      missing.unshift(basename(ancestor));
      ancestor = parent;
    }
  }
}

function dataDirectory(): string {
  const explicit = Deno.env.get("RESONANCE_DATA");
  if (explicit !== undefined) return outsideCheckout(explicit);
  const home = Deno.env.get("HOME") ?? Deno.env.get("USERPROFILE");
  if (!home || !isAbsolute(home)) {
    throw new Error("Set RESONANCE_DATA to an absolute private directory");
  }
  return outsideCheckout(
    Deno.build.os === "darwin"
      ? join(home, "Library", "Application Support", "ResonanceShelf")
      : Deno.build.os === "windows"
      ? join(Deno.env.get("LOCALAPPDATA") ?? join(home, "AppData", "Local"), "ResonanceShelf")
      : join(Deno.env.get("XDG_DATA_HOME") ?? join(home, ".local", "share"), "ResonanceShelf"),
  );
}

export function parseConfig(value: unknown): ShelfConfig {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("Shelf configuration must be an object");
  }
  const row = value as Record<string, unknown>;
  const text = (key: string): string => {
    const value = row[key];
    if (typeof value !== "string" || !value.trim()) throw new Error(`Invalid setting: ${key}`);
    return value;
  };
  const integer = (key: string, min: number, max: number): number => {
    const value = row[key];
    if (typeof value !== "number" || !Number.isInteger(value) || value < min || value > max) {
      throw new Error(`Invalid setting: ${key}`);
    }
    return value;
  };
  if (!Array.isArray(row.libraries)) throw new Error("Invalid setting: libraries");
  const names = new Set<string>();
  const libraries = row.libraries.map((entry: unknown) => {
    if (!entry || typeof entry !== "object") throw new Error("Invalid library");
    const { name, path } = entry as Record<string, unknown>;
    if (
      typeof name !== "string" || !name.trim() || typeof path !== "string" || !path.startsWith("/")
    ) {
      throw new Error("Each library needs a unique name and an absolute path");
    }
    if (names.has(name)) throw new Error("Duplicate library name");
    names.add(name);
    return { name, path: path.replace(/\/$/, "") || "/" };
  });
  if (row.displayNames !== null && (typeof row.displayNames !== "string" || !row.displayNames)) {
    throw new Error("Invalid setting: displayNames");
  }
  const stateDirectory = outsideCheckout(text("stateDirectory"));
  return {
    libraries,
    maxFiles: integer("maxFiles", 1, 10000),
    maxStreams: integer("maxStreams", 1, 4),
    bind: text("bind"),
    port: integer("port", 10000, 14999),
    displayNames: row.displayNames as string | null,
    stateDirectory,
    ffprobe: text("ffprobe"),
    shasum: text("shasum"),
  };
}

export async function loadShelfConfig(): Promise<ShelfConfig> {
  const defaults = JSON.parse(await Deno.readTextFile(new URL("./config.json", import.meta.url)));
  const dataRoot = dataDirectory();
  await verifyPhysicalDirectory(dataRoot);
  const explicit = Deno.env.get("RESONANCE_CONFIG");
  if (Deno.env.get("RESONANCE_DATA") === undefined) {
    for (const legacy of ["Local/shelf", "Local/portable-shelf"]) {
      try {
        await Deno.lstat(projectFile(legacy));
        throw new Error(
          `Legacy ${legacy} data remains: preserve and migrate it to the private data directory before starting`,
        );
      } catch (error) {
        if (!(error instanceof Deno.errors.NotFound)) throw error;
      }
    }
  }
  const overlay = explicit ? projectFile(explicit) : pathToFileURL(join(dataRoot, "config.json"));
  let local = {};
  try {
    local = JSON.parse(await Deno.readTextFile(overlay));
    if (!local || typeof local !== "object" || Array.isArray(local)) {
      throw new Error("Local shelf configuration must be an object");
    }
  } catch (error) {
    if (explicit || !(error instanceof Deno.errors.NotFound)) throw error;
  }
  const merged = {
    ...defaults,
    ...local,
    ...(Deno.env.get("RESONANCE_BIND") ? { bind: Deno.env.get("RESONANCE_BIND") } : {}),
    ...(Deno.env.get("PROJECT_PORT") || Deno.env.get("RESONANCE_PORT")
      ? { port: Number(Deno.env.get("PROJECT_PORT") || Deno.env.get("RESONANCE_PORT")) }
      : {}),
  };
  if (typeof merged.stateDirectory !== "string" || !merged.stateDirectory.trim()) {
    throw new Error("Invalid setting: stateDirectory");
  }
  if (!isAbsolute(merged.stateDirectory) && merged.stateDirectory.split(/[\\/]/).includes("..")) {
    throw new Error("Relative shelf state must stay within its private data directory");
  }
  const stateDirectory = outsideCheckout(resolve(dataRoot, merged.stateDirectory));
  await verifyPhysicalDirectory(stateDirectory);
  return parseConfig({
    ...merged,
    stateDirectory,
  });
}
