# Project Plan: Open Health Ring & Body Composition App

**Working title:** FatTrack
**Platform:** Android (Kotlin)
**License:** GPL (open-source) — enables direct reuse of Gadgetbridge & openScale code
**Distribution:** GitHub Releases (Obtainium + manual sideload); no app stores
**Goal:** Replace QRing for the Colmi R10 ring, with Health Connect sync, body-composition scale support, and a future WaterRower PowerPulley integration.
**Design principles:** Local-first, privacy-respecting, no mandatory cloud account, granular user control over what syncs.

---

## 1. Scope

### Target hardware
- **Ring: Colmi R10** (confirmed) — exposes **temperature ✓** and **HRV ✓**, so no
  feature-detection fallback is required for your unit. Keep detection logic in place
  anyway to allow optional R02 support later.
- **Scale: Omron HBF-702T** — or another scale with a mature openScale body-composition driver.

### In scope (v1.0)
- Colmi R10 BLE connectivity + metric logging (steps, HR, HRV, SpO2, temperature, sleep)
- Local persistence (SQLite / Room)
- Health Connect integration with per-metric sync toggles
- Manual weight entry
- Smart scale sync (weight, body fat %, body water %)

### Future / out of scope for v1.0
- WaterRower PowerPulley (SmartRow) integration
- Ring firmware updates
- iOS version
- Cloud backup / multi-device sync

---

## 2. Scale Decision

**Primary target: Omron HBF-702T** — already supported in openScale via the
`OmronWlcHandler` (BLE GATT), with body metrics ✓ and history ✓. The same handler drives
a family of Omron scales (BCM-500, HBF-222T/227T/228T/230T, VIVA), so it is well-exercised
and GPL-compatible for direct reuse.

**Fallback rule:** if the HBF-702T is unavailable, substitute **only** a scale with a
mature, body-composition-capable openScale driver, e.g.:

| Brand | Models (openScale-supported) | Body fat % | Body water % |
|---|---|---|---|
| Omron | HBF-702T, HBF-222T/227T/228T/230T, BCM-500, VIVA | ✓ | ✓ |
| Beurer/Sanitas | BF500, BF700, BF710, BF800, BF1000, SBF70/77 | ✓ | ✓ |
| Xiaomi | Mi Body Composition v1/v2 | ✓ | ✓ |

> ⚠️ Avoid Beurer **BF990** (WiFi + HealthManager Pro, *not* in openScale) and any Tanita
> consumer model (proprietary, bonding-gated, unfinished). These require self-reverse-engineering.

**Design-around note:** the Omron handler lists time-sync / user-sync / unit-config as "n/a" —
manage user profiles app-side rather than pushing them to the scale.

---

## 3. Tech Stack

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin | |
| UI | Jetpack Compose | Modern, less boilerplate |
| Architecture | MVVM + Repository pattern | Clean separation of BLE / DB / UI |
| Async | Coroutines + Flow | BLE notifications map well to Flow |
| BLE | Nordic Android BLE Library | chosen for peripheral robustness and queued GATT ops. |
| Local DB | Room (SQLite) | Mirror schemas from reference projects |
| Health sync | Health Connect SDK | `androidx.health.connect:connect-client` |
| DI | Hilt | |
| Charts | Vico or MPAndroidChart | Trends/history views |
| Build/CI | GitHub Actions | Build + sign + attach APK on tag |
| Min SDK | 26+ (verify current Health Connect reqs) | |

---

## 4. Reference Material (reuse under GPL)

> Project is GPL, so reusing the GPL code below is fully compatible.

| Source | License | Use |
|---|---|---|
| **Gadgetbridge** (Android) | GPL | Colmi ring driver — most valuable, already Kotlin/Java |
| **tahnok/colmi_r02_client** (Python) | — | Best protocol docs + DB schema reference |
| **Puxtril/colmi-docs** | — | Protocol packet documentation |
| **openScale** (Android) | GPL | `OmronWlcHandler` scale driver + others |

---

## 5. Architecture Overview

```
┌──────────────────────────────────────────────┐
│                   UI (Compose)               │
│   Dashboard · History · Devices · Settings   │
└───────────────────┬──────────────────────────┘
                    │ (ViewModels / StateFlow)
┌───────────────────▼───────────────────────────┐
│                 Repositories                  │
│   RingRepo · ScaleRepo · WeightRepo · HCRepo  │
└──────┬──────────────┬───────────────┬─────────┘
       │              │               │
┌──────▼─────┐ ┌──────▼──────┐ ┌──────▼────────┐
│ BLE Layer  │ │  Room DB    │ │ Health Connect│
│(Ring+Scale)│ │  (local)    │ │  Client       │
└────────────┘ └─────────────┘ └───────────────┘
```

**Data flow:** Device (BLE) → parsed in BLE layer → stored in Room (source of truth) →
optionally pushed to Health Connect based on user toggles.

---

## 6. Data Model (Room entities)

```
Device(id, type[RING|SCALE], name, address, lastSync)

StepRecord(id, timestamp, steps, distance, calories)
HeartRateRecord(id, timestamp, bpm)
HrvRecord(id, timestamp, rmssd)
Spo2Record(id, timestamp, percent)
TempRecord(id, timestamp, celsius)
SleepRecord(id, start, end, stage)

WeightRecord(id, timestamp, weightKg, bodyFatPct, bodyWaterPct,
             muscleMassKg, boneMassKg, source[MANUAL|SCALE])

UserProfile(id, name, heightCm, birthDate, sex, activityLevel)   // app-side, for Omron
SyncState(recordType, lastHealthConnectSync, enabled)
```

---

## 7. Health Connect Mapping

| App metric | Health Connect record type | Notes |
|---|---|---|
| Steps | `StepsRecord` | |
| Heart rate | `HeartRateRecord` | |
| HRV | `HeartRateVariabilityRmssdRecord` | |
| SpO2 | `OxygenSaturationRecord` | |
| Temperature | `BodyTemperatureRecord` | |
| Sleep | `SleepSessionRecord` | |
| Weight | `WeightRecord` | |
| Body fat % | `BodyFatRecord` | |
| **Body water %** | ⚠️ No native type | Store locally; no HC equivalent |

**Granular control:** one toggle per record type in Settings, backed by `SyncState`. Only
sync enabled types; track `lastHealthConnectSync` to avoid duplicates.

---

## 8. Milestones / Phases

### Phase 0 — Foundation (1–2 weeks)
- Project scaffold, Hilt, Compose navigation
- Room DB + entities
- BLE permission flow (Android 12+ `BLUETOOTH_SCAN`/`CONNECT`)
- Device scan + pairing UI skeleton
- GitHub repo set up (GPL license, README stub)

### Phase 1 — Colmi R10 Core (3–5 weeks)
- Connect to R10, implement 16-byte command protocol
- Read: steps, HR, SpO2, HRV, temperature, sleep (all confirmed supported on R10)
- Historical data sync (batch read)
- Persist to Room
- **Mitigate battery drain** — tune polling/connection intervals (known issue)
- Dashboard showing live + recent data

### Phase 2 — Health Connect (1–2 weeks)
- Integrate SDK, request permissions
- Per-metric sync toggles + `SyncState`
- Background sync worker (WorkManager), dedup logic

### Phase 3 — Weight & Body Composition (2–3 weeks)
- Manual weight/body-fat/water entry UI
- `UserProfile` management (height/age/sex/activity) — needed app-side for Omron BIA
- Port `OmronWlcHandler` from openScale (BLE GATT)
- Decode weight, body fat %, body water %, muscle, bone
- Push weight + body fat to Health Connect; keep water local
- **Test task:** confirm user/profile handling (Omron handler has no on-scale user sync)
- Weight trend charts

### Phase 4 — Polish & Release (2 weeks)
- History/trends views, data export (CSV/JSON)
- Settings, onboarding, error handling
- Testing on real hardware (R10 + scale)
- **Distribution via GitHub:**
  - Public GPL repo with source + build docs
  - Tagged **GitHub Releases** with signed APKs attached
  - **Obtainium**-friendly: users add the repo URL; Obtainium tracks Releases for auto-updates
  - Document manual sideload steps in README
  - No Play Store / F-Droid submission (avoids Play HC review + F-Droid build pipeline)
- **CI:** GitHub Actions workflow — build + sign release APK on tag push, auto-attach to the Release

### Phase 5 — Future: WaterRower PowerPulley
- Research SmartRow BLE protocol (HCI snoop + Wireshark)
- Determine standard FTMS vs proprietary SmartRow
- Prototype connection, log stroke/power/distance
- Treat as R&D spike, not a port (no known mature OSS client)

---

## 9. Key Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Ring battery drain from polling | Medium | Careful connection interval / batch sync, learn from Gadgetbridge |
| R10 firmware variant breaks protocol | Medium | Test against your unit; version-detect |
| Omron needs app-side user profile | Known | Build `UserProfile`; don't rely on scale-side sync |
| Chosen scale not actually in openScale | Low | Restrict to confirmed-supported models only |
| Health Connect API changes | Low | Pin SDK version, follow release notes |
| Lost/changed signing key breaks updates | Medium | Keep a stable release keystore from day one; back it up |
| SmartRow protocol undocumented | High | Keep in future phase; time-box research |

---

## 10. Decisions — Status

1. **License:** ✅ GPL (open-source) — reuse Gadgetbridge + openScale.
2. **Scale:** ✅ Omron HBF-702T (or another mature openScale body-composition driver).
3. **Ring hardware:** ✅ Colmi R10 — temperature and HRV both supported.
4. **Distribution:** ✅ GitHub Releases (Obtainium + manual sideload). No app stores.

---

## 11. Distribution & Update Notes (GitHub / Obtainium)

- **Signing:** use a stable release keystore from the first release. Obtainium and Android
  both reject updates signed with a different key — a lost/changed key forces users to
  uninstall/reinstall. Back the keystore up securely.
- **Obtainium:** it reads the repo's Releases, so attach the APK directly to each tagged
  release (not just committed in a folder). A consistent APK filename aids version matching.
- **Health Connect:** skipping the Play Store avoids its data-use review entirely — make sure
  the HC permissions rationale is clear in-app.
- **README:** document both install paths (Obtainium repo URL + manual APK sideload) and the
  minimum Android / Health Connect requirements.

---

## 12. Definition of Done (v1.0)

- [ ] R10 connects reliably; all metrics (steps, HR, HRV, SpO2, temp, sleep) sync to local DB
- [ ] Battery impact acceptable over 24h use
- [ ] Per-metric Health Connect toggles work and dedup correctly
- [ ] `UserProfile` drives scale body-composition calc correctly
- [ ] Manual + scale weight/body-fat/water captured and charted
- [ ] Data export works
- [ ] Tested on physical R10 + scale
- [ ] GitHub Actions builds + signs + attaches APK on tag
- [ ] Public GPL repo with build/setup docs and install instructions
- [ ] Obtainium install verified end-to-end
