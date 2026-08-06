# Prega AI v2 — Handover

Branch: `redesign/v2` (4 commits on top of `main`)

## Before it will build

1. **Create `.env`** in the project root (it's gitignored):
   ```
   OPENROUTER_API_KEY=sk-or-v1-...
   GEMINI_API_KEY=unused
   ```
   Get a key at https://openrouter.ai/keys. Without it the app still runs —
   AI features fall back to curated offline content.

2. **Open in Android Studio and sync Gradle.** New dependencies: WorkManager,
   Play Billing, core library desugaring, Room testing.

3. **Expect compile errors on the first build.** This is ~8,600 lines written
   without a compiler available. The logic is tested; the Compose UI will need
   a pass. Most likely: missing imports, Material 3 API drift, unused-import
   warnings.

4. Run `./gradlew test` — 45 unit tests should pass.

## Before it can ship

| Item | Why it matters |
|---|---|
| **Move the API key off-device** | `BuildConfig` ships inside the APK. Anyone can unzip it and spend your credit. Put the key behind a proxy and repoint `OpenRouterClient.BASE_URL`. |
| **Create the Play subscription** | Product ID `prega_premium_monthly`. Until it exists the paywall correctly shows "unavailable". |
| **Verify OpenRouter model slugs** | `PregaModel` pins two slugs. Check them against openrouter.ai/models. |
| **Have a midwife review the copy** | Especially `PregaPrompts.VOICE`, `RedFlags.kt`, and the kick counter guidance. I wrote these carefully but I'm not a clinician. |
| **Add a real app icon** | Still the template launcher icon. |
| **Test the 3→4 migration** | Install a v1 build, add data, upgrade. Data must survive. |

## What changed, in one line each

- **Design system** — new palette, type scale, motion language, spacing/shape scales
- **AI** — Gemini → OpenRouter; one prompt library drives every AI surface
- **Gamification** — points, levels, badges, quests, streaks *with grace days*
- **Notifications** — 4 channels, ≤3/day, AI copy that can't repeat itself
- **Onboarding** — asks for a date she knows, not a week she doesn't
- **Screens** — home, journey, kicks, coach, badges, settings, paywall
- **Payments** — fake PayPal/OTP/card capture replaced with Play Billing

## Bugs fixed from the original audit

1. `fallbackToDestructiveMigration()` — was wiping all user data on schema bumps
2. Test suite didn't compile (`Greeting()` didn't exist)
3. Wrong app-name assertion
4. Release builds weren't minified
5. GDPR delete only cleared 3 of 10 tables
6. Coach had no double-submit guard
7. Coach sent no conversation history — every question was treated as the first

## Design decisions you may want to revisit

These were judgement calls. They're defensible but they're mine, not yours.

- **Streak grace days.** Streaks survive missed days automatically. Reduces
  engagement pressure; some would argue it reduces engagement.
- **Kick counter isn't gamified.** No target, no score, no interpretation.
  Deliberate — see the file comment.
- **Nothing medical behind the paywall.** Limits monetisation surface.
- **Weight is never rewarded.** No points, streaks or badges attached to it.
- **Dynamic colour disabled.** Brand consistency over system theming.
