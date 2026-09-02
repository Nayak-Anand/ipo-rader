/**
 * NSE public market-data endpoints. Free, no key, no registration.
 *
 * NSE refuses /api/ calls that do not carry a session cookie issued by the main
 * site, so we load a normal page first and reuse its Set-Cookie header.
 */

import { numbers, num, toIsoDate, deriveStatus } from '../lib/util.mjs'

const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) ' +
  'Chrome/124.0.0.0 Safari/537.36'

const BASE = 'https://www.nseindia.com'
const WARM_UP = `${BASE}/market-data/all-upcoming-issues-ipo`

let cookieHeader = ''

async function primeSession() {
  const res = await fetch(WARM_UP, {
    headers: {
      'User-Agent': UA,
      Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      'Accept-Language': 'en-US,en;q=0.9'
    }
  })
  const raw = res.headers.getSetCookie?.() ?? []
  cookieHeader = raw.map((c) => c.split(';')[0]).join('; ')
}

async function getJson(pathname) {
  if (!cookieHeader) await primeSession()

  for (let attempt = 0; attempt < 2; attempt++) {
    const res = await fetch(`${BASE}${pathname}`, {
      headers: {
        'User-Agent': UA,
        Accept: 'application/json, text/plain, */*',
        'Accept-Language': 'en-US,en;q=0.9',
        Referer: WARM_UP,
        Cookie: cookieHeader
      }
    })
    if (res.ok) {
      const text = await res.text()
      try {
        return JSON.parse(text)
      } catch {
        return []
      }
    }
    // Session expired or throttled — re-prime once and retry.
    await primeSession()
  }
  console.warn(`! NSE ${pathname} failed after retry`)
  return []
}

function asArray(payload) {
  if (Array.isArray(payload)) return payload
  for (const key of ['data', 'activeIssues', 'upcomingIssues', 'results']) {
    if (Array.isArray(payload?.[key])) return payload[key]
  }
  return []
}

function pick(row, ...keys) {
  for (const key of keys) {
    const v = row?.[key]
    if (v !== undefined && v !== null && String(v).trim() !== '' && String(v).trim() !== '-') {
      return String(v).trim()
    }
  }
  return null
}

function mapIssue(row, forcedStatus) {
  const name = pick(row, 'companyName', 'company', 'issuerName', 'name', 'symbol')
  if (!name) return null

  const symbol = (pick(row, 'symbol') ?? '').toUpperCase()
  const openDate = toIsoDate(pick(row, 'issueStartDate', 'biddingStartDate', 'startDate'))
  const closeDate = toIsoDate(pick(row, 'issueEndDate', 'biddingEndDate', 'endDate'))
  const listingDate = toIsoDate(pick(row, 'listingDate', 'dateOfListing'))

  const band = numbers(pick(row, 'issuePrice', 'priceBand', 'price'))

  // NSE spells these inconsistently: the multiple is "noOfTime" and the bid field
  // carries a lowercase s ("noOfsharesBid").
  const offered = num(pick(row, 'noOfSharesOffered', 'noOfShareOffered'))
  const bid = num(pick(row, 'noOfsharesBid', 'noOfSharesBid'))
  const rawTimes =
    num(pick(row, 'noOfTime', 'noOfTimesIssueSubscribed', 'timesSubscribed')) ??
    (offered && bid ? bid / offered : null)
  // NSE returns full float precision (0.9205078702392565); two decimals is what we show.
  const times = rawTimes === null ? null : Number(rawTimes.toFixed(2))

  const series = (pick(row, 'series', 'category', 'issueType') ?? '').toUpperCase()
  const rawIssueSize = num(pick(row, 'issueSize'))

  return {
    name,
    symbol,
    board: series.includes('SME') ? 'SME' : 'MAINBOARD',
    status: forcedStatus ?? deriveStatus(openDate, closeDate, listingDate),
    priceMin: band[0] ?? null,
    priceMax: band.length ? band[band.length - 1] : null,
    lotSize: num(pick(row, 'lotSize', 'marketLot', 'minBidQuantity')),
    // "issueSize" here is a share count, not rupees — convert with the cut-off price.
    issueSizeCr: (() => {
      if (!rawIssueSize) return null
      const cutOff = band.length ? band[band.length - 1] : null
      if (cutOff) return Number(((rawIssueSize * cutOff) / 1e7).toFixed(2))
      return rawIssueSize > 100000 ? Number((rawIssueSize / 1e7).toFixed(2)) : rawIssueSize
    })(),
    openDate,
    closeDate,
    listingDate,
    exchange: pick(row, 'isBse') === '1' ? 'BSE, NSE' : 'NSE',
    subscription: times === null ? null : { total: times }
  }
}

export async function fetchNseIssues() {
  const [current, upcoming] = await Promise.all([
    getJson('/api/ipo-current-issue'),
    getJson('/api/all-upcoming-issues?category=ipo')
  ])

  const rows = [
    ...asArray(current).map((r) => mapIssue(r, 'OPEN')),
    ...asArray(upcoming).map((r) => mapIssue(r, null))
  ].filter(Boolean)

  const seen = new Set()
  return rows.filter((issue) => {
    const key = issue.symbol || issue.name
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}
