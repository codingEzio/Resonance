import type { CatalogStore } from "./catalog.ts";
import type { SourceRecord } from "./core.ts";

/** Same atomic rename and private cache format as the existing shelf. */
export class FileCatalogStore implements CatalogStore {
  constructor(private readonly directory: URL) {}

  async load(): Promise<SourceRecord[]> {
    try {
      const records = JSON.parse(await Deno.readTextFile(new URL("catalog.json", this.directory)));
      if (!Array.isArray(records)) throw new Error("Invalid shelf catalog cache");
      return records;
    } catch (error) {
      if (error instanceof Deno.errors.NotFound) return [];
      throw error;
    }
  }

  async save(records: SourceRecord[]): Promise<void> {
    const temporary = new URL("catalog.next.json", this.directory);
    await Deno.writeTextFile(temporary, JSON.stringify(records));
    await Deno.rename(temporary, new URL("catalog.json", this.directory));
  }
}
