# rootnet-proxy — Landing page reverse proxy

Reverse proxy that serves the Chob Group / RootNet landing pages
(`https://chobgroup.pages.dev`) through a workers.dev domain, for regions
where `pages.dev` is blocked (e.g. Iran).

| Item | Value |
|------|-------|
| **Worker URL** | `https://rootnet-proxy.mobileahmad43-a18.workers.dev` |
| **Origin** | `https://chobgroup.pages.dev` |
| **Files** | `src/index.js` (worker), `wrangler.jsonc` (config) |

Works for every path the origin serves — including the APK download:
`https://rootnet-proxy.mobileahmad43-a18.workers.dev/downloads/app-release.apk`

## Deploy

```bash
cd rootnet-proxy
npx wrangler deploy          # needs wrangler auth with Workers Scripts:Edit scope
```

## Verify

```bash
curl -I https://rootnet-proxy.mobileahmad43-a18.workers.dev/rootnet.html
curl -I https://rootnet-proxy.mobileahmad43-a18.workers.dev/downloads/app-release.apk
```
