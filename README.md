# IPO Radar

India ke IPOs — list, GMP tracker, subscription status, allotment aur alerts.
Kotlin + Jetpack Compose, Material 3, fully free data sources.

Reference app: IPOwiz (IPO GMP & Allotment).

---

## Machine setup (ye pehle karna hoga)

Is PC pe abhi **Java, Android SDK aur Android Studio kuch bhi installed nahi hai**,
isliye project abhi build nahi hua hai. Ek baar setup kar lo:

1. **Android Studio** download karo: <https://developer.android.com/studio>
   (Windows `.exe`, ~1.2 GB). Install karte waqt "Android SDK", "SDK Platform-Tools"
   aur "Android Virtual Device" — teeno checked rakhna.
2. Android Studio kholo → **Open** → ye folder select karo:
   `C:\Users\Magnet Brains\AndroidStudioProjects\IPORadar`
3. Pehli baar Gradle sync chalega (10–20 min, internet chahiye). Studio khud
   Gradle wrapper aur saari dependencies download kar lega.
4. **Tools → SDK Manager** me check karo ki **Android 15 (API 35)** platform
   installed hai (compileSdk 35 chahiye).
5. Upar dropdown me device chuno (emulator ya USB-connected phone with USB debugging)
   aur ▶ **Run** dabao.

APK banane ke liye: **Build → Build Bundle(s)/APK(s) → Build APK(s)**.
File yahan milegi: `app/build/outputs/apk/debug/app-debug.apk`

---

## Features (v1 — jo maanga tha, sab hai)

| Feature | Kahan |
|---|---|
| IPO list — Open / Upcoming / Closed / Listed tabs, Mainboard vs SME filter, search | `ui/home/HomeScreen.kt` |
| IPO detail — price band, lot size, min investment, sHNI/bHNI lots, fresh issue vs OFS, timeline | `ui/detail/IpoDetailScreen.kt` |
| Financials — Revenue / PAT / Net worth / Borrowings, year by year | `ui/detail/IpoDetailScreen.kt` |
| Valuation — P/E vs industry P/E, EPS, RoNW%, market cap | `ui/detail/IpoDetailScreen.kt` |
| Strengths & risk factors from the RHP | `ui/detail/IpoDetailScreen.kt` |
| GMP tracker — GMP, Kostak, Subject to Sauda, expected listing price, gain %, profit/lot, trend chart | `ui/gmp/GmpScreen.kt`, `ui/components/Charts.kt` |
| Subscription — QIB / bNII / sNII / Retail / Employee bars | `ui/components/Charts.kt` |
| PAN vault — multiple family PANs with relationship tags, tap to copy | `ui/allotment/AllotmentScreen.kt` |
| Allotment — registrar deep links (Link Intime, KFin, Bigshare, Maashitla, Skyline, Cameo, Purva) | `ui/allotment/AllotmentScreen.kt` |
| Notifications — IPO open, closing reminder, allotment day, listing day, big GMP moves; watchlist-only mode | `notif/IpoSyncWorker.kt` |
| Watchlist, dark/light/system theme, offline cache | `data/local/AppPrefs.kt` |

---

## Architecture

```
UI (Compose)  ──►  IpoViewModel  ──►  IpoRepository  ──┬─►  FeedApi   (apna free JSON feed)
                                                       ├─►  NseApi    (NSE public API)
                                                       └─►  AppPrefs  (DataStore cache)
```

- **DI**: hand-written `ServiceLocator` — koi Hilt/KSP nahi, isliye build fast hai.
- **Network**: Retrofit + OkHttp + kotlinx.serialization.
- **Storage**: DataStore Preferences (watchlist, settings, offline snapshot) — Room ki
  zaroorat nahi padi kyunki dataset chhota hai.
- **Background**: WorkManager har 3 ghante refresh karke local notifications deta hai.
  Koi push server nahi, isliye koi server bill nahi.
- **Fallback chain**: live feed → last good snapshot → bundled `assets/ipos_sample.json`.
  App kabhi blank nahi dikhta.

---

## Data — sab free

Do sources, dono free, koi API key nahi:

1. **NSE public API** (`www.nseindia.com/api/...`) — official aur free, koi key nahi:
   - `ipo-current-issue` + `all-upcoming-issues` → open/upcoming IPOs
   - `public-past-issues` → closed aur listed IPOs
   - `ipo-detail?symbol=X` → **lot size, registrar, aur category-wise subscription
     (QIB / bNII / sNII / Retail / Total)** — ye sirf isi endpoint me milta hai
   
   NSE bina browser-jaisi request ke block kar deta hai, isliye
   `core/net/NseInterceptors.kt` pehle session cookie leta hai.
2. **Apna JSON feed** — GMP, allotment/refund dates, company description.
   `server/` folder me poora scraper + GitHub Actions workflow ready hai jo GitHub ke
   free tier pe chalta hai. Setup ke liye [`server/README.md`](server/README.md) padho.

> **GMP ke baare me honest baat**: GMP ka koi official ya free API duniya me nahi hai —
> na free, na paid. Ye ek unregulated grey-market number hai jo kuch websites apne
> hisaab se publish karti hain. Isliye scraper generic banaya hai: aap jis source ko
> legally read kar sakte ho (uska robots.txt aur terms check karke) usko `config.json`
> me point kar do. Bina GMP source ke bhi app poori tarah chalta hai — bas GMP ke
> numbers hide ho jaate hain.

**Feed URL set karna zaroori hai.** Abhi `app/build.gradle.kts` me placeholder hai:

```kotlin
buildConfigField("String", "FEED_BASE_URL", "\"https://raw.githubusercontent.com/CHANGE-ME/ipo-feed/main/\"")
```

Isko apne feed ke URL se replace karo. Jab tak nahi karte, app NSE data + bundled
sample pe chalta rahega.

---

## Play Store pe daalne se pehle

- [ ] `applicationId` badlo (`com.iporadar.app` → apna unique id) — `app/build.gradle.kts`
- [ ] App ka naam badlo — `res/values/strings.xml`
- [ ] Launcher icon replace karo — `res/drawable/ic_launcher_foreground.xml`
- [ ] Release signing key banao: **Build → Generate Signed Bundle / APK**
- [ ] Privacy policy chahiye hogi (app notification permission maangta hai)
- [ ] Play Console me **Finance** category select karo
- [ ] Store listing me clearly likho: "information only, not investment advice" —
      finance apps pe Google iski checking karta hai. App ke andar disclaimer already hai
      (detail screen, GMP tab aur Settings me).

---

## Agla step (v2 ideas)

- Broker apps (Zerodha/Groww/Upstox) me "Apply" deep link
- IPO calculator: kitne lots pe kitna profit at different listing prices
- Allotment probability estimate based on subscription numbers
- Buyback / NCD / Rights issue tabs
- Firebase Cloud Messaging agar instant push chahiye (WorkManager 3-ghante wala hai)
