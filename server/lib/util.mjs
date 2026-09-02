const MONTHS = {
  jan: '01', feb: '02', mar: '03', apr: '04', may: '05', jun: '06',
  jul: '07', aug: '08', sep: '09', oct: '10', nov: '11', dec: '12'
}

export function num(value) {
  if (value === null || value === undefined || value === '') return null
  const n = typeof value === 'number' ? value : Number(String(value).replace(/[^0-9.\-]/g, ''))
  return Number.isFinite(n) ? n : null
}

/** Pulls every number out of "105 - 111" / "₹1,250.50 Cr". */
export function numbers(raw) {
  if (!raw) return []
  return (String(raw).replace(/,/g, '').match(/\d+(?:\.\d+)?/g) ?? []).map(Number)
}

/** Accepts "25-Nov-2026", "2026-11-25", "25/11/2026" and returns ISO yyyy-mm-dd. */
export function toIsoDate(raw) {
  if (!raw) return null
  const s = String(raw).trim().split('T')[0]

  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s

  let m = s.match(/^(\d{1,2})[-/\s]([A-Za-z]{3,})[-/\s](\d{4})$/)
  if (m) {
    const month = MONTHS[m[2].slice(0, 3).toLowerCase()]
    if (month) return `${m[3]}-${month}-${m[1].padStart(2, '0')}`
  }

  m = s.match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/)
  if (m) return `${m[3]}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}`

  return null
}

export function normaliseName(name) {
  return String(name ?? '')
    .toLowerCase()
    .replace(/\b(limited|ltd|private|pvt|india|ipo|the)\b/g, '')
    .replace(/[^a-z0-9]/g, '')
    .trim()
}

/** Stable id shared by the scraper and the app. */
export function matchKey(issue) {
  const base = issue.symbol || issue.id || issue.name || ''
  return String(base)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
}

export function deriveStatus(openDate, closeDate, listingDate) {
  const today = new Date().toISOString().slice(0, 10)
  if (listingDate && listingDate <= today) return 'LISTED'
  if (closeDate && closeDate < today) return 'CLOSED'
  if (openDate && openDate > today) return 'UPCOMING'
  if (openDate && openDate <= today) return 'OPEN'
  return 'UPCOMING'
}
