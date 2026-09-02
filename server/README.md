# IPO Radar — free data feed

The Android app reads one file: **`ipos.json`**. This folder builds that file for free.

```
NSE public API  ─┐
GMP source      ─┼─►  scrape.mjs  ─►  public/ipos.json  ─►  GitHub Pages  ─►  app
data/overrides  ─┘
```

Cost: ₹0. GitHub Actions gives unlimited minutes on public repos, and GitHub Pages
hosts the JSON for free.

## Setup (one time, ~10 minutes)

1. Create a **public** GitHub repo, e.g. `ipo-feed`.
2. Copy everything inside this `server/` folder to the **root** of that repo.
3. Push. Then in the repo: **Settings → Pages → Source = Deploy from branch → `main` / root**.
4. Your feed is now live at one of:
   - `https://<user>.github.io/ipo-feed/public/ipos.json`
   - `https://raw.githubusercontent.com/<user>/ipo-feed/main/public/ipos.json`
5. Put the **directory** part of that URL (with trailing slash) into
   `app/build.gradle.kts` → `FEED_BASE_URL`. For example:

   ```kotlin
   buildConfigField(
       "String",
       "FEED_BASE_URL",
       "\"https://raw.githubusercontent.com/<user>/ipo-feed/main/public/\""
   )
   ```

6. Run the workflow once by hand: **Actions → Build IPO feed → Run workflow**.

## Running it locally

```bash
npm install
node scrape.mjs --dry-run   # prints the feed
node scrape.mjs             # writes public/ipos.json
```

### Testing the app against a local feed

Debug builds already point at `http://10.0.2.2:8787/` (that address is how the Android
emulator reaches your machine), and a debug-only network config allows plain HTTP to it.
So a full local loop is:

```bash
cd server
node scrape.mjs
cd public && python -m http.server 8787
```

Then run the debug app — no GitHub repo needed. Release builds use the
`FEED_BASE_URL` set in the `release` block and keep cleartext HTTP disabled.

## What comes from where

| Field | Source | Notes |
|---|---|---|
| Company, symbol, board, price band, open/close dates | NSE public API | `ipo-current-issue`, `all-upcoming-issues` |
| Closed & listed issues | NSE public API | `public-past-issues` |
| **Lot size, registrar** | NSE public API | `ipo-detail?symbol=X` → `issueInfo.dataList` |
| **Subscription split** (QIB / bNII / sNII / retail) | NSE public API | `ipo-detail?symbol=X` → `activeCat.dataList` |
| Allotment / refund dates | `data/overrides.json` | NSE does not publish these |
| Fresh issue vs OFS split, about | `data/overrides.json` | From the RHP cover page |
| Financials, valuation, strengths, risks | `data/overrides.json` | **Prospectus content — no API exists, free or paid** |
| GMP, Kostak, Subject to Sauda | `config.json` source, or overrides | See below |

The app now reads lot size, registrar and the category-wise subscription split straight
from NSE, so those work without any feed at all. What the feed still adds is GMP and
everything that only exists inside the prospectus PDF.

Everything sourced from `data/overrides.json` is hand-entered. That is not a shortcut
we took — the RHP is a PDF, and no free service exposes its numbers as data. Budget
about 10 minutes per IPO if you want the financials and valuation cards populated.
Leave them out and the app simply hides those sections.

## About GMP — read this before you rely on it

Grey Market Premium is an **unofficial, unregulated** number traded off-exchange.
There is no official API for it anywhere, free or paid — SEBI does not recognise it.
A few websites publish their own GMP tables, and that is the only place the number exists.

So GMP comes from a **list** of sources in `config.json`, tried in order. Each runs
independently — one being down, slow or restructured never blocks the others, and the
run just produces fewer fields. Where several sources report the same company, the
first one listed wins and the rest only fill gaps.

`sources[0]` ships configured for **investorgain.com**, whose `robots.txt` is
`User-agent: * / Allow: /`. Its report endpoint also carries lot size, price, issue
size, subscription, P/E and — importantly — the **allotment and listing dates**, which
NSE does not publish anywhere.

To add a second source (recommended, so one site going down does not cost you GMP):

1. Pick a site you are permitted to read. Check its `robots.txt` first — some
   explicitly disallow automated agents, and that should be respected.
2. Open its GMP page in Chrome → **DevTools → Network → Fetch/XHR** → reload.
3. Find the request whose response holds the GMP rows, right-click → **Copy → Copy as cURL**
   (or just copy the **Request URL** from the Headers tab).
4. Paste the URL into the matching entry in `config.json` and set `enabled: true`.
   Chittorgarh runs the same reporting platform as InvestorGain, so `type: "report-json"`
   parses it as-is. Anything else can use `type: "html-table"`.
5. Verify with `node scrape.mjs --dry-run` before scheduling.

If every source fails, the scraper keeps the previous run's values rather than wiping
them, and the app hides whatever is missing. Nothing breaks.

**Two things to keep in mind.** These are unofficial endpoints, not published APIs —
they can change without notice, so if GMP suddenly goes flat, re-capture the URL with
the steps above. And robots.txt permitting a crawler is not the same as a licence to
redistribute someone's data in a published app: check the source's terms of use, and
get permission, before you ship this to the Play Store.

Be a good citizen: the workflow runs every 2 hours, not every minute. Don't lower it.

## Filling in the gaps by hand

`data/overrides.json` is keyed by the IPO's id (symbol, lowercased and hyphenated —
the same `id` you'll see in `public/ipos.json`):

```json
{
  "vantamob": {
    "registrar": "Link Intime India Pvt Ltd",
    "allotmentDate": "2026-09-03",
    "refundDate": "2026-09-04",
    "about": "Electric two-wheeler manufacturer...",
    "gmp": 62
  }
}
```

Anything set here wins over the scraped value. Registrar names are matched loosely
in the app (`Registrar.match`), so "Link Intime", "MUFG Intime" and
"Link Intime India Pvt Ltd" all resolve to the same allotment page.

## Feed schema

```jsonc
{
  "updatedAt": "2026-09-01T04:00:00.000Z",
  "ipos": [
    {
      "id": "vantamob",              // stable key, also used by the app
      "name": "Vanta Mobility Ltd",
      "symbol": "VANTAMOB",
      "board": "MAINBOARD",          // or "SME"
      "status": "OPEN",              // UPCOMING | OPEN | CLOSED | LISTED
      "priceMin": 246,
      "priceMax": 259,
      "lotSize": 57,
      "issueSizeCr": 1240.5,
      "freshIssueCr": 900,           // money that goes to the company
      "ofsCr": 340.5,                // money that goes to selling shareholders
      "openDate": "2026-08-31",      // ISO yyyy-mm-dd throughout
      "closeDate": "2026-09-02",
      "allotmentDate": "2026-09-03",
      "refundDate": "2026-09-04",
      "listingDate": "2026-09-05",
      "registrar": "Link Intime India Pvt Ltd",
      "exchange": "BSE, NSE",
      "about": "...",
      "listingPrice": null,          // set once listed, drives listing-gain %
      "strengths": ["One bullet per string"],
      "risks": ["One bullet per string"],
      "financials": [                // ₹ crore; any number of periods
        { "period": "FY26", "revenueCr": 3455, "patCr": 196,
          "netWorthCr": 1024, "borrowingsCr": 431 }
      ],
      "valuation": {
        "peRatio": 34.2, "industryPe": 41.8,
        "eps": 7.57, "ronwPct": 19.1, "marketCapCr": 6702
      },
      "gmp": {
        "premium": 62,
        "kostak": 850,               // flat price per application
        "subjectToSauda": 5200,      // paid only if the application is allotted
        "updatedAt": "1/9/2026, 9:15:00 am",
        "history": [{ "date": "31 Aug", "premium": 58 }]
      },
      "subscription": {
        // Give niiBig/niiSmall when you have the split, else just nii.
        "qib": 3.42, "niiBig": 11.24, "niiSmall": 6.58,
        "retail": 5.16, "employee": 2.03, "total": 5.28,
        "updatedAt": "1 Sep, 5:00 PM"
      }
    }
  ]
}
```

Every field except `id` and `name` is optional — the app renders "—" for whatever
is missing rather than failing.
