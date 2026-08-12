# RootNet Publishing and Monetization Plan

## 1. Product model

### Free mode
- The admin Telegram account can publish VLESS links into the shared channel/feed.
- Free users can view those public links and use the basic app experience.
- Ads remain visible for free users.

### Premium mode
- Premium users get an ad-free experience.
- Premium helps fund the scraper-bot workflow and related infrastructure costs.
- Premium can also unlock future premium-only features such as higher availability, faster updates, and deeper automation support.

## 2. Publishing approach

1. Launch the app as a free-to-try experience with a clear premium upgrade path.
2. Keep the Telegram link publishing flow simple and admin-friendly.
3. Make the premium value obvious: ad removal and premium access to bot-backed features.
4. Use a clean landing page and app store listing to explain the product and the free/premium split.

## 3. Suggested rollout order

- Phase 1: Public release of the app with free access to shared links and ads enabled.
- Phase 2: Add premium gating for ad removal and premium bot-related benefits.
- Phase 3: Expand the admin flow for Telegram posting while keeping the free-user experience lightweight.
- Phase 4: Add analytics and subscription tracking so the premium model can be improved over time.

## 4. Notes for operators

- The Telegram admin flow should remain usable even in the free tier.
- The scraper bot should be positioned as a premium-enabling infrastructure layer rather than a free-only feature.
- Keep the messaging consistent: free users can access the shared content, premium users get a cleaner experience and better long-term support.

## 5. Release runbook (APK hosting + landing page deploy)

Status as of 2026-08-06 (Phase 11 checklist item "Landing page + download link + privacy policy page"):

- Landing page and privacy policy are **LIVE** and working:
  - `https://chobgroup.pages.dev/` — Chob Group portal
  - `https://chobgroup.pages.dev/rootnet` — RootNet VPN landing page
  - `https://chobgroup.pages.dev/privacy` — RootNet privacy policy
  - Cloudflare Pages auto-clean-URL: `/privacy.html` 308s to `/privacy` — browsers follow it fine, so the app's `.../privacy.html` link works.
  - Source lives in `pages-site/` (index.html, rootnet.html, privacy.html, geoip.html).
- **Download button is BROKEN**: `pages-site/rootnet.html` points at
  `https://github.com/ahmad43ir/rootnet/releases/latest/download/app-release.apk`, but the repo is
  **private** and **no release exists** → 404 for every visitor. Page metadata is also stale
  ("v1.1.2 · 84.8 MB" — the signed clone APK is actually **204.3 MB**).
- Signed release APK: `android-app/app/build/outputs/apk/release/app-release.apk`
  (versionName 1.1.2, versionCode 100, RSA-signed, v1+v2, SHA-256
  `90154a59c25af318bd736e92d7d45491062e760e5f3e602cfacb11c24f13c2c4`).

### Why not these hosts (verified 2026-08-06)
- **Supabase Storage** — free plan has a hard **50 MB per-file cap**. Both a 60 MB test upload and any
  bucket `file_size_limit` above 50 MB are rejected (HTTP 413 `EntityTooLarge`). The 204 MB APK cannot
  be hosted there without upgrading to Pro ($25/mo).
- **APK shrinkage** — Xray `.so` files are stored uncompressed (`useLegacyPackaging=true`); even an
  arm64-v8a-only build is ~62 MB. Cannot get under the 50 MB cap.
- **Cloudflare Pages** — 25 MiB per-file upload cap. No.
- **Making the GitHub repo public** — rejected (repo contains secrets).

### One-time setup required (user, Cloudflare dashboard)
1. `dash.cloudflare.com` → **R2** → click **"Enable R2"** (free opt-in; currently not enabled —
   `wrangler r2 bucket list` fails with error `10042`).
2. Grant R2 auth, ONE of:
   - a) `npx wrangler login` (re-run in this repo) and complete the browser OAuth — current wrangler
     token's scope list lacks `workers_r2:write`; or
   - b) Create an API token: My Profile → **API Tokens** → template **"R2 Object Read & Write"** and
     use it directly.

### Steps to finish the item (next session, after R2 is enabled)
1. Create bucket + upload:
   `npx wrangler r2 object put rootnet-apk/app-release.apk --file android-app/app/build/outputs/apk/release/app-release.apk`
   (bucket name suggestion `rootnet-apk`; if the S3 API route is used instead, endpoint is
   `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`, account id `a18dd8c6e49dd4541a88a539ad482bf6`).
2. Make the bucket public (dashboard: R2 → bucket → Settings → Public access, or `r2.dev` dev URL).
3. Update `pages-site/rootnet.html`: replace the GitHub download href with the R2 public URL, and fix
   the metadata line (`v1.1.2 · Android 6.0+ · 204 MB`), then verify the download link with a HEAD
   request.
4. Deploy the site (Pages auto-deploy watches branch `main`, but the repo only has `master`, so always
   deploy manually):
   `cd pages-site; npx wrangler pages deploy . --project-name chobgroup`
5. Verify `https://chobgroup.pages.dev/rootnet` serves the updated page.
6. Commit `pages-site/rootnet.html` (+ this runbook) to git, push to `master`.
7. Mark checklist item done in `app_clone_checklist.md`, then run the §16 acceptance pass.
