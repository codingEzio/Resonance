import { catalogMemberships, type SourceRecord, uniqueCatalog } from "./core.ts";

export type CatalogLocation = { path: string; group: string };
export type CatalogStatus = {
  active: boolean;
  completed: number;
  total: number;
  failures: string[];
  updated: string;
};
export type DisplayName = { title: string; subtitle: string };

/** Capabilities keep indexing independent of HTTP, filesystem APIs and probing executables. */
export interface CatalogSource {
  discover(path: string, group: string, limit: number): Promise<CatalogLocation[]>;
  info(path: string): Promise<{ size: number; modified: number }>;
  inspect(path: string, group: string): Promise<SourceRecord>;
}
export interface CatalogStore {
  load(): Promise<SourceRecord[]>;
  save(records: SourceRecord[]): Promise<void>;
}

export class ShelfCatalog {
  status: CatalogStatus = { active: false, completed: 0, total: 0, failures: [], updated: "" };
  private records: SourceRecord[] = [];
  private byId = new Map<string, SourceRecord>();
  private requestedAgain = false;
  text = "{}";

  constructor(
    private readonly libraries: { name: string; path: string }[],
    private readonly maxFiles: number,
    private readonly source: CatalogSource,
    private readonly store: CatalogStore,
    private readonly displayNames: ReadonlyMap<string, DisplayName> = new Map(),
  ) {}

  async restore() {
    this.records = await this.store.load();
    this.publish();
  }

  find(id: string): SourceRecord | undefined {
    return this.byId.get(id);
  }

  private publish() {
    // Filter the cached source before publishing after a configuration change.
    const records = this.records.filter((record) =>
      this.libraries.some((library) =>
        library.name === record.group &&
        record.path.startsWith(library.path.replace(/\/$/, "") + "/")
      )
    );
    this.byId = new Map(uniqueCatalog(records).map((record) => [record.id, record]));
    const counts = new Map<string, number>();
    for (const record of records) counts.set(record.id, (counts.get(record.id) ?? 0) + 1);
    const memberships = catalogMemberships(records, this.libraries);
    this.text = JSON.stringify({
      version: 1,
      status: this.status,
      items: [...this.byId.values()].map(({ path: _path, modified: _modified, ...record }) => ({
        ...record,
        sourceCount: counts.get(record.id),
        memberships: memberships.get(record.id) ?? [],
        displayName: this.displayNames.get(record.id) ?? {
          title: record.title.replaceAll("_", " "),
          subtitle: "",
        },
      })),
    });
  }

  async scan(): Promise<void> {
    if (this.status.active) {
      this.requestedAgain = true;
      return;
    }
    this.status = { ...this.status, active: true, completed: 0, total: 0, failures: [] };
    this.publish();
    try {
      const candidates: CatalogLocation[] = [];
      for (const root of this.libraries) {
        candidates.push(...await this.source.discover(root.path, root.name, this.maxFiles));
        if (candidates.length > this.maxFiles) {
          throw new Error(`Configured ${this.maxFiles}-file limit reached`);
        }
      }
      this.status.total = candidates.length;
      const paths = new Set(candidates.map((candidate) => candidate.path));
      this.records = this.records.filter((record) => paths.has(record.path));
      const cached = new Map(this.records.map((record) => [record.path, record]));
      for (const candidate of candidates) {
        try {
          const info = await this.source.info(candidate.path), old = cached.get(candidate.path);
          if (!old || old.bytes !== info.size || old.modified !== info.modified) {
            const record = await this.source.inspect(candidate.path, candidate.group);
            this.records = this.records.filter((record) => record.path !== candidate.path);
            this.records.push(record);
          }
        } catch (error) {
          this.records = this.records.filter((record) => record.path !== candidate.path);
          this.status.failures.push(
            `${candidate.path.split("/").at(-1)}: ${
              error instanceof Error ? error.message : error
            }`,
          );
        }
        this.status.completed++;
        this.publish();
        if (this.status.completed % 10 === 0) await this.store.save(this.records);
      }
      await this.store.save(this.records);
      this.status.updated = new Date().toISOString();
    } catch (error) {
      this.status.failures.push(String(error));
    } finally {
      this.status.active = false;
      this.publish();
      if (this.requestedAgain) {
        this.requestedAgain = false;
        void this.scan();
      }
    }
  }
}
