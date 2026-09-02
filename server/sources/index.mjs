/**
 * Multi-source GMP aggregator.
 *
 * Sources are tried in the order they appear in config.json. Every source runs
 * independently, so one being down, slow or restructured never blocks the others —
 * the run just produces fewer fields. For each company, the first source that
 * reports a value wins, and every value is tagged with where it came from.
 *
 * Adding a source means adding one entry to config.json; the adapter is picked by
 * its `type`.
 */

import { fetchReportJson } from './report-json.mjs'
import { fetchGenericTable } from './generic-table.mjs'
import { normaliseName } from '../lib/util.mjs'

const ADAPTERS = {
  'report-json': fetchReportJson,
  'html-table': fetchGenericTable
}

/** Fields a source may contribute, in the order the merger fills them. */
const MERGEABLE = [
  'premium',
  'gmpPercent',
  'kostak',
  'subjectToSauda',
  'lotSize',
  'priceMax',
  'issueSizeCr',
  'subscriptionTotal',
  'peRatio',
  'openDate',
  'closeDate',
  'allotmentDate',
  'listingDate',
  'board'
]

export async function fetchGmpFromAll(config) {
  const sources = (config?.sources ?? []).filter((s) => s.enabled !== false && s.url)

  if (sources.length === 0) {
    console.error('· no GMP sources configured — using overrides only')
    return { rows: [], report: [] }
  }

  // Run every source, but never let one failure reject the batch.
  const settled = await Promise.all(
    sources.map(async (source) => {
      const adapter = ADAPTERS[source.type]
      if (!adapter) {
        console.warn(`! ${source.name}: unknown type "${source.type}"`)
        return { source, rows: [] }
      }
      const started = Date.now()
      try {
        const rows = await adapter(source)
        console.error(`· ${source.name}: ${rows.length} rows in ${Date.now() - started}ms`)
        return { source, rows }
      } catch (err) {
        console.warn(`! ${source.name} threw: ${err.message}`)
        return { source, rows: [] }
      }
    })
  )

  const merged = new Map()

  for (const { source, rows } of settled) {
    for (const row of rows) {
      const key = normaliseName(row.name)
      if (!key) continue

      const existing = merged.get(key)
      if (!existing) {
        merged.set(key, { ...row, sources: { [source.name]: true } })
        continue
      }

      // Earlier sources win; later ones only fill gaps.
      existing.sources[source.name] = true
      for (const field of MERGEABLE) {
        if (existing[field] === null || existing[field] === undefined) {
          const candidate = row[field]
          if (candidate !== null && candidate !== undefined) {
            existing[field] = candidate
          }
        }
      }
    }
  }

  const report = settled.map(({ source, rows }) => ({
    name: source.name,
    ok: rows.length > 0,
    rows: rows.length
  }))

  const live = report.filter((r) => r.ok).length
  if (live === 0 && sources.length > 0) {
    console.warn('! every GMP source failed this run — keeping previous values')
  }

  return { rows: [...merged.values()], report }
}
