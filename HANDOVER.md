# Prega AI — Project Handover

Updated: 13 Sep 2026 · branch `redesign/v2` · DB v9 · covers everything through the
Japanese garden, "in my room", icons, Birth club UX, and CI.

## 1. What this is

Prega AI — an Android pregnancy companion (Kotlin + Jetpack Compose) built by Punkesh
(non-developer, Gurugram) with Claude as the engineering team. Repo:
github.com/punkesh93/Prega_AI, branch `redesign/v2` only. Website:
punkesh93.github.io/Prega_AI (served from `docs/`).

**Product identity (locked, ratified repeatedly):** Calm. Private. Useful. Never make
pregnancy feel like a productivity competition.

**Hard rules** that override any future request until explicitly re-decided by the owner
with the tradeoff stated back to them:

- No XP / levels / streak pressure. The Bloom Garden (grows, never wilts) is the
  progression system.
- The app never diagnoses, never says "you're in labor," never interprets symptoms or
  contraction patterns. It shows neutral numbers, educates, and points to her care team.
- No homemade medical decision trees until real clinical review exists.
- Local-first is sacred. Journal, moods, weights, photos, kicks, contractions, provider
  details — on-device only. Cloud = only what she explicitly shares.
- Home stays "Today, gently" — new features live on their own surfaces, never as new
  Home cards.
- Brand: olive/linen/rose, serif display, line-art mother, **Prega's own line-art icons**
  (`ui/icons/PregaIcons.kt`). The garden is a **painted film, not a photograph** — the
  owner asked for photoreal on 13 Sep and, with the tradeoff stated (brand split, +60–150 MB
  engine, GPU heat, new crash surface), accepted the drawn ceiling. Don't reopen without
  new information.

## 2. Current state

| Area | Status |
|---|---|
| Latest commit | see `git log`; the Actions tab is the truth |
| CI | ✅ Live: `.github/workflows/build.yml` — tests then assembleDebug on every push; APK artifact per run; **publish is manual** (Actions → Build → Run workflow → tick publish). Compile errors are surfaced as annotations. |
| `OPENROUTER_API_KEY` secret | ⚠️ Must be added in repo Settings → Secrets → Actions, or CI APKs ship with a placeholder key (build still passes; AI features 401). |
| Device test pending | Everything from 12–13 Sep (§8). Two DB migrations (v7→v9) ride along — install over the top. |
| Firebase | ✅ Anonymous auth + Firestore (asia-south1). Rules published. |
| Releases | ⚠️ No release has ever been published. First publish activates the website Download button and in-app update cards. |
| OpenRouter | Free tier, 50 req/day (hit once). $10 top-up → 1,000/day recommended before distribution. |
| Little One images | 3 of 40 (weeks 08/20/32). Nearest-week fallback covers all weeks. |

## 3. Architecture map

```
app/src/main/java/com/example/
├── MainActivity.kt            crash-file check first; schedules reminders on EVERY launch
├── PregaApplication.kt        UncaughtExceptionHandler → files/last_crash.txt
├── ai/                        OpenRouterService (404→fallback), PregaPrompts, SpeechService
├── community/                 CommunityRepository (Auth+Firestore), CommunityScreen
├── data/                      Room v9, Migrations 3→9 (additive only), Gamification.kt
├── notifications/             Channels, Scheduler (+sendTestNow), Worker, NotificationPermission
├── stats/AppStats.kt          Firebase Analytics, 21 events, privacy contract in-file
├── update/UpdateChecker.kt    GitHub releases API; expects asset named exactly prega-ai.apk
├── ui/
│   ├── PregnancyApp.kt        HorizontalPager of 5 tabs + overlays; youActive gates garden audio
│   ├── icons/PregaIcons.kt    18 ImageVectors from SVG path data (tabs + drawer)
│   ├── garden/
│   │   ├── GardenScreen.kt    flat diorama (now with lantern/pond/maple/etc.), share, film
│   │   ├── GardenWorld.kt     the stroll garden: camera, objects, drawers, frame renderer
│   │   ├── GardenWalk.kt      first-person walk: controls, gyro, audio, "in my room"
│   │   └── GardenAmbience.kt  wind/birds, gated on `active`
│   ├── labor/                 ContractionMath (pure, tested), timer + Labor Mode
│   ├── appointments/          Appointments + QuestionBank
│   └── …home, coach, kicks, journal, journey, settings, onboarding, paywall
└── viewmodel/PregnancyViewModel.kt
app/src/test/…                 FakeDao (must track PregnancyDao), ContractionMathTest, etc.
.github/workflows/build.yml    CI
docs/index.html                website + privacy policy (camera paragraph added 13 Sep)
```

## 4. Build & release

**CI (primary).** Every push compiles and tests. Red X → open the run → the annotations
name the file:line. Publishing a release is deliberately manual so no unreviewed build
ever OTA-prompts users: Actions → Build → Run workflow → tick **publish**. The release
tag is `build-<7-char hash>` and the asset is `prega-ai.apk` — both are contracts with
UpdateChecker and the website; do not rename.

**Colab (fallback).** Cells 1–7 still work. Cell 7 needs a token with Contents: RW.

**OTA.** Each build bakes `BuildConfig.GIT_HASH`; the app checks the latest release
daily; newer → sage "fresh version" card on Home.

## 5. Debugging

- Crash flight recorder: any uncaught exception → next launch shows a copyable trace.
- CI annotations: Kotlin `e: file:line` and Gradle "What went wrong" appear on the run.
- Pre-commit sweeps (Claude-side, mandatory): brace/paren/quote scan, duplicate
  @Composable, unused-import check, signature grep against real code, escaped `\uXXXX`
  for emoji in Kotlin, render-and-view for anything visual.
- Unit tests: `FakeDao` must implement every `PregnancyDao` member or the whole test
  source set fails to compile (this silently disabled ContractionMathTest for weeks).

## 6. Secrets

| Secret | Status | Action |
|---|---|---|
| GitHub fine-grained PAT (created 13 Sep, Contents RW + Workflows RW) | Active; **pasted in chat** | Revoke when this round is done; CI needs no PAT. |
| Old PAT `github_pat_11AZTL…` (Aug) | Dead (401) | Delete from the token list. |
| OpenRouter `sk-or-v1-e868…` | Active, ships in APK via `.env` | Add as repo secret; rotate at leisure; proxy before scale. |
| Airtable PAT | Retired | Revoke. |
| Debug keystore | Committed for dev builds | Real release keystore before Play. |

## 7. What shipped 12–13 Sep (all approved by the owner)

1. **Garden audio only where she sees the garden.** Pager pre-composition and overlays
   kept the You page alive; `youActive` (settled page ∧ no overlay) now gates ambience
   and ends a walk.
2. **Reminders schedule on every launch**; Android 13 permission asked once; permanently
   denied → system settings; **Send a test reminder** in Settings.
3. **Garden walk v2→v3.** Free camera (position, heading, pitch), drag-up to stroll with
   momentum, drag sideways to turn, gyroscope look-around (toggle, remembered). Then the
   Japanese stroll garden: lantern, koi pond + irises, maples, bamboo, mossed rocks,
   torii, azaleas, stepping stones, mist mountains, light, petals. Repeats every 60 m.
4. **"In my room"**: live CameraX preview behind the drawn garden. Opt-in toggle on the
   walk; permission asked only on tap; Preview use case only; privacy policy updated.
5. **Flat garden** shares the vocabulary (diorama). Share PNG and film inherit it.
6. **Birth club**: flicker fixed (flows were rebuilt per keystroke); member row with
   honest count; new-club empty state makes Invite the answer; chat bubbles/day dividers.
7. **Icons**: 18 of Prega's own; tabs and drawer.
8. **CI** written, fixed for a missing secret, annotations; `FakeDao` repaired.

## 8. Test scripts

**Garden (new):** You → Garden: lantern, pond with a koi that drifts, maple, bamboo at
right edge, stepping stones, two azaleas — her flowers still the hero. Sound only here;
swipe to Prega AI → silence; open Journal over it → silence. "Walk in my garden": drag up
walks (footsteps follow distance), release glides then stops; sideways turns; full turn
shows the sun crossing and the path behind; walk ~20 m → pass under the torii; find the
lantern, the pond, a visitor at a station. Gyro toggle top-right. Camera toggle (second
button): first tap asks permission; grant → room behind the garden, ground translucent;
deny → nothing changes. Leave the walk mid-stroll by swiping tabs → walk ends, audio
stops. Watch frame rate on an older phone — reduce `WALK_FAR` or the 230-bloom budget
if it stutters.

**Birth club:** brand-new month → empty state with big Invite → share sheet. Type in the
composer → no flicker. Second phone joins → member row shows two initials, "You and one
other mother". Messages: hers right/rose, others left, day divider, long-press report.

**Reminders:** Settings → Send a test reminder → notification within seconds.

**The 2 AM death test** (unchanged, still the release gate for the contraction timer).

## 9. Roadmap (locked order)

2 AM release remaining: 6. Doctor-confirmed due date · 7. Smart Hospital Bag · 8. Birth
Preferences. Then Journey "40-week river" (needs 37 Little One images) → Partner Mode
(Firebase account-linking model BEFORE UI) → Postpartum Bridge.

Pre-Play-Store: FCM push, release keystore, Crashlytics SDK+plugin together, OpenRouter
proxy (Supabase), Play Console (Data Safety form now includes Camera: optional, not
collected), photos-in-community.

Declined/deferred: true 3D engine or photoreal garden (accepted 13 Sep), ARCore (the
camera pass-through is the accepted middle path), homemade triage, belly-photo AI, XP.

## 10. Working-style rules (accumulated, mandatory)

- Fresh container: `git config user.name "Punkesh Kumar"`, email punkesh93@gmail.com;
  clone/fetch `redesign/v2` before touching anything; push with
  `https://x-access-token:<PAT>@github.com/punkesh93/Prega_AI.git`. Copy the PAT from the
  transcript, never retype. Check CI annotations after every push.
- Read real code and grep real signatures before calling anything.
- Verification sweep before EVERY commit, in the same script, before git.
- Visuals: prototype → render → inspect before shipping (PIL/Playwright).
- **Design changes: present mockups, get approval, then build** (13 Sep precedent).
- Push back with reasons when a request conflicts with locked philosophy/safety; execute
  if the owner overrules knowingly.
- Smallest excellent version first; detailed why-commits.
- Room: additive migrations only. New analytics events: StatEvent + call sites + privacy
  contract. New permissions: manifest + in-app policy + website policy, all three.
- User is outcome-focused: report exact hash + what to expect; bug reports arrive as
  screenshots; Colab code = only the python cell.

## 11. Watchlist

- Nothing from 12–13 Sep has been run on a device yet. CI proves it compiles, not that
  it looks right or runs at 60 fps.
- Gyroscope heading drifts slowly (dead-zone only); toggle off/on re-centres.
- Two DB migrations (v7→v9) untested over-the-top.
- Website `#watch` iframe still `VIDEO_ID_HERE`.
- 1,000/day OpenRouter cap is fine for testers, not scale.
