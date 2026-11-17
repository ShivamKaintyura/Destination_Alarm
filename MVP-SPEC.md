# Destination Alarm — MVP Spec

**One-liner:** Set a destination, sleep or zone out, and your phone rings when you get there.

**Problem:** Long metro/bus rides and commutes make people miss their stop. Existing alarms are time-based, not place-based. People set a "safe" alarm 10 minutes early and still worry.

**User story:** *"Main metro mein hoon, Kothrud station pe utarna hai. Phone silent hai. 20 min ka safar hai. App baje jab station ke paas pahunchun."*

---

## Non-goals (v1)

- No social features, no trip history, no user accounts
- No iOS launch (silent-alarm promise can't be met there — see Risks)
- No offline map downloads in v1 (trigger works offline anyway; tiles are cosmetic)
- No transit/route APIs (no "which metro line" logic)

---

## Core flow

```
Open app → pick destination → set radius + optional "X min before"
→ Start → app tracks in background → alarm rings on arrival → dismiss/snooze
```

## Screens

| Screen | Contents |
|---|---|
| **1. Destination picker** | Map (tap to drop pin) + search box with results list |
| **2. Setup** | Radius slider (200 m – 2 km), "Alarm before arrival" (0–10 min), alarm tone picker, Start button |
| **3. Active tracking** | Live map, distance to destination, ETA, small persistent notification (Android) |
| **4. Alarm** | Full-screen, loud looping alarm, destination name, Dismiss / Snooze 5 min |

## Trigger logic

Alarm fires when **either** condition is true:

1. `distance_to_destination <= radius` — **proximity trigger**
2. `speed > 1 m/s AND distance / speed <= minutes_before` — **pre-arrival trigger**

Guards:
- Ignore fixes with accuracy worse than 150 m for triggering (still show on map)
- If a fix is older than 45 s, mark ETA as stale
- Snooze = 5 min cooldown, then re-evaluate
- Dismiss = stop tracking (trip over)

## Permissions (Android)

| Permission | Why | When asked |
|---|---|---|
| `ACCESS_FINE_LOCATION` | Track position | Onboarding |
| `ACCESS_BACKGROUND_LOCATION` | Keep tracking with screen off | Onboarding, step 2 |
| `FOREGROUND_SERVICE_LOCATION` | Required by Android 14+ for background tracking | Install-time |
| `POST_NOTIFICATIONS` | Show tracking + alarm notification | Onboarding |
| `SCHEDULE_EXACT_ALARM` | Exact alarm firing | When first alarm set |
| DND access (`ACCESS_NOTIFICATION_POLICY`) | Alarm through Do Not Disturb | Optional, in settings |

UX rule: one permission per screen, plain-language reason, never a wall of dialogs.

## Risks & mitigations

| Risk | Reality | Mitigation |
|---|---|---|
| **No GPS underground** | Metro tunnels block satellite signal | Default the "X min before" option ON for transit; use last known position + speed to estimate; allow manual trigger |
| **Silent mode** | Android: alarm audio stream bypasses silent/DND ✅. iOS: silent switch mutes 3rd-party audio ❌ | Ship Android first; declare "Android only" for silent alarm |
| **Battery drain** | Continuous GPS is expensive | Adaptive sampling: every 30 s when far, every 5 s within 2 km |
| **Background kill** | OEMs (Xiaomi, Oppo, Vivo) kill background services | Foreground service + onboarding guide to disable battery optimization |
| **False alarms** | GPS drift near destination | Accuracy gate + radius minimum 200 m + require 2 consecutive fixes inside radius |

## MVP checklist

- [ ] Destination via map tap + search
- [ ] Radius setting
- [ ] "X min before" setting with live ETA
- [ ] Foreground tracking service
- [ ] Loud alarm that overrides silent (Android)
- [ ] Dismiss + 5 min snooze
- [ ] Permission onboarding
- [ ] Battery optimization guide screen

## Phase 2

- Saved places (Home, Office, favourite stations)
- Offline map tile caching
- Station database (auto-suggest "Kothrud station" style stops)
- iOS version (notification-based, no silent override)
- Wearable vibration alert

## Recommended stack

**Android:** Kotlin + Jetpack Compose + MapLibre (free) or Google Maps SDK + FusedLocationProvider + Foreground Service + AlarmManager + MediaPlayer on `STREAM_ALARM`.

**Prototype (now):** single-file HTML + **MapLibre GL** + **OpenFreeMap** tiles + **Photon** search + **OSRM** routing + Geolocation API + Web Audio.

### Free map stack (no key, no billing)

| Job | Service | Limits |
|---|---|---|
| Map render | OpenFreeMap `https://tiles.openfreemap.org/styles/dark` + MapLibre | Free, no key, fair use |
| Search / places | Photon (`photon.komoot.io`) | Free public instance, be gentle |
| Road route + ETA | OSRM demo (`router.project-osrm.org`) | Demo server, ~1 req/s |
| Walking route | OSRM foot (`routing.openstreetmap.de/routed-foot`) | Free community instance |
| Map-tap name | Nominatim reverse geocode | 1 req/s, needs User-Agent/browser |

**Production caveat:** public demo servers are not for scale. Before launch, self-host OSRM/Photon or move to a paid provider (MapTiler, LocationIQ, Geoapify) or Google.

**Transit routing is not available for free** — transit mode uses a distance/speed estimate, shown with a dashed line and an "estimate" label.

### Paid option (higher quality)

| API | Used for |
|---|---|
| Maps JavaScript API | Map, markers, radius, route |
| Places API (New) | Uber-style search suggestions |
| Directions API | Traffic-aware ETA (drive/transit/walk) |
| Geocoding API | Naming a dropped pin |

Google requires a key with billing enabled. Keep it out of source control; restrict it to HTTP referrers.

## Success test

Give the prototype to 5 commuters. Success = they say *"I'd actually use this on my next metro ride"* and at least 3 complete a real trip with a correct alarm.
