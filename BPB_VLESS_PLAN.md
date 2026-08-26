# 📡 BPB → RootNet VPN Engine Plan

> Status: **ENGINE + BACKEND LIVE** (2026-08-25 session complete). RootNet v3 runs the
> embedded Xray engine with an ad-funded **TIME** quota (30 min/ad, 60 min cap) synced to a
> server ledger. VlessHub (ex-ProxyBox) is the Links/MTProto/Files config hub.
>
> ▶️ **STATUS UPDATE (2026-08-25, later):** the first TWO BPB workers are deployed and
> registered in `bpb_services` (labels `BPB-1`, `BPB-2`) — `/bpb-sub` verified returning
> 4 parsed VLESS servers. RootNet's Servers tab is now **BPB-only**: scraped Supabase
> links removed end-to-end (`RemoteServerRepository`/`ServerRepository` deleted),
> Copy/Export actions removed (Connect is the only card action; those live in VlessHub).
> Both sub URLs also stored in `AppConstants.SUBSCRIPTION_URLS` as offline fallback.
>
> ▶️ **Remaining:** real-device connect test through a live BPB sub · 8 more workers
> whenever ready (just INSERT rows into `bpb_services`) · store listings.

## ✅ Done

1. [x] Plan + rename ProxyBox → **VlessHub** (`vlesshub/vlesshub-app/`, pkg `com.chobgroup.vlesshub`)
2. [x] Ported Links + Files tabs from RootNet; Settings tab (Chob Group site, privacy,
       "remove/add my Telegram channel" mailto actions)
3. [x] Channel-name template — scraper attributes every item to its Telegram channel
       (`KNOWN_PEERS` ID map, button/text-link proxy parsing, case-insensitive matching);
       servers/files/proxies display + name by channel
4. [x] VlessHub pagination: links/proxies 10 per page, files 5; ALL More buttons +
       5-click cycles = picture ads; refreshes + every 3 completed downloads = video
5. [x] File download fixes: PostgREST double-encoded bytea (hex→base64), missing
       WRITE_EXTERNAL_STORAGE manifest entry, "Open with" chooser via FileProvider
6. [x] **LibXray engine** in RootNet (`io.github.homa-games:libXray:26.8.7`) —
   - `vpn/XrayConfigBuilder.kt` (native TUN inbound, DoH, Reality/TLS/WS/gRPC,
     VLESS/VMess/Trojan/SS/SOCKS)
   - `vpn/VpnEngineService.kt` (foreground VpnService, fd → `xray.tun.fd`, socket
     protector via `LibXray.registerDialerController`, metering loop, hard disconnect)
7. [x] **Time quota** (`vpn/TimeQuotaManager.kt`): 🎬 video = +30 min; extra video only
   while connected and < 30 min left → never above 60 min; clock ticks only while
   connected; auto-disconnect at 00:00. Persisted across restarts.
8. [x] RootNet UI v3: 3 tabs (**VPN** connect ring · **Servers** · **Settings**);
   per-card Connect/Copy/Export; ⠇ menu (sort by ping / remove timed-out / restore hidden)
9. [x] Lint clean on both apps (0 errors); API 23–25 crash risks fixed
   (java.util.Base64 → android.util.Base64, longVersionCode/stopForeground/
   startForegroundService guards)
10. [x] **Backend live & verified (2026-08-25):**
   - Migration `20260825000004_bpb_services_and_device_quota.sql` applied:
     `bpb_services` (secret sub URLs, RLS deny-anon) + `device_quota` ledger
   - `rootnet-api` redeployed with two endpoints:
     - `GET /bpb-sub` — picks ONE random active sub server-side, decodes
       base64/plain body, extracts vless links → `{servers:[{name,flag,country,rawConfig}],source}`
     - `POST /quota/sync` `{deviceId, remainingSeconds?, watchAd?}` — server-authoritative
       time ledger (grant = +1800s capped 3600s; heartbeat only burns down). VERIFIED: grant returned exactly 1800s
11. [x] **App wired to backend:**
   - `BpbSubRepository.fetchRandomSub()` order: `/bpb-sub` proxy → local URL slots → Supabase fallback
   - `TimeQuotaManager.syncWithServer(context, watchAd)` — adopted on app entry,
     after every rewarded grant, heartbeat every 60 s while connected;
     deviceId = persisted random UUID
12. [x] **Release APKs built**: RootNet `app-release.apk` (~139 MB, Xray native libs)
      + VlessHub `app-release.apk` (1.6 MB), both keystore-signed

## 🔜 What's left / needed

| # | Item | Notes |
|---|------|-------|
| 1 | Real-device connect test through a live BPB sub | Engine boot + consent flow already verified on device |
| 2 | Deploy the remaining 8 BPB workers | Just `INSERT INTO public.bpb_services (label, sub_url) VALUES ('BPB-n', '<url>');` per worker — picked up instantly |
| 3 | Adivery rewarded placement verified filling inside RootNet's Connection tab | Same Adivery app as VlessHub |
| 4 | Store listings for both apps | VlessHub has a NEW applicationId → new listing required |

---

## 🔧 Key file map (for future sessions)

| What | Where |
|---|---|
| Engine config builder | `rootnet-vpn/android-app/.../vpn/XrayConfigBuilder.kt` |
| Engine service (TUN fd, protector, loop) | `rootnet-vpn/android-app/.../vpn/VpnEngineService.kt` |
| Time quota (+server sync) | `rootnet-vpn/android-app/.../vpn/TimeQuotaManager.kt` |
| Connection tab UI | `rootnet-vpn/android-app/.../ui/screens/ConnectionScreen.kt` |
| Servers tab (refresh/subs/gates) | `rootnet-vpn/android-app/.../ui/screens/ServerListScreen.kt` |
| Sub parsing + backend-first fetch | `rootnet-vpn/android-app/.../data/repository/BpbSubRepository.kt` |
| Scraper channel attribution | `vlesshub/vless-scraper/main.py` (`KNOWN_PEERS`) |
| Quota/bpb endpoints | `rootnet-vpn/supabase/functions/rootnet-api/index.ts` (`handleBpbSub`, `handleQuotaSync`) |

---

# Original panel comparison (kept for reference)

---

## 1. Panel choice — why BPB

| | **BPB-Worker-Panel** ⭐12.7k | EDtunnel | edgetunnel (cmliu) | ZEUS-PANEL |
|---|---|---|---|---|
| Maturity | v5.1.0 (Jul 2026), very active | Stale fork chain | Active, China-focused | New Jul 2026, 467 ⭐ |
| Panel GUI + subscriptions | ✅ Xray / sing-box / Clash subs | Basic sub link | ✅ | ✅ |
| Iran-focused (fragment, Warp Pro, DoH, routing) | ✅ best-in-class | ❌ | Partial | Partial |
| Per-user byte quotas | ❌ (100k req/day ≈ 2–3 users per free worker) | ❌ | ❌ | ✅ (D1-based) |

**Decision: BPB.** Most mature, actively maintained (Telegram bot, admin dashboard,
one-click Wizard deploy), and purpose-built for Iranian ISPs. Its lack of per-user
byte quotas does not matter because our quota is **enforced client-side** by the
embedded Xray engine (`queryStats` traffic counters) — that works no matter which
backend serves the configs.

## 2. The 10 BPB services

- Deploy **10 separate Cloudflare Workers**, each running its own BPB panel via the
  one-click [BPB Wizard](https://github.com/bia-pain-bache/BPB-Wizard)
  (~100k requests/day each ⇒ aggregate headroom for a real user base).
- Each service exposes a **subscription URL** (Xray-core JSON sub is what our engine
  consumes). The subscription path + UUID are secrets per worker.
- Server registry: new Supabase table `bpb_services`
  (`id, label, flag, country, sub_url, sub_path, is_active, created_at`) — configs
  NEVER hardcoded in the app (core rule). `rootnet-api` gains
  `GET /public/bpb-services` (metadata only) and `GET /bpb-sub?id=` (proxied fetch of
  the subscription body so raw sub URLs never ship inside the APK).
- App merges sources: Supabase `servers` (existing scraped VLESS) + the 10 BPB subs
  (tagged "BPB-n"), deduped, TCP-pinged as today.

## 3. VPN engine (RootNet app)

- **Core:** [LibXray](https://github.com/XTLS/libxray) AAR — prebuilt Maven artifact
  exists at `io.github.homa-games:libXray` (repo `homa-games/libXray` branch `repo`),
  pins current Xray-core releases, min API 21 ≥ our minSdk 23.
  Fallback if the maven artifact lags: build AndroidLibXrayLite-style AAR ourselves
  with gomobile (Go + NDK required).
- **TUN:** Android `VpnService` + tun2socks bridging into Xray's local SOCKS inbound
  (same architecture as v2rayNG). v1 Flutter engine learnings apply
  (`requestPermission()` before start, cancel-during-connect, etc.).
- **Traffic metering:** Xray stats API (`queryStats("user>>>traffic>>>uplink/downlink")`)
  polled every second → drives the quota countdown UI + hard disconnect.
- RootNet keeps its launcher features too (copy/export stay available), but gains
  Connect/Disconnect + quota screens.

## 4. Ad-funded quota (SUPERSEDED by time model — see ✅ Done #7)

Constants:

| Constant | Value |
|---|---|
| Grant per rewarded video | **500 MB** |
| Max total granted (per cycle) | **2 GB (= 4 videos)** |
| First connection | requires watching 1 video |

**Rule:** a video may be watched iff `grantedTotal + 500 MB ≤ 2 GB`.

- granted = 1.54 GB → 2.04 > 2 GB → **blocked** ("watching more would overcharge past the cap")
- granted = 1.40 GB → 1.90 ≤ 2 GB → **allowed** (reaches 1.9 GB)

Behaviour:
- Connection allowed while `usedThisCycle < grantedTotal`; engine **hard-disconnects**
  when usage reaches the grant (safety-net timer like v1's session enforcement).
- Watching a video mid-session raises the ceiling without dropping the tunnel.
- Ledger is server-authoritative: Supabase table `device_quota`
  (`device_id PK, granted_bytes, used_bytes, cycle_started_at, updated_at`) keyed by an
  anonymous install ID; endpoints in `rootnet-api`: `POST /quota/grant` (after verified
  ad callback), `POST /quota/heartbeat` (batched usage, ~30 s), `POST /quota/reset`
  (cycle rollover policy TBD — daily or weekly).
- Offline tolerance: local ledger cached; server reconciles deltas on next heartbeat.
  Cap is enforced locally regardless (no lockout-free bypass).

## 5. VlessHub (renamed ProxyBox) — done 2026-08-25

- `proxybox-app/` → **`vlesshub/vlesshub-app/`**, package `com.chobgroup.proxybox` →
  `com.chobgroup.vlesshub`, applicationId updated (installs as a NEW app).
- 3-tab shell: **Links** (RootNet config launcher — port pending),
  **MTProto** (original ProxyBox batches, live), **Files** (VPN files browser — port pending).
- RootNet keeps its own launcher tabs until the ports are verified, then slims down to
  VPN-only (connect/quota/servers).

## 6. Build order

1. [x] Plan + rename to VlessHub (this phase)
2. [ ] Port Links + Files tabs from `rootnet-vpn/android-app/` into `vlesshub/vlesshub-app/`
3. [ ] LibXray AAR integration + VpnService/tun2socks in `rootnet-vpn/android-app/` (RootNet)
4. [ ] Deploy 10 BPB workers (Wizard) + `bpb_services` table + `/bpb-sub` proxy endpoint
5. [ ] Quota system (table + endpoints + app gating + hard disconnect)
6. [ ] Docs/sites update (rootnet.html download split: RootNet vs VlessHub)

## 7. Risks / notes

- Free CF Worker egress ≈ 1 GB/day per account — 10 workers across **separate CF
  accounts** may be needed for real capacity; keep each worker's user count small.
- `*.workers.dev` is partially blocked in Iran → bind clean domains where possible;
  fragment/clean-IP settings live in each BPB panel.
- Changing `applicationId` means Play Store listing must be recreated for VlessHub.
