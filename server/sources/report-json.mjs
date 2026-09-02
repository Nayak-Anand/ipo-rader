/**
 * Adapter for the JSON report endpoint used by InvestorGain and Chittorgarh
 * (they run the same reporting platform, so one parser serves both).
 *
 * Rows look like this — HTML in the display fields, clean values in the `~` fields:
 *
 *   {
 *     "~ipo_name": "Rays of Belief",
 *     "Name": "<a href=\"/gmp/rays-of-belief-ipo/2041/\" title=\"Rays of Belief\">",
 *     "GMP": "&#8377;<b>38</b> (15.90%)<br><small>&#128293;</small>",
 *     "~gmp_percent_calc": "15.90",
 *     "Lot": "62",  "Price (₹)": "125",  "IPO Size": "&#8377;125.00 Cr",
 *     "Sub": "1.18x",  "P/E": "11.87",
 *     "~Srt_Open": "2026-09-01", "~Srt_Close": "2026-09-03",
 *     "~Srt_BoA_Dt": "2026-09-04", "~Str_Listing": "2026-09-08",
 *     "~ipo_category1": "SME" | "IPO"
 *   }
 */

import { num } from '../lib/util.mjs'

const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) ' +
  'Chrome/124.0.0.0 Safari/537.36'

/** Strips tags and the few HTML entities these fields actually use. */
function plain(value) {
  if (value === null || value === undefined) return ''
  return String(value)
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/<[^>]+>/g, '')
    .replace(/&#8377;|&rupee;/gi, '')
    .replace(/&#\d+;/g, '')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/\s+/g, ' ')
    .trim()
}

/**
 * "₹38 (15.90%)" -> 38 ; "₹-- (0.00%)" -> null.
 * The premium is whatever sits before the bracket — the bracket holds the percent,
 * so reading "the first number" would silently return 0.00 for an empty GMP.
 */
function parsePremium(raw) {
  const text = plain(raw)
  if (!text) return null
  const head = text.split('(')[0].trim()
  if (!/\d/.test(head)) return null
  const m = head.match(/-?\d+(?:\.\d+)?/)
  return m ? Number(m[0]) : null
}

/** The payload is sometimes a bare array, sometimes wrapped in a data key. */
function rowsOf(payload) {
  if (Array.isArray(payload)) return payload
  if (!payload || typeof payload !== 'object') return []
  for (const key of ['reportTableData', 'data', 'rows', 'result', 'records']) {
    if (Array.isArray(payload[key])) return payload[key]
  }
  const firstArray = Object.values(payload).find((v) => Array.isArray(v))
  return firstArray ?? []
}

function nameOf(row) {
  const clean = plain(row['~ipo_name'])
  if (clean) return clean
  // Fall back to the title attribute inside the anchor.
  const title = String(row.Name ?? '').match(/title=\\?"([^"\\]+)/)
  if (title) return title[1].trim()
  return plain(row.Name)
}

/**
 * The report path embeds the current month, calendar year and Indian financial year:
 *   /cloud/v2/report/data-read/331/1/{month}/{year}/{fy}/0/all?search=&v=10-49
 * Substituting them per run keeps the URL from going stale on the 1st of the month.
 */
function resolveUrl(template, now = new Date()) {
  // Evaluate in IST — the report is keyed to the Indian trading calendar.
  const ist = new Date(now.getTime() + (330 + now.getTimezoneOffset()) * 60000)
  const month = ist.getMonth() + 1
  const year = ist.getFullYear()
  // Indian FY runs April to March.
  const fyStart = month >= 4 ? year : year - 1
  const fy = `${fyStart}-${String((fyStart + 1) % 100).padStart(2, '0')}`

  return template
    .replaceAll('{month}', String(month))
    .replaceAll('{year}', String(year))
    .replaceAll('{fy}', fy)
}

export async function fetchReportJson(source) {
  if (!source?.url) return []
  const url = resolveUrl(source.url)

  let payload
  try {
    const res = await fetch(url, {
      headers: {
        'User-Agent': UA,
        Accept: 'application/json, text/plain, */*',
        ...(source.referer ? { Referer: source.referer } : {}),
        ...(source.origin ? { Origin: source.origin } : {})
      }
    })
    if (!res.ok) {
      console.warn(`! ${source.name}: HTTP ${res.status}`)
      return []
    }
    payload = await res.json()
  } catch (err) {
    console.warn(`! ${source.name}: ${err.message}`)
    return []
  }

  const rows = rowsOf(payload)
  const out = []

  for (const row of rows) {
    const name = nameOf(row)
    if (!name) continue

    const premium = parsePremium(row.GMP ?? row.gmp)
    const record = {
      name,
      premium,
      gmpPercent: num(row['~gmp_percent_calc']),
      // This source carries more than GMP — take it all, the merger decides what wins.
      lotSize: num(plain(row.Lot)),
      priceMax: num(plain(row['Price (₹)'] ?? row.Price)),
      issueSizeCr: num(plain(row['IPO Size'])),
      subscriptionTotal: num(plain(row.Sub)),
      peRatio: num(plain(row['~P/E'] ?? row['P/E'])),
      openDate: plain(row['~Srt_Open']) || null,
      closeDate: plain(row['~Srt_Close']) || null,
      allotmentDate: plain(row['~Srt_BoA_Dt']) || null,
      listingDate: plain(row['~Str_Listing'] ?? row['~Srt_Listing']) || null,
      board: String(row['~ipo_category1'] ?? '').toUpperCase().includes('SME')
        ? 'SME'
        : 'MAINBOARD',
      source: source.name
    }

    // A row with neither a premium nor any useful detail is not worth carrying.
    if (record.premium === null && record.lotSize === null && record.allotmentDate === null) {
      continue
    }
    out.push(record)
  }

  return out
}
