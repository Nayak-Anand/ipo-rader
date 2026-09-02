/**
 * GMP source.
 *
 * There is no official or free API for grey-market premium anywhere — GMP is an
 * unregulated over-the-counter number that a handful of websites publish. So this
 * module is deliberately generic: you point it at a page you are allowed to read
 * (check that site's robots.txt and terms first), describe its table in
 * config.json, and it extracts the rows.
 *
 * config.json -> "gmp": {
 *   "url": "https://example.com/ipo-gmp",
 *   "rowSelector": "table tbody tr",
 *   "nameCell": 0,
 *   "premiumCell": 2
 * }
 *
 * If no URL is configured, GMP comes purely from data/overrides.json, which you
 * can edit by hand — the app works fine either way, it just shows fewer numbers.
 */

import { load } from 'cheerio'
import { num } from '../lib/util.mjs'

const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) ' +
  'Chrome/124.0.0.0 Safari/537.36'

export async function fetchGenericTable(config) {
  if (!config?.url) {
    console.error('· generic-table source has no url')
    return []
  }

  let html
  try {
    const res = await fetch(config.url, { headers: { 'User-Agent': UA } })
    if (!res.ok) {
      console.warn(`! GMP source responded ${res.status}`)
      return []
    }
    html = await res.text()
  } catch (err) {
    console.warn(`! GMP fetch failed: ${err.message}`)
    return []
  }

  const $ = load(html)
  const rowSelector = config.rowSelector ?? 'table tbody tr'
  const nameCell = config.nameCell ?? 0
  const premiumCell = config.premiumCell ?? 1

  const rows = []
  $(rowSelector).each((_, el) => {
    const cells = $(el).find('td')
    if (cells.length <= Math.max(nameCell, premiumCell)) return

    const name = $(cells[nameCell]).text().replace(/\s+/g, ' ').trim()
    const premium = num($(cells[premiumCell]).text())
    if (!name || premium === null) return

    rows.push({ name, premium, source: config.name ?? 'generic-table' })
  })

  return rows
}
