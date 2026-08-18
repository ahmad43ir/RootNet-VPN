# AGENTS.md — RootNet Agent Operating Rules

> **READ THIS FIRST.** These are hard guardrails, not suggestions. They exist to keep the
> `@rootnet_vpn_manager` Telegram account unbanned, the GitHub Actions free tier from running dry,
> and Supabase free-tier limits from being exceeded. Every agent working in this repo **must**
> follow them — including agents started fresh later.

---

## 📦 PROJECT SCOPE — ROOTNET ONLY

This repo is **RootNet only** (VPN config launcher, Supabase project `bprkazfxqmanrybiexnh`).
ProxyBox (`proxybox-app/`) stays in this repo but is its own app with its own secrets.

---

## ⚠️ THE WARN-FIRST RULE (MOST IMPORTANT)

Before you do **anything** on this list, STOP and warn the user in plain terms: what you're about
to do, why it risks a ban / quota exhaustion, and a safer alternative. Do **not** just proceed
because the user "already asked" — a risky action that only *sounds* like what they asked still
gets flagged. If the user insists, do the smallest safe version first.

Warn before:
- **Sending** any message from the `@rootnet_vpn_manager` user account (it is a *real user
  account*, not a bot — messages sent from it count against the account's flood limits).
- **Logging in / logging out / re-signing-in / re-creating the session** (`sign_in`, new
  `TELEGRAM_SESSION`, deleting the StringSession). Login is the #1 way to get a user account
  flagged. The session string in `vless-scraper/.env` is precious — never regenerate casually.
- **Bursty anonymous connections**: proxy-pool testing creates throwaway MTProto auth keys.
  Caps (below) are mandatory.
- **Heavy scans**: fetching more than ~30 messages per channel, iterating the whole dialogs list,
  resolving hundreds of entities in one run.
- **Raising cron frequency** of the scraper workflow or lowering `CLEANUP_INTERVAL` /
  `RUN_ONCE_MAX_MESSAGES` limits.
- **Adding per-message external calls** (webhook/HTTP) that scale with channel volume.
- **Deleting data** (servers, links, chats, tables) — always dry-run first, always confirm.
- Anything that runs the scraper **twice at the same time** (two cron jobs, manual + scheduled,
  or a second instance during a test) — overlapping sessions from one account trigger flags.

---

## 📡 TELEGRAM SAFETY BUDGET

The scraper uses API_ID `34358009` and the user account `@rootnet_vpn_manager` (Telethon,
`flood_sleep_threshold=60` already set — do NOT lower it or set it to 0).

| Activity | Safe cap | Why |
|---|---|---|
| Read messages (`get_messages`) | ≤ 30 msgs/channel, ≤ 5–8 channels per run | reads are cheap; burst-reads across many channels aren't |
| Sustained request rate | ≤ ~1 request/sec average | above that → `FloodWaitError` (30s–24h) |
| Proxy-pool anonymous connects | **≤ 10–15 per run**, concurrency ≤ 4 | each creates an auth key with the DC; too many = `AUTH_KEY_DUPLICATED` / IP flag |
| Re-test cadence | only untested / previously-failed / stale (>12h) proxies | never re-test the whole pool every run |
| Concurrent scraper instances | **never** | |
| Sending from the user account | **never** — use the bot (`BOT_TOKEN`) instead | user-account sends are the fastest path to a ban |
| `TELEGRAM_SESSION` | never delete / recreate | same session = same auth key; switching proxies is fine |

`FloodWaitError` must always be honored (`sleep_for(seconds)`) — never catch it and continue.

---

## ⚙️ GITHUB ACTIONS MONTHLY BUDGET (free tier)

**Private repos get 2,000 minutes/month** (Linux runners count 1×, Windows 2×, macOS 10× — use
`ubuntu-latest` only). Public repos are unlimited, but this repo is private. Verify the live
number under **Settings → Billing → Plans and usage**; GitHub also throttles/queues scheduled
workflows and **disables them after ~60 days with no repo activity** (run `workflow_dispatch`
occasionally or commit regularly).

Rough run cost for the scraper (run-once mode, `ubuntu-latest`):

| Run profile | Minutes/run |
|---|---|
| Lean (cached deps, no heavy proxy test) | ~1.5–2 |
| With proxy-pool testing burst | ~3–5 |

Monthly math → **don't exceed ~2,000 min**:

| Cron interval | Runs/month | Est. min/month @2min | Verdict |
|---|---|---|---|
| Every 30 min | 1,440 | ~2,880–7,200 | ❌ way over budget + Telegram-overkill |
| Every 45 min | 960 | ~1,920–4,800 | ⚠️ only if runs stay ≤ 2 min |
| **Every 60 min** | 720 | ~1,440–3,600 | ✅ default — comfortable at lean runtime |
| Every 90 min | 480 | ~960–2,400 | ✅ safest |
| Every 2 h | 360 | ~720–1,800 | ✅ very safe |

### Recommended schedule (balanced)
- **Scheduling is currently OFF** — the scraper runs on demand from the Telegram bot via `/scrape`
  (that command dispatches the `vless-scraper` workflow on GitHub Actions; the bot must have the
  `GH_PAT` secret set, see below). Manual runs are naturally rate-safe: at most a handful per day.
- If/when a cron is re-enabled, use **every 60 minutes** (`cron: '0 * * * *'`), lean run ≤ 2 min →
  ~720 runs/mo. If the usage meter shows a run consistently ≥ 3 min, drop to **every 90 min**.
- `timeout-minutes: 10`, `concurrency` group so runs never overlap, `actions/setup-python` +
  pip cache to keep setup fast.
- Re-evaluate once a month against the usage meter.

### Secrets for /scrape (bot → GitHub)
- Function secret `GH_PAT` (Supabase): fine-grained PAT with **Actions: read & write** on the
  repo. `GH_REPO` defaults to `ahmad43ir/rootnet`, `GH_REF` to `master` — override with function
  secrets if needed.
- Repo secret `BOT_TOKEN` (GitHub Actions): only needed for the workflow's result report to the
  admin chat. Reports are sent from the **bot** account — allowed.
- The scraper's proxy pool lives in Supabase (`scraper_proxies`), shared with the bot's
  `/addproxy`/`/delproxy`. Until the `20260808000001_create_scraper_config_and_proxies.sql`
  migration is applied, those tables 404 and the loop silently degrades to the local JSON pool —
  apply the migration before relying on `/addproxy` → `/scrape`.

---

## 🗄️ SUPABASE / WEBHOOK BUDGET

Free-tier limits change — verify exact numbers in the dashboard (Settings → Project settings → Usage).
Treat these as safe ceilings regardless of plan changes:

- Keep **total REST calls per scraper run ≤ ~20** (1 cleanup + proxy-pool GET/PATCH/upserts +
  a handful of `scraper_config` reads). At 720 runs/mo ≈ 15k calls — negligible vs. any free cap.
- `vpn_files` uploads add up to 2 calls per config-attachment message (1 dedup GET + 1 insert POST),
  only for recognized config files (`.npvt`/`.npv`/`.json`/…), deduped by filename+channel+size —
  encrypted files that yield no links still get stored so users can download them (VPN Files tab).
- The webhook worker already spaces inserts 200 ms apart and dedups — don't batch-spam it, and
  don't send messages with no links.
- `import_pending_vless_links()` runs via pg_cron every 30 min (internal, low cost) — leave it.
- Don't add new per-run HTTP calls without updating this budget.

---

## ✅ CHECKLIST BEFORE RUNNING ANYTHING

1. `RUN_ONCE=1` for one-off tests; never launch persistent mode in a test without checking no
   other instance is alive.
2. Proxy pool: new candidates only; concurrency ≤ 4; count ≤ 15.
3. Confirm the workflow `concurrency` guard exists before enabling a cron.
4. No message-sending from the user account. No session regeneration.
5. Any destructive operation: dry-run → show output → explicit user confirmation.
