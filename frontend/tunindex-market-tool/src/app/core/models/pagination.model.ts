// Mirrors backend common's PaginationAndFilteringDto / PagedResponse.

export type SortingDirection = 'ASC' | 'DESC';

export interface PaginationAndFilteringRequest {
  page: number; // 1-indexed, per the backend contract
  size: number;
  sortField?: string;
  sortDirection?: SortingDirection;
  filters?: Record<string, string>;
}

export interface PagedResponse<T> {
  content: T[];
  page: number; // 1-indexed
  size: number;
  totalElements: number;
  totalPages: number;
}
