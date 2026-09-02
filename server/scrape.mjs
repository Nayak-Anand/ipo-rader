#!/usr/bin/env node
/**
 * Builds the free `ipos.json` feed the Android app reads.
 *
 *   node scrape.mjs            # writes ./public/ipos.json
 *   node scrape.mjs --dry-run  # prints to stdout instead
 *
 * Sources:
 *   - NSE public market-data API  (official, free, no key)
 *   - GMP: whatever you configure in config.json + manual overrides
 *
 * Run it from GitHub Actions on a schedule and publish ./public via GitHub Pages.
 * That whole loop costs nothing.
 */

import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { fetchNseIssues } from './sources/nse.mjs'
import { fetchGmpFromAll } from './sources/index.mjs'
import { normaliseName, matchKey, toIsoDate, num, deriveStatus } from './lib/util.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const dryRun = process.argv.includes('--dry-run')

async function readJson(relative, fallback) {
  const file = path.join(here, relative)
  if (!existsSync(file)) return fallback
  try {
    return JSON.parse(await readFile(file, 'utf8'))
  } catch (err) {
    console.warn(`! could not parse ${relative}: ${err.message}`)
    return fallback
  }
}

/** Yesterday's feed, so GMP history accumulates instead of resetting every run. */
async function previousFeed() {
  return readJson('public/ipos.json', { ipos: [] })
}

function mergeGmpHistory(previous, todayPremium, label, override) {
  const history = Array.isArray(previous?.gmp?.history) ? [...previous.gmp.history] : []
  const kostak = num(override.kostak ?? previous?.gmp?.kostak)
  const subjectToSauda = num(override.subjectToSauda ?? previous?.gmp?.subjectToSauda)

  if (todayPremium === null || todayPremium === undefined) {
    if (!previous?.gmp) return null
    return { ...previous.gmp, kostak, subjectToSauda }
  }

  const last = history[history.length - 1]
  if (last && last.date === label) {
    last.premium = todayPremium
  } else {
    history.push({ date: label, premium: todayPremium })
  }

  return {
    premium: todayPremium,
    kostak,
    subjectToSauda,
    updatedAt: new Date().toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' }),
    history: history.slice(-45)
  }
}

async function main() {
  const config = await readJson('config.json', {})
  const overrides = await readJson('data/overrides.json', {})
  const previous = await previousFeed()
  const prevByKey = new Map((previous.ipos ?? []).map((i) => [matchKey(i), i]))

  const issues = await fetchNseIssues()
  console.error(`· NSE returned ${issues.length} issues`)

  const { rows: gmpRows, report: sourceReport } = await fetchGmpFromAll(config)
  console.error(`· GMP: ${gmpRows.length} companies from ${sourceReport.filter((s) => s.ok).length}/${sourceReport.length} sources`)

  const gmpByName = new Map(gmpRows.map((row) => [normaliseName(row.name), row]))
  const todayLabel = new Date().toLocaleDateString('en-IN', {
    timeZone: 'Asia/Kolkata',
    day: 'numeric',
    month: 'short'
  })

  const ipos = issues.map((issue) => {
    const key = matchKey(issue)
    const prev = prevByKey.get(key)
    const override = overrides[key] ?? overrides[issue.symbol] ?? {}

    const gmpRow =
      gmpByName.get(normaliseName(issue.name)) ??
      gmpRows.find((row) => normaliseName(row.name).startsWith(normaliseName(issue.name).slice(0, 12)))

    const premium = num(override.gmp ?? gmpRow?.premium)

    return {
      id: key,
      name: issue.name,
      symbol: issue.symbol,
      board: override.board ?? issue.board,
      status: issue.status,
      priceMin: num(override.priceMin ?? issue.priceMin ?? prev?.priceMin),
      // NSE wins on price/lot (it is the exchange); the GMP source fills the gaps.
      priceMax: num(override.priceMax ?? issue.priceMax ?? gmpRow?.priceMax ?? prev?.priceMax),
      lotSize: num(override.lotSize ?? issue.lotSize ?? gmpRow?.lotSize ?? prev?.lotSize),
      issueSizeCr: num(
        override.issueSizeCr ?? issue.issueSizeCr ?? gmpRow?.issueSizeCr ?? prev?.issueSizeCr
      ),
      freshIssueCr: num(override.freshIssueCr ?? prev?.freshIssueCr),
      ofsCr: num(override.ofsCr ?? prev?.ofsCr),
      openDate: toIsoDate(override.openDate ?? issue.openDate ?? gmpRow?.openDate ?? prev?.openDate),
      closeDate: toIsoDate(
        override.closeDate ?? issue.closeDate ?? gmpRow?.closeDate ?? prev?.closeDate
      ),
      // NSE publishes neither of these — the GMP report does.
      allotmentDate: toIsoDate(override.allotmentDate ?? gmpRow?.allotmentDate ?? prev?.allotmentDate),
      refundDate: toIsoDate(override.refundDate ?? prev?.refundDate),
      listingDate: toIsoDate(
        override.listingDate ?? issue.listingDate ?? gmpRow?.listingDate ?? prev?.listingDate
      ),
      registrar: override.registrar ?? prev?.registrar ?? null,
      exchange: issue.exchange ?? prev?.exchange ?? null,
      about: override.about ?? prev?.about ?? null,
      // Prospectus content has no free API — it is hand-entered in data/overrides.json.
      strengths: override.strengths ?? prev?.strengths ?? [],
      risks: override.risks ?? prev?.risks ?? [],
      financials: override.financials ?? prev?.financials ?? [],
      valuation: override.valuation ?? prev?.valuation ?? null,
      listingPrice: num(override.listingPrice ?? prev?.listingPrice),
      gmp: mergeGmpHistory(prev, premium, todayLabel, override),
      subscription:
        override.subscription ??
        issue.subscription ??
        (gmpRow?.subscriptionTotal != null ? { total: gmpRow.subscriptionTotal } : null) ??
        prev?.subscription ??
        null
    }
  })

  // NSE only lists NSE issues. BSE SME IPOs reach us solely through the GMP source,
  // so carry over any row it reported that NSE never mentioned — otherwise a whole
  // segment of the market (and the registrars that serve it) is invisible.
  const matchedNames = new Set(issues.map((i) => normaliseName(i.name)))
  for (const row of gmpRows) {
    const key = normaliseName(row.name)
    if (!key || matchedNames.has(key)) continue
    matchedNames.add(key)

    const override = overrides[key] ?? {}
    const openDate = toIsoDate(override.openDate ?? row.openDate)
    const closeDate = toIsoDate(override.closeDate ?? row.closeDate)
    const listingDate = toIsoDate(override.listingDate ?? row.listingDate)
    const prev = prevByKey.get(key)

    ipos.push({
      id: key,
      name: row.name,
      symbol: '',
      board: override.board ?? row.board ?? 'SME',
      status: deriveStatus(openDate, closeDate, listingDate),
      priceMin: null,
      priceMax: num(override.priceMax ?? row.priceMax),
      lotSize: num(override.lotSize ?? row.lotSize),
      issueSizeCr: num(override.issueSizeCr ?? row.issueSizeCr),
      freshIssueCr: num(override.freshIssueCr),
      ofsCr: num(override.ofsCr),
      openDate,
      closeDate,
      allotmentDate: toIsoDate(override.allotmentDate ?? row.allotmentDate),
      refundDate: toIsoDate(override.refundDate),
      listingDate,
      registrar: override.registrar ?? prev?.registrar ?? null,
      exchange: null,
      about: override.about ?? null,
      strengths: override.strengths ?? [],
      risks: override.risks ?? [],
      financials: override.financials ?? [],
      valuation: override.valuation ?? null,
      listingPrice: num(override.listingPrice),
      gmp: mergeGmpHistory(prev, num(override.gmp ?? row.premium), todayLabel, override),
      subscription:
        override.subscription ??
        (row.subscriptionTotal != null ? { total: row.subscriptionTotal } : null)
    })
  }

  // Keep recently-listed issues around for the "Listed" tab even after NSE drops them.
  const liveKeys = new Set(ipos.map((i) => i.id))
  const cutoff = Date.now() - 45 * 24 * 60 * 60 * 1000
  for (const old of previous.ipos ?? []) {
    if (liveKeys.has(old.id)) continue
    const listed = old.listingDate ? Date.parse(old.listingDate) : null
    if (listed && listed > cutoff) ipos.push({ ...old, status: 'LISTED' })
  }

  const envelope = {
    updatedAt: new Date().toISOString(),
    ipos
  }

  const json = JSON.stringify(envelope, null, 2)
  if (dryRun) {
    process.stdout.write(json + '\n')
    return
  }

  await mkdir(path.join(here, 'public'), { recursive: true })
  await writeFile(path.join(here, 'public/ipos.json'), json + '\n', 'utf8')
  console.error(`✓ wrote public/ipos.json (${ipos.length} IPOs)`)
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
