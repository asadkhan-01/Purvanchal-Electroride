// Date display/input in Indian dd/mm/yyyy. The API and database use ISO dates (yyyy-mm-dd);
// these helpers only convert at the edges. Plain module (no imports) so it can be unit-tested.

const IST_OFFSET_MS = 5.5 * 3600e3;
const pad = (n: number) => String(n).padStart(2, '0');

/** Today's date in India as yyyy-mm-dd. */
export function todayIso(): string {
  return new Date(Date.now() + IST_OFFSET_MS).toISOString().slice(0, 10);
}

/** Today's date in India as dd/mm/yyyy. */
export function todayDmy(): string { return toDmy(todayIso()); }

/** 'yyyy-mm-dd' (or a timestamp starting with it) -> 'dd/mm/yyyy'. Empty for missing values. */
export function toDmy(iso: string | null | undefined): string {
  if (!iso) return '';
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  return m ? `${m[3]}/${m[2]}/${m[1]}` : iso;
}

/** Timestamp -> 'dd/mm/yyyy, hh:mm' in Indian time. */
export function toDmyTime(ts: string | null | undefined): string {
  if (!ts) return '';
  const t = new Date(ts);
  if (Number.isNaN(t.getTime())) return ts;
  const d = new Date(t.getTime() + IST_OFFSET_MS);
  return `${pad(d.getUTCDate())}/${pad(d.getUTCMonth() + 1)}/${d.getUTCFullYear()}, ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
}

/** 'dd/mm/yyyy' (also d/m/yyyy, dd-mm-yyyy, dd.mm.yyyy) -> 'yyyy-mm-dd', or null if not a real date. */
export function parseDmy(input: string | null | undefined): string | null {
  const m = /^\s*(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})\s*$/.exec(input ?? '');
  if (!m) return null;
  const [d, mo, y] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const date = new Date(Date.UTC(y, mo - 1, d));
  if (date.getUTCFullYear() !== y || date.getUTCMonth() !== mo - 1 || date.getUTCDate() !== d) return null;
  return `${y}-${pad(mo)}-${pad(d)}`;
}
