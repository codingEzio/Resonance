/** Machine settings are an adapter input; portable defaults contain no library or identity. */
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
  const stateDirectory = text("stateDirectory");
  if (
    !/^Local\/[a-zA-Z0-9_/-]+$/.test(stateDirectory) || stateDirectory.split("/").includes("..")
  ) {
    throw new Error("stateDirectory must stay under ignored Local/");
  }
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
  const explicit = Deno.env.get("RESONANCE_CONFIG");
  const overlay = explicit ? projectFile(explicit) : projectFile("Local/shelf/config.json");
  let local = {};
  try {
    local = JSON.parse(await Deno.readTextFile(overlay));
    if (!local || typeof local !== "object" || Array.isArray(local)) {
      throw new Error("Local shelf configuration must be an object");
    }
  } catch (error) {
    if (explicit || !(error instanceof Deno.errors.NotFound)) throw error;
  }
  return parseConfig({
    ...defaults,
    ...local,
    ...(Deno.env.get("RESONANCE_BIND") ? { bind: Deno.env.get("RESONANCE_BIND") } : {}),
    ...(Deno.env.get("PROJECT_PORT") || Deno.env.get("RESONANCE_PORT")
      ? { port: Number(Deno.env.get("PROJECT_PORT") || Deno.env.get("RESONANCE_PORT")) }
      : {}),
  });
}
