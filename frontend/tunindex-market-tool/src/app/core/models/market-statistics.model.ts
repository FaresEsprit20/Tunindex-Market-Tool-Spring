import { OwnershipType, SectorType } from './stock.model';

// GET .../statistics/by-sector and .../statistics/by-ownership return
// List<Object[]> from a raw JPA aggregate query (SELECT x, COUNT(*) ...
// GROUP BY x) — Jackson serializes that as an array of 2-element arrays,
// e.g. [["FINANCIALS", 12], ["BANKING", 8]], NOT a named {sector, count}
// object. Model it as a tuple, not a record.
export type SectorStatEntry = [sector: SectorType, count: number];
export type OwnershipStatEntry = [ownership: OwnershipType, count: number];
