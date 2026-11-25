import { catalogMemberships, libraryPrefix, type SourceRecord, uniqueCatalog } from "./core.ts";

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
  info(path: string): Promise<{ size: number; modified: number; fileIdentity?: string }>;
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
  private sourceId: string | null = null;
  private collectionNames: Record<string, string> = {};
  private retiredHashes: string[] = [];
  text = "{}";

  constructor(
    private readonly libraries: { name: string; path: string; role?: string }[],
    private readonly maxFiles: number,
    private readonly source: CatalogSource,
    private readonly store: CatalogStore,
    private displayNames: ReadonlyMap<string, DisplayName> = new Map(),
  ) {}

  updateDisplayNames(
    names: ReadonlyMap<string, DisplayName>,
    sourceId: string | null = null,
    collectionNames: Record<string, string> = {},
    retiredHashes: string[] = [],
  ) {
    this.displayNames = names;
    this.sourceId = sourceId;
    this.collectionNames = collectionNames;
    this.retiredHashes = retiredHashes;
    this.publish();
  }

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
        record.path.startsWith(libraryPrefix(library.path))
      )
    );
    this.byId = new Map(uniqueCatalog(records).map((record) => [record.id, record]));
    const counts = new Map<string, number>();
    for (const record of records) counts.set(record.id, (counts.get(record.id) ?? 0) + 1);
    const memberships = catalogMemberships(records, this.libraries);
    this.text = JSON.stringify({
      version: 1,
      sourceId: this.sourceId,
      collectionNames: this.collectionNames,
      retiredHashes: this.retiredHashes,
      status: this.status,
      items: [...this.byId.values()].map((
        { path: _path, modified: _modified, fileIdentity: _fileIdentity, ...record },
      ) => ({
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
      const cached = new Map(this.records.map((record) => [record.path, record]));
      const next: SourceRecord[] = [];
      for (const candidate of candidates) {
        const info = await this.source.info(candidate.path), old = cached.get(candidate.path);
        next.push(
          !old || old.bytes !== info.size || old.modified !== info.modified ||
            old.fileIdentity !== info.fileIdentity
            ? await this.source.inspect(candidate.path, candidate.group)
            : { ...old, group: candidate.group },
        );
        this.status.completed++;
      }
      // A partial or changing scan must never become a deletion authority.
      const current: CatalogLocation[] = [];
      for (const root of this.libraries) {
        current.push(...await this.source.discover(root.path, root.name, this.maxFiles));
      }
      const signature = (rows: CatalogLocation[]) =>
        rows.map((x) => `${x.group}\0${x.path}`).sort().join("\n");
      if (signature(candidates) !== signature(current)) {
        throw new Error("Library changed during scan");
      }
      for (const record of next) {
        const info = await this.source.info(record.path);
        if (
          info.size !== record.bytes || info.modified !== record.modified ||
          info.fileIdentity !== record.fileIdentity
        ) {
          throw new Error("Library changed during scan");
        }
      }
      await this.store.save(next);
      this.records = next;
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
