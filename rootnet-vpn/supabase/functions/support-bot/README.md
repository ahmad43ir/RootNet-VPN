# 🤖 Support Bot

Customer-facing support bot for VlessHub & RootNet VPN.

## Features

- 📥 **Download links** — GitHub releases for VlessHub and RootNet VPN
- ❓ **FAQ** — Common questions and troubleshooting
- 📞 **Contact** — Email, Telegram channel, website
- 📊 **Status** — Real-time service health checks
- 🌐 **Website** — Link to chobgroup.pages.dev

## Commands

| Command | Description |
|---------|-------------|
| `/start` | Welcome menu with inline buttons |
| `/help` | How to use the bot |
| `/download` | Download links for apps |
| `/contact` | Contact support |
| `/faq` | Frequently asked questions |
| `/status` | Service status |

## Deploy

```bash
# Set secrets
supabase secrets set BOT_TOKEN=<token> ADMIN_KEY=<key> CONTACT_EMAIL=support@rootnet.app --project-ref bprkazfxqmanrybiexnh

# Deploy
supabase functions deploy support-bot --project-ref bprkazfxqmanrybiexnh --no-verify-jwt

# Set webhook
curl -X POST https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/support-bot/setwebhook \
  -H "X-Admin-Key: <key>"
```

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `BOT_TOKEN` | ✅ | Telegram bot token |
| `ADMIN_KEY` | ❌ | Secret for admin endpoints |
| `CONTACT_EMAIL` | ❌ | Support email (default: support@rootnet.app) |
| `GITHUB_REPO_VLESSHUB` | ❌ | GitHub repo (default: ahmad43ir/vlesshub) |
| `GITHUB_REPO_ROOTNET_VPN` | ❌ | GitHub repo (default: ahmad43ir/rootnet) |
