# ============================================================
# 📁 main.py — VLESS Telegram Scraper (Hybrid Pipeline)
# ============================================================
# Event-driven Telegram scraper that listens for new messages
# in specified channels, extracts VLESS links, and forwards them
# to the VLESS Ingestion Worker via webhook.
#
# Architecture:
#   Telegram Channel
#       ↓ (events.NewMessage)
#   Telethon Listener
#       ↓ (POST /webhook)
#   Cloudflare Worker
#       ↓
#   Supabase (vless_links)
#
# Deploy: GitHub Actions (free cron) / Railway / Fly.io / your own VPS
#
# Environment:
#   API_ID              — Telegram API ID (int)
#   API_HASH            — Telegram API hash (string)
#   TELEGRAM_SESSION    — Telethon StringSession (string)
#   WEBHOOK_URL         — Worker webhook URL (e.g. https://vless-ingestion-api.worker.dev/webhook)
#   WEBHOOK_API_KEY     — Shared secret for webhook auth
#   SUPABASE_URL        — (Fallback) Supabase project URL
#   SUPABASE_KEY        — (Fallback) Supabase service_role key
#   CHANNELS            — Comma-separated channel usernames
#   CLEANUP_INTERVAL    — Cleanup interval in minutes (default: 60)
#   RUN_ONCE            — "1" = fetch recent messages then exit (for cron runners)
#   RUN_ONCE_MAX_MESSAGES — Messages to scan per channel in run-once mode (default: 3)
# ============================================================

import asyncio
import base64
import json
import logging
import os
import re
import sys
from datetime import datetime, timedelta, timezone

import httpx

from telethon import TelegramClient, events
from telethon.errors import FloodWaitError
from telethon.network import ConnectionTcpMTProxyRandomizedIntermediate
from telethon.sessions import StringSession

from proxy_pool import (
    create_storage,
    env_bootstrap,
    pick_proxy,
    refresh_pool,
)

# ─── .env Loading (local dev) ─────────────────────────────────
# Populates os.environ from a local .env file when present, without
# overriding variables already set in the environment (Railway, etc.).
def _load_dotenv(path: str = ".env") -> None:
    try:
        with open(path, encoding="utf-8") as f:
            for raw in f:
                line = raw.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, _, val = line.partition("=")
                key = key.strip()
                if key and key not in os.environ:
                    os.environ[key] = val.strip()
    except FileNotFoundError:
        pass


_load_dotenv()

# ─── Logging Setup ──────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
log = logging.getLogger("vless-scraper")

# ─── Configuration ──────────────────────────────────────────────

API_ID: int = int(os.environ.get("API_ID", 0))
API_HASH: str = os.environ.get("API_HASH", "")
SESSION_STRING: str = os.environ.get("TELEGRAM_SESSION", "")

# Worker webhook (primary storage path)
WEBHOOK_URL: str = os.environ.get("WEBHOOK_URL", "")
WEBHOOK_API_KEY: str = os.environ.get("WEBHOOK_API_KEY", "")

# Direct Supabase (fallback — used when webhook is not configured)
SUPABASE_URL: str = os.environ.get("SUPABASE_URL", "")
SUPABASE_KEY: str = os.environ.get("SUPABASE_KEY", "")

# Comma-separated list of channel usernames (without @)
CHANNELS_RAW: str = os.environ.get("CHANNELS", "")
CHANNELS: list[str] = [ch.strip().lstrip("@") for ch in CHANNELS_RAW.split(",") if ch.strip()]

# Comma-separated list of channels that post free MTProto proxies.
# Their latest messages feed the scraper's own connection proxy pool.
PROXY_CHANNELS: list[str] = [
    ch.strip().lstrip("@") for ch in os.environ.get("PROXY_CHANNELS", "").split(",") if ch.strip()
]

# How often to run cleanup of old links (in minutes)
CLEANUP_INTERVAL: int = int(os.environ.get("CLEANUP_INTERVAL", "60"))

# Maximum age of links in hours before deletion
MAX_AGE_HOURS: int = 36

# Run-once mode — used by scheduled runners (e.g. GitHub Actions cron):
# connect, fetch recent messages (default: 3 per VLESS channel), extract links, forward, then exit.
RUN_ONCE: bool = os.environ.get("RUN_ONCE", "0") == "1"
RUN_ONCE_MAX_MESSAGES: int = int(os.environ.get("RUN_ONCE_MAX_MESSAGES", "3"))
# Total messages a channel may yield per run-once run (top batch + catch-up
# drain). Bounds Telegram reads per AGENTS.md (≤ 30 msgs/channel). When a
# channel has more unread messages than this, the remainder is drained on
# the next run instead of being skipped forever.
RUN_ONCE_MAX_MESSAGES_TOTAL: int = int(os.environ.get("RUN_ONCE_MAX_MESSAGES_TOTAL", "30"))

# FloodWait above this many seconds is not worth waiting out in a run-once
# job: sleeping the full wait risks the GitHub Actions 10-min timeout, and
# sleeping a truncated portion just re-triggers the wait on the next request.
# Abort the remaining channels cleanly instead (AGENTS.md: honor FloodWait,
# never catch-and-continue past it).
FLOOD_WAIT_ABORT_SECONDS: int = int(os.environ.get("FLOOD_WAIT_ABORT_SECONDS", "120"))


# ─── FloodWait policy ────────────────────────────────────────────

class FloodWaitAbort(Exception):
    """Raised when a FloodWait is longer than FLOOD_WAIT_ABORT_SECONDS and the
    run-once job must stop cleanly instead of sleeping and re-triggering it."""


async def _handle_flood_wait(e: FloodWaitError) -> None:
    """Sleep out short waits; raise FloodWaitAbort for long ones.

    Long waits (>= FLOOD_WAIT_ABORT_SECONDS) abort the run: sleeping the full
    wait would blow the job timeout, and a truncated sleep just re-triggers
    the same wait on the very next request.
    """
    if e.seconds >= FLOOD_WAIT_ABORT_SECONDS:
        raise FloodWaitAbort(f"FloodWait {e.seconds}s >= abort threshold {FLOOD_WAIT_ABORT_SECONDS}s")
    log.warning(f"🌊 FloodWait {e.seconds}s — sleeping")
    await asyncio.sleep(e.seconds)


# ─── Validation ─────────────────────────────────────────────────

def _validate_config() -> None:
    """Fail fast if any required config is missing."""
    errors: list[str] = []
    if not API_ID:
        errors.append("API_ID is required")
    if not API_HASH:
        errors.append("API_HASH is required")
    if not SESSION_STRING:
        errors.append("TELEGRAM_SESSION is required")
    # CHANNELS may be empty when the channel list lives in storage
    # (bot /addchannel → scraper_config.vless_channels). Only require it
    # when the storage backend can't supply a channel list.
    storage = create_storage()
    if not CHANNELS and not getattr(storage, "supports_config", lambda: False)():
        errors.append(
            "CHANNELS is required (comma-separated) when no storage-backed "
            "channel list (scraper_config.vless_channels) is available"
        )

    # Must have at least one storage path configured
    if not WEBHOOK_URL and not SUPABASE_URL:
        errors.append("Either WEBHOOK_URL or SUPABASE_URL is required")
    if WEBHOOK_URL and not WEBHOOK_API_KEY:
        errors.append("WEBHOOK_API_KEY is required when WEBHOOK_URL is set")
    if not WEBHOOK_URL and not SUPABASE_KEY:
        errors.append("Either WEBHOOK_URL (with WEBHOOK_API_KEY) or SUPABASE_KEY is required")

    if errors:
        for e in errors:
            log.error(f"ConfigError: {e}")
        sys.exit(1)

    # Log which storage mode is active
    if WEBHOOK_URL:
        log.info(f"Storage mode: Webhook → {WEBHOOK_URL}")
    else:
        log.info("Storage mode: Direct Supabase (fallback)")

    log.info(
        f"Configuration validated: {len(CHANNELS)} channel(s), "
        f"cleanup every {CLEANUP_INTERVAL} min, "
        f"max age {MAX_AGE_HOURS}h"
    )


# ─── Config Extraction ──────────────────────────────────────────

# Every URI scheme a VPN channel might post. `ss`/`shadowsocks` are the same
# thing; socks4/5 and hysteria(2) appear too, plus .npv/.npvt files carry
# these URIs in their JSON. The pattern is deliberately loose (scheme:// up
# to whitespace/quotes) — junk gets filtered at import time.
CONFIG_SCHEMES: str = (
    "vless|vmess|trojan|ss|ssr|shadowsocks|socks|socks5|socks5h|socks4"
    "|hysteria2|hy2|hysteria|tuic|wireguard|warp|ssh"
)
CONFIG_PATTERN: re.Pattern = re.compile(
    rf"\b(?:{CONFIG_SCHEMES}):\/\/[^\s\"'<>]+", re.IGNORECASE
)

# Schemes whose payload is already a raw URI (never base64). Used to decide
# whether a JSON string leaf might be an encoded VMess payload worth decoding.
BASE64_SAFE_PREFIXES: tuple[str, ...] = (
    "vless://", "vmess://", "trojan://", "ss://", "ssr://", "shadowsocks://",
    "socks://", "socks5://", "socks5h://", "socks4://", "hysteria2://",
    "hysteria://", "hy2://", "tuic://", "wireguard://", "warp://", "ssh://",
)

# Extensions for config EXTRACTION (parsing links from content)
CONFIG_EXTENSIONS: tuple[str, ...] = (".npv", ".npvt", ".npt", ".json", ".conf", ".config", ".ovpn", ".txt")

# Extensions for FILE UPLOAD (store raw file for user download)
FILE_UPLOAD_EXTENSIONS: tuple[str, ...] = (".npv", ".npvt", ".npt", ".json", ".sip", ".conf", ".config", ".ovpn", ".txt")


def extract_links(text: str) -> list[str]:
    """
    Extract all VPN config links (VLESS, VMess, Trojan, SS/SSR, Hysteria2,
    WireGuard, SOCKS, ...) from a text string.

    Returns a deduplicated list of normalized URIs, preserving the order
    of first occurrence.
    """
    if not text or not isinstance(text, str):
        return []

    matches: list[str] = CONFIG_PATTERN.findall(text)
    seen: set[str] = set()
    unique: list[str] = []

    for link in matches:
        normalized = link.strip()
        # Drop trailing punctuation that isn't part of the URI (e.g. a comma
        # or closing bracket right after the link in a sentence).
        normalized = re.sub(r"[.,;:!?()\[\]{}\"'<>]+$", "", normalized).strip()
        if normalized and normalized not in seen:
            seen.add(normalized)
            unique.append(normalized)

    return unique


def extract_from_json_text(text: str) -> list[str]:
    """
    Extract config URIs from an NPV-family export (`.npv` / `.npvt` / `.npt`)
    or any JSON document. Walks every string leaf: raw URIs are matched
    directly, and base64-encoded payloads (how NekoBox stores VMess configs)
    are decoded and rescanned. Falls back to plain regex over the raw text
    when the content isn't valid JSON.
    """
    try:
        root = json.loads(text)
    except (ValueError, TypeError):
        return extract_links(text)

    found: list[str] = []

    def walk(node, depth: int = 0) -> None:
        if depth > 6:
            return
        if isinstance(node, dict):
            # Check for SIP config format: {"protocol": "ssh|socks|http", "host": "...", "port": 22, ...}
            # Also handle variations: "type" instead of "protocol", "ip" instead of "host", etc.
            protocol_keys = ("protocol", "type", "proto")
            host_keys = ("host", "address", "server", "ip", "hostname")
            port_keys = ("port", "port_number")
            
            has_protocol = any(k in node for k in protocol_keys)
            has_host = any(k in node for k in host_keys)
            has_port = any(k in node for k in port_keys)
            
            if has_protocol and has_host:
                # This looks like a SIP config - serialize it as JSON
                found.append(json.dumps(node, separators=(",", ":")))
            for value in node.values():
                walk(value, depth + 1)
        elif isinstance(node, list):
            for value in node:
                walk(value, depth + 1)
        elif isinstance(node, str):
            found.extend(extract_links(node))
            value = node.strip()
            if value and not value.lower().startswith(BASE64_SAFE_PREFIXES):
                try:
                    decoded = base64.urlsafe_b64decode(
                        value + "=" * ((4 - len(value) % 4) % 4)
                    ).decode("utf-8", errors="ignore")
                except Exception:
                    decoded = None
                if decoded and ("://" in decoded or decoded.lstrip().startswith("{")):
                    walk(decoded, depth + 1)

    walk(root)
    return list(dict.fromkeys(found))


# ─── Time Helpers ────────────────────────────────────────────────

def _to_utc_iso(dt) -> str:
    """Normalize a (possibly naive, UTC) datetime to an ISO-8601 UTC string.

    Telethon message dates are UTC but may be naive. The webhook / Supabase
    columns are TIMESTAMPTZ, so always emit an explicit +00:00 offset — this
    is the "scraped at" time shown next to the link in the app.
    """
    if dt is None:
        return datetime.now(timezone.utc).isoformat()
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc).isoformat()


# ─── Webhook Client ─────────────────────────────────────────────

class WebhookClient:
    """
    Async HTTP client that forwards extracted VLESS links (or raw messages)
    to the Cloudflare Worker webhook endpoint.
    """

    def __init__(self, webhook_url: str, api_key: str, timeout: float = 15.0) -> None:
        self.base_url = webhook_url.rstrip("/")
        self.url = self.base_url + "/webhook"
        self.api_key = api_key
        self.timeout = timeout
        self._client: httpx.AsyncClient | None = None

    async def _get_client(self) -> httpx.AsyncClient:
        if self._client is None or self._client.is_closed:
            self._client = httpx.AsyncClient(timeout=self.timeout)
        return self._client

    async def send_message(
        self,
        message: str,
        source: str,
        scraped_at: str | None = None,
    ) -> dict:
        """
        Send a raw Telegram message to the worker for processing.

        [scraped_at] is the ISO-8601 UTC time the link was scraped (Telegram
        message date) — the worker stores it as `created_at` so the app can
        show it next to the link.

        Returns the worker's response JSON.
        """
        client = await self._get_client()
        payload: dict = {
            "message": message,
            "source": source,
        }
        if scraped_at:
            payload["scraped_at"] = scraped_at

        try:
            response = await client.post(
                self.url,
                json=payload,
                headers={
                    "X-Webhook-Key": self.api_key,
                    "Content-Type": "application/json",
                },
            )
            response.raise_for_status()
            return response.json()
        except httpx.TimeoutException:
            log.warning(f"  ⏱ Webhook timeout for message from @{source}")
            return {"success": False, "error": "timeout"}
        except httpx.HTTPStatusError as e:
            log.warning(f"  ⚠️ Webhook HTTP {e.response.status_code} for message from @{source}: {e.response.text[:200]}")
            return {"success": False, "error": f"HTTP {e.response.status_code}"}
        except httpx.RequestError as e:
            log.warning(f"  ⚠️ Webhook request failed for message from @{source}: {e}")
            return {"success": False, "error": str(e)}

    async def send_batch(
        self,
        links: list[str],
        source: str,
        scraped_at: str | None = None,
    ) -> dict:
        """
        Send pre-extracted VLESS links directly to the worker's batch endpoint.

        [scraped_at] is the ISO-8601 UTC time the links were scraped (Telegram
        message date) — the worker stores it as `created_at` for each link.
        """
        batch_url = self.url + "/batch"
        client = await self._get_client()
        payload: dict = {
            "links": links,
            "source": source,
        }
        if scraped_at:
            payload["scraped_at"] = scraped_at

        try:
            response = await client.post(
                batch_url,
                json=payload,
                headers={
                    "X-Webhook-Key": self.api_key,
                    "Content-Type": "application/json",
                },
            )
            response.raise_for_status()
            return response.json()
        except httpx.TimeoutException:
            log.warning(f"  ⏱ Webhook batch timeout for {len(links)} link(s) from @{source}")
            return {"success": False, "error": "timeout"}
        except httpx.HTTPStatusError as e:
            log.warning(f"  ⚠️ Webhook batch HTTP {e.response.status_code}: {e.response.text[:200]}")
            return {"success": False, "error": f"HTTP {e.response.status_code}"}
        except httpx.RequestError as e:
            log.warning(f"  ⚠️ Webhook batch request failed: {e}")
            return {"success": False, "error": str(e)}

    async def trigger_cleanup(self, max_age_hours: int = MAX_AGE_HOURS) -> dict:
        """Tell the worker to clean up old links."""
        cleanup_url = self.base_url + "/cleanup"
        client = await self._get_client()
        payload = {"max_age_hours": max_age_hours}

        try:
            response = await client.post(
                cleanup_url,
                json=payload,
                headers={
                    "X-Webhook-Key": self.api_key,
                    "Content-Type": "application/json",
                },
            )
            response.raise_for_status()
            return response.json()
        except Exception:
            return {"success": False, "error": "cleanup_failed"}

    async def close(self) -> None:
        if self._client and not self._client.is_closed:
            await self._client.aclose()


# ─── Supabase Operations (Fallback) ─────────────────────────────

class SupabaseStore:
    """Handles all Supabase interactions for VLESS link storage (fallback path)."""

    def __init__(self, url: str, key: str) -> None:
        from supabase import Client, create_client
        self.client: Client = create_client(url, key)

    def link_exists(self, link: str) -> bool:
        """Check if a VLESS link already exists in the database."""
        result = (
            self.client.table("vless_links")
            .select("id")
            .eq("link", link)
            .limit(1)
            .execute()
        )
        return len(result.data) > 0

    def insert_link(
        self,
        link: str,
        source_channel: str,
        created_at: str | None = None,
    ) -> bool:
        """
        Insert a VLESS link into Supabase.

        [created_at] is the scraped-at time (ISO-8601 UTC); falls back to now.
        Returns True if inserted, False if duplicate.
        """
        if self.link_exists(link):
            log.info(f"  → Duplicate, skipped: {link[:60]}...")
            return False

        data = {
            "link": link,
            "source_channel": source_channel,
            "created_at": created_at or datetime.now(timezone.utc).isoformat(),
        }
        self.client.table("vless_links").insert(data).execute()
        log.info(f"  ✅ Inserted: {link[:60]}... (from @{source_channel})")
        return True

    def cleanup_old_links(self, max_age_hours: int = MAX_AGE_HOURS) -> int:
        """Delete all links older than max_age_hours. Returns count of deleted rows."""
        cutoff = datetime.now(timezone.utc) - timedelta(hours=max_age_hours)
        result = (
            self.client.table("vless_links")
            .delete()
            .lt("created_at", cutoff.isoformat())
            .execute()
        )
        deleted = len(result.data)
        if deleted > 0:
            log.info(f"🧹 Cleaned up {deleted} old link(s) (> {max_age_hours}h old)")
        return deleted

    def get_link_count(self) -> int:
        """Get the total number of links currently in the database."""
        result = (
            self.client.table("vless_links")
            .select("id", count="exact")
            .limit(0)
            .execute()
        )
        return result.count if hasattr(result, "count") else 0


# ─── Immediate Import Trigger ─────────────────────────────────

async def _trigger_import(supabase_url: str, supabase_key: str) -> None:
    """
    Call the Supabase RPC to immediately import pending vless_links
    into the servers table.  Avoids waiting up to 30 min for pg_cron.
    """
    if not supabase_url or not supabase_key:
        log.info("  ℹ️ No Supabase credentials — skipping immediate import")
        return

    url = f"{supabase_url.rstrip('/')}/rest/v1/rpc/import_pending_vless_links"
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.post(
            url,
            json={"p_max_links": 500},
            headers={
                "apikey": supabase_key,
                "Authorization": f"Bearer {supabase_key}",
                "Content-Type": "application/json",
            },
        )
        if resp.status_code == 200:
            data = resp.json()
            imported = data.get("imported", 0)
            skipped = data.get("skipped", 0)
            total = data.get("total", 0)
            log.info(
                f"  ⬆️  Import complete: {imported} imported, "
                f"{skipped} skipped ({total} pending before import)"
            )
        else:
            log.warning(f"  ⚠️ Import RPC returned HTTP {resp.status_code}: {resp.text[:200]}")


# ─── Telegram Event Handler ─────────────────────────────────────

class VlessScraper:
    """
    Persistent Telegram scraper that listens for new messages
    in specified channels and forwards VLESS links via webhook.
    """

    def __init__(
        self,
        client: TelegramClient,
        webhook: WebhookClient | None,
        store: SupabaseStore | None,
        channels: list[str],
    ) -> None:
        self.client = client
        self.webhook = webhook
        self.store = store
        self.channels: set[str] = set(channels)

    async def start(self) -> None:
        """Register event handlers and start the client."""
        if not self.client.is_connected():
            await self.client.start()

        me = await self.client.get_me()
        log.info(f"✅ Connected as @{me.username or me.id}")

        # Resolve channel IDs for logging
        for ch in self.channels:
            try:
                entity = await self.client.get_entity(ch)
                log.info(f"  📡 Listening to @{ch} (ID: {entity.id})")
            except Exception as e:
                log.warning(f"  ⚠️ Could not resolve @{ch}: {e}")

        # Register the message handler
        @self.client.on(events.NewMessage)
        async def message_handler(event: events.NewMessage.Event) -> None:
            await self._handle_message(event)

        log.info(f"🚀 Listening on {len(self.channels)} channel(s). Waiting for messages...")
        if self.store:
            log.info(f"🧹 Cleanup runs every {CLEANUP_INTERVAL} minutes (direct Supabase)")

    async def _handle_message(self, event: events.NewMessage.Event) -> None:
        """Process a new Telegram message."""
        sender = await event.get_sender()
        if not sender:
            return

        channel_username = sender.username or ""
        if channel_username not in self.channels:
            return

        # Extract text + any NPV/JSON attachment
        text: str = event.text or event.message.message or ""
        file_links: list[str] = await self._extract_attachment_links(event.message)
        # When the link was scraped = the Telegram message date (shown in the app).
        scraped_at: str = _to_utc_iso(event.message.date)

        if not text and not file_links:
            return

        log.info(f"📨 New message from @{channel_username} ({len(text)} chars)")

        # Extract config links (all schemes)
        links: list[str] = extract_links(text)
        merged: list[str] = list(dict.fromkeys(links + file_links))
        if not merged:
            log.info("  → No config links found")
            return

        # Upload the attachment to vpn_files ONLY when it actually yielded
        # config links — a document with no extractable configs isn't worth
        # the GET+POST REST calls (AGENTS.md: ≤ ~20 REST calls per run).
        if event.message.document and file_links:
            await self._upload_attachment_file(event.message, channel_username, scraped_at)

        log.info(f"🔗 Found {len(merged)} config link(s) in message")

        # ── Primary: send via webhook to Worker ──
        if self.webhook:
            if file_links:
                log.info(f"  → Forwarding {len(merged)} link(s) to webhook batch ({self.webhook.url})")
                result = await self.webhook.send_batch(merged, channel_username, scraped_at=scraped_at)
            else:
                log.info(f"  → Forwarding to webhook ({self.webhook.url})")
                result = await self.webhook.send_message(text, channel_username, scraped_at=scraped_at)

            if result.get("success"):
                inserted = result.get("inserted", 0)
                total = result.get("total_links", "?")
                log.info(f"  ✅ Webhook processed: {inserted} inserted, total in DB: {total}")
            else:
                log.warning(f"  ⚠️ Webhook returned error: {result.get('error', 'unknown')}")
                # Fallback: try direct Supabase if available
                if self.store:
                    log.info("  → Falling back to direct Supabase insert")
                    await self._insert_direct(merged, channel_username, scraped_at)
            return

        # ── Fallback: direct Supabase insert ──
        if self.store:
            await self._insert_direct(merged, channel_username, scraped_at)

    async def _extract_attachment_links(self, msg) -> list[str]:
        """
        Download config files and extract every config URI they contain.
        Uses CONFIG_EXTENSIONS for parsing. Returns [] for messages
        without a matching attachment.
        """
        document = getattr(msg, "document", None)
        if not document:
            return []

        # Try to get filename from attributes (DocumentAttributeFilename)
        file_name = ""
        for attr in getattr(document, "attributes", []) or []:
            name = getattr(attr, "file_name", None)
            if name:
                file_name = str(name)
                break

        # Fallback: check mime_type for JSON-like documents
        mime_type = getattr(document, "mime_type", "") or ""
        is_json_like = mime_type in ("application/json", "text/json", "application/octet-stream")

        # If no filename or not a recognized extension, but mime_type suggests JSON, try anyway
        if file_name:
            ext_match = file_name.lower().endswith(CONFIG_EXTENSIONS)
        else:
            ext_match = False

        if not ext_match and not is_json_like:
            return []

        try:
            data = await self.client.download_media(msg, file=bytes)
        except Exception as e:
            log.warning(f"  ⚠️ Could not download attachment {file_name or 'unknown'}: {e}")
            return []

        if isinstance(data, bytes):
            raw = data.decode("utf-8", errors="ignore")
        elif isinstance(data, str):
            raw = data
        else:
            return []
        return extract_from_json_text(raw)

    async def _upload_attachment_file(self, msg, channel_username: str, scraped_at: str | None = None) -> bool:
        """
        Download and upload the raw attachment file to vpn_files table
        for user download via bot. Uses FILE_UPLOAD_EXTENSIONS.
        [scraped_at] becomes the file's uploaded_at (the Telegram message date).
        Returns True if uploaded, False otherwise.
        """
        document = getattr(msg, "document", None)
        if not document:
            return False

        # Get filename
        file_name = ""
        for attr in getattr(document, "attributes", []) or []:
            name = getattr(attr, "file_name", None)
            if name:
                file_name = str(name)
                break

        if not file_name:
            return False

        # Check if extension is in FILE_UPLOAD_EXTENSIONS
        if not file_name.lower().endswith(FILE_UPLOAD_EXTENSIONS):
            return False

        try:
            data = await self.client.download_media(msg, file=bytes)
        except Exception as e:
            log.warning(f"  ⚠️ Could not download attachment for upload {file_name}: {e}")
            return False

        if not isinstance(data, bytes):
            return False

        # Upload to Supabase vpn_files table
        if not (SUPABASE_URL and SUPABASE_KEY):
            return False

        import base64
        try:
            # Encode as base64 for transport
            content_b64 = base64.b64encode(data).decode('ascii')
            
            async with httpx.AsyncClient(timeout=30) as client:
                # First, check if file already exists (by filename + channel + size)
                check_url = f"{SUPABASE_URL.rstrip('/')}/rest/v1/vpn_files"
                params = {
                    "filename": f"eq.{file_name}",
                    "source_channel": f"eq.{channel_username}",
                    "size_bytes": f"eq.{len(data)}",
                    "select": "id"
                }
                headers = {
                    "apikey": SUPABASE_KEY,
                    "Authorization": f"Bearer {SUPABASE_KEY}",
                }
                r = await client.get(check_url, headers=headers, params=params)
                if r.status_code == 200 and r.json():
                    log.info(f"  ⏭️ File already exists in vpn_files: {file_name}")
                    return True

                # Insert new file
                insert_url = f"{SUPABASE_URL.rstrip('/')}/rest/v1/vpn_files"
                body = {
                    "filename": file_name,
                    "mime_type": getattr(document, "mime_type", None),
                    "size_bytes": len(data),
                    "content": content_b64,
                    "source_channel": channel_username,
                    "uploaded_by": None,  # scraped from channel
                }
                if scraped_at:
                    body["uploaded_at"] = scraped_at
                r = await client.post(
                    insert_url,
                    headers={**headers, "Content-Type": "application/json", "Prefer": "return=minimal"},
                    json=body,
                )
                if r.status_code in (200, 201):
                    log.info(f"  📁 Uploaded attachment to vpn_files: {file_name} ({len(data)} bytes)")
                    return True
                else:
                    log.warning(f"  ⚠️ Failed to upload attachment {file_name}: HTTP {r.status_code} {r.text[:200]}")
                    return False
        except Exception as e:
            log.warning(f"  ⚠️ Exception uploading attachment {file_name}: {e}")
            return False

    async def _insert_direct(
        self,
        links: list[str],
        channel_username: str,
        scraped_at: str | None = None,
    ) -> None:
        """Insert links directly into Supabase (fallback path)."""
        inserted = 0
        for link in links:
            try:
                if self.store and self.store.insert_link(link, channel_username, created_at=scraped_at):
                    inserted += 1
                await asyncio.sleep(0.3)
            except Exception as e:
                log.error(f"  ❌ Insert error: {e}")
                await asyncio.sleep(1.0)

        if inserted and self.store:
            total = self.store.get_link_count()
            log.info(f"  📊 Total links in DB: {total}")

    async def _get_cursor(self, channel: str, storage) -> int | None:
        """
        Read the per-channel cursor from shared config. The cursor is the
        newest message id that is FULLY processed (all messages with id ≤
        cursor have been handled). Returns None when no cursor is stored
        yet (first run → backfill).
        """
        if storage is None or not getattr(storage, "supports_config", lambda: False)():
            return None
        try:
            value = await storage.get_config(f"scrape_cursor:{channel}")
        except Exception as e:
            log.warning(f"  ⚠️ Could not read cursor for @{channel}: {e}")
            return None
        if not value:
            return None
        try:
            return int(value)
        except (TypeError, ValueError):
            return None

    async def _set_cursor(self, channel: str, msg_id: int, storage) -> None:
        """
        Persist the per-channel cursor. The next run only fetches messages
        with id > this — the cursor is only advanced once a channel is fully
        caught up (see _fetch_new_messages), so already-scanned messages are
        never re-read and busy channels never skip a gap.
        """
        if storage is None or not getattr(storage, "supports_config", lambda: False)():
            return
        try:
            await storage.set_config(f"scrape_cursor:{channel}", str(msg_id))
            log.info(f"  ✅ @{channel}: cursor advanced to message #{msg_id}")
        except Exception as e:
            log.warning(f"  ⚠️ Could not persist cursor for @{channel}: {e}")

    async def _fetch_new_messages(
        self,
        entity,
        cursor: int | None,
        limit: int,
    ) -> tuple[list, int | None]:
        """
        Fetch messages posted AFTER [cursor] for one channel without ever
        permanently skipping a busy channel's backlog.

        - First run (cursor None): backfill the newest `limit` messages
          (older history is intentionally not pulled).
        - Otherwise fetch the newest `limit` messages after the cursor. If
          that top batch is NOT full, everything newer than the cursor was
          read and the cursor advances to the newest id.
        - If the top batch IS full, older unread messages may sit between
          the cursor and the top batch. Walk BACKWARD (ascending, oldest
          first) from the cursor up to the top batch, reading at most
          RUN_ONCE_MAX_MESSAGES_TOTAL messages per channel per run. A
          short/empty batch proves everything was read → the cursor
          advances to the newest id. If the per-run budget runs out first,
          the cursor advances only to the top of the drained chunk so the
          next run resumes the drain there — no message is ever skipped,
          the backlog just takes extra runs.

        Returns (messages, new_cursor). Batches never overlap within a run;
        cross-run re-reads are harmless (worker + vless_links UNIQUE dedup).
        """
        if cursor is None:
            top = await self.client.get_messages(entity, limit=limit)
            if not top:
                return [], None
            return list(top), top[0].id

        top = await self.client.get_messages(entity, min_id=cursor, limit=limit)
        if not top:
            return [], None
        all_msgs: list = list(top)
        newest: int = top[0].id

        # Top batch filled the limit → older messages may be unread below it.
        if len(top) < limit:
            return all_msgs, newest

        budget = max(limit, RUN_ONCE_MAX_MESSAGES_TOTAL) - len(top)
        if budget <= 0:
            return all_msgs, cursor

        drained = 0
        min_bound = cursor
        # Telethon max_id is EXCLUSIVE — use the top batch's oldest id itself so
        # the drain covers everything strictly below it (including the message
        # right under the batch).
        max_bound = top[-1].id
        fully_drained = False
        while drained < budget:
            batch = await self.client.get_messages(
                entity, min_id=min_bound, max_id=max_bound, limit=limit, reverse=True
            )
            if not batch:
                fully_drained = True
                break
            all_msgs.extend(batch)
            drained += len(batch)
            # reverse=True → the batch is ascending, so batch[-1] is the newest.
            min_bound = batch[-1].id
            if len(batch) < limit:
                fully_drained = True
                break
            # Gentle pacing between walk batches (stays within the rate budget).
            await asyncio.sleep(0.3)

        if fully_drained:
            return all_msgs, newest
        # Budget exhausted with a backlog remaining — advance the cursor only
        # to the newest message read by the bottom drain, so the next run
        # resumes exactly where this run stopped (nothing re-read, nothing
        # skipped).
        if drained > 0:
            return all_msgs, all_msgs[-1].id
        return all_msgs, cursor

    async def scrape_once(self, limit: int = 30, storage=None) -> dict:
        """
        Run-once mode: fetch only messages posted AFTER the stored per-channel
        cursor (first run backfills the most recent `limit` messages), extract
        config links of every protocol, forward them, then mark the channel as
        seen only once fully caught up.

        Used by scheduled runners (e.g. GitHub Actions cron) where a persistent
        listener is not available. The worker (or the vless_links UNIQUE
        constraint) still deduplicates, so re-running is harmless.

        Returns a summary dict with per-channel stats.
        """
        if not self.client.is_connected():
            await self.client.start()

        me = await self.client.get_me()
        log.info(f"✅ Connected as @{me.username or me.id}")

        summary = {
            "channels": 0,
            "messages_seen": 0,
            "messages_with_links": 0,
            "links_found": 0,
        }

        for channel in self.channels:
            try:
                entity = await self.client.get_entity(channel)
            except Exception as e:
                log.warning(f"  ⚠️ Could not resolve @{channel}: {e}")
                continue

            summary["channels"] += 1
            cursor = await self._get_cursor(channel, storage)

            try:
                messages, new_cursor = await self._fetch_new_messages(entity, cursor, limit)
            except FloodWaitError as e:
                try:
                    await _handle_flood_wait(e)
                except FloodWaitAbort as abort:
                    log.warning(f"🚦 @{channel}: {abort} — aborting remaining channels")
                    break
                continue
            except Exception as e:
                log.warning(f"  ⚠️ Could not fetch messages from @{channel}: {e}")
                continue

            if messages:
                log.info(f"  👀 @{channel}: {len(messages)} new message(s) (cursor {cursor if cursor is not None else 'none'})")

            for msg in messages:
                if not msg:
                    continue
                text: str = msg.message or ""
                file_links: list[str] = await self._extract_attachment_links(msg)
                # When the link was scraped = the Telegram message date (shown in the app).
                scraped_at: str = _to_utc_iso(msg.date)

                if not text and not file_links:
                    continue

                summary["messages_seen"] += 1
                merged: list[str] = list(dict.fromkeys(extract_links(text) + file_links))
                if not merged:
                    continue

                # Upload the attachment to vpn_files ONLY when it yielded
                # config links (see _handle_message — REST-budget guard).
                if msg.document and file_links:
                    await self._upload_attachment_file(msg, channel, scraped_at)

                summary["messages_with_links"] += 1
                summary["links_found"] += len(merged)
                log.info(f"🔗 @{channel}: {len(merged)} config link(s) in message #{msg.id}")

                if self.webhook:
                    if file_links:
                        result = await self.webhook.send_batch(merged, channel, scraped_at=scraped_at)
                    else:
                        result = await self.webhook.send_message(text, channel, scraped_at=scraped_at)
                    if result.get("success"):
                        log.info(f"  ✅ Webhook processed from @{channel}")
                    elif self.store:
                        log.warning("  ⚠️ Webhook failed → falling back to direct Supabase")
                        await self._insert_direct(merged, channel, scraped_at)
                elif self.store:
                    await self._insert_direct(merged, channel, scraped_at)

            # Mark-as-seen: the cursor only advances once the channel is fully
            # caught up (see _fetch_new_messages). A partial drain leaves the
            # cursor unchanged so the next run resumes — configs are never
            # permanently skipped.
            if messages and new_cursor is not None:
                await self._set_cursor(channel, new_cursor, storage)
            elif cursor is None and not messages:
                log.info(f"  ℹ️ @{channel}: no messages yet — cursor stays unset")

        # Keep parity with persistent mode: trigger the 36h cleanup of old links.
        # (The pg_cron import only promotes pending links — it never deletes them.)
        try:
            if self.webhook:
                result = await self.webhook.trigger_cleanup(MAX_AGE_HOURS)
                if result.get("deleted", 0) > 0:
                    log.info(f"  🧹 Remote cleanup: {result['deleted']} deleted")
            elif self.store:
                self.store.cleanup_old_links(MAX_AGE_HOURS)
        except Exception as e:
            log.error(f"🧹 Cleanup error: {e}")

        # ── Trigger immediate import of pending links into servers ──
        # Don't wait for the 30-min pg_cron — promote links now so they
        # appear in the app right away.
        try:
            await _trigger_import(SUPABASE_URL, SUPABASE_KEY)
        except Exception as e:
            log.warning(f"  ⚠️ Immediate import trigger failed (pg_cron will catch up): {e}")

        log.info(
            f"📊 Run-once summary: {summary['channels']} channel(s), "
            f"{summary['messages_seen']} message(s) seen, "
            f"{summary['messages_with_links']} with links, "
            f"{summary['links_found']} link(s) found"
        )
        return summary

    async def run_cleanup_loop(self, storage=None) -> None:
        """Periodically refresh the proxy pool + channel list, then clean up old links."""
        while True:
            await asyncio.sleep(CLEANUP_INTERVAL * 60)
            try:
                # Refresh the proxy pool + VLESS channel list from shared config,
                # so bot changes (/addproxy, /addchannel) take effect automatically.
                if storage is not None and PROXY_CHANNELS:
                    report = await refresh_pool(self.client, storage, PROXY_CHANNELS, API_ID, API_HASH)
                    log.info(f"🔄 Proxy pool refresh: {report}")
                channels = await get_vless_channels(storage)
                if set(channels) != self.channels:
                    self.channels = set(channels)
                    log.info(f"📡 Channel list updated: {len(self.channels)} channel(s)")

                if self.webhook:
                    log.info("🧹 Triggering remote cleanup via webhook...")
                    result = await self.webhook.trigger_cleanup(MAX_AGE_HOURS)
                    if result.get("deleted", 0) > 0:
                        log.info(f"  ✅ Remote cleanup: {result['deleted']} deleted")
                elif self.store:
                    self.store.cleanup_old_links(MAX_AGE_HOURS)
                else:
                    log.warning("  → No storage backend configured for cleanup")
            except Exception as e:
                log.error(f"🧹 Cleanup error: {e}")


# ─── Main Entry Point ───────────────────────────────────────────

async def get_vless_channels(storage=None) -> list[str]:
    """
    VLESS channels to listen on: prefer the shared config (managed by the
    bot via /addchannel), fall back to the CHANNELS environment variable.
    """
    if storage is not None and getattr(storage, "supports_config", lambda: False)():
        try:
            value = await storage.get_config("vless_channels")
            chans = [c.strip().lstrip("@") for c in (value or "").split(",") if c.strip()]
            if chans:
                return chans
        except Exception as e:
            log.warning(f"  ⚠️ Could not read vless_channels config: {e}")
    return CHANNELS


def build_client(proxy: dict) -> TelegramClient:
    """Build a TelegramClient that connects through the given MTProto proxy."""
    mtproxy = {
        "connection": ConnectionTcpMTProxyRandomizedIntermediate,
        "proxy": (proxy["host"], int(proxy["port"]), proxy.get("secret") or ""),
    }
    return TelegramClient(
        StringSession(SESSION_STRING),
        API_ID,
        API_HASH,
        connection_retries=10,
        retry_delay=5,
        flood_sleep_threshold=60,
        request_retries=3,
        **mtproxy,
    )


async def _safe_disconnect(client: TelegramClient) -> None:
    try:
        await client.disconnect()
    except Exception:
        pass


async def _mark_dead(storage, proxy: dict) -> None:
    try:
        await storage.update_status(
            proxy["host"],
            int(proxy["port"]),
            last_ok=False,
            checked=datetime.now(timezone.utc).isoformat(),
        )
        await storage.deactivate(proxy["host"], int(proxy["port"]))
    except Exception as e:
        log.warning(f"  ⚠️ Could not mark proxy dead: {e}")


async def run_once_mode(webhook, store, storage, bootstrap: dict | None) -> None:
    """Run-once mode: connect, refresh the proxy pool, scrape, clean up, exit."""
    proxy = await pick_proxy(storage) or bootstrap
    if not proxy:
        log.critical("💥 No proxy available — set MT_PROXY_* in .env or seed the pool.")
        sys.exit(1)

    log.info(f"🌐 Connecting via proxy {proxy['host']}:{proxy['port']}")
    client = build_client(proxy)
    scraper = VlessScraper(client, webhook, store, await get_vless_channels(storage))

    try:
        await client.connect()
        me = await client.get_me()
        log.info(f"✅ Connected as @{me.username or me.id}")
    except Exception as e:
        log.critical(f"💥 Could not connect: {e}")
        await _safe_disconnect(client)
        sys.exit(1)

    try:
        if PROXY_CHANNELS:
            report = await refresh_pool(client, storage, PROXY_CHANNELS, API_ID, API_HASH)
            log.info(f"🔄 Proxy pool refresh: {report}")
        await scraper.scrape_once(RUN_ONCE_MAX_MESSAGES, storage=storage)
    except FloodWaitAbort as abort:
        log.warning(f"🚦 {abort} — run-once aborted (FloodWait above {FLOOD_WAIT_ABORT_SECONDS}s)")
        sys.exit(2)
    finally:
        await _safe_disconnect(client)


async def run_persistent_mode(webhook, store, storage, bootstrap: dict | None) -> None:
    """
    Persistent mode: listen for new messages forever, rotating through
    the proxy pool whenever the active proxy dies.
    """
    proxy = await pick_proxy(storage) or bootstrap
    if not proxy:
        log.critical("💥 No proxy available — set MT_PROXY_* in .env or seed the pool.")
        sys.exit(1)

    while True:
        client = build_client(proxy)
        scraper = VlessScraper(client, webhook, store, await get_vless_channels(storage))

        try:
            await client.connect()
            me = await client.get_me()
            log.info(f"✅ Connected as @{me.username or me.id} via {proxy['host']}:{proxy['port']}")
            if PROXY_CHANNELS:
                report = await refresh_pool(client, storage, PROXY_CHANNELS, API_ID, API_HASH)
                log.info(f"🔄 Proxy pool refresh: {report}")
            await scraper.start()
        except Exception as e:
            log.error(f"❌ Connect failed via {proxy['host']}:{proxy['port']}: {e}")
            await _mark_dead(storage, proxy)
            await _safe_disconnect(client)
            proxy = await pick_proxy(storage)
            if not proxy:
                log.warning("⏳ No verified proxy left — retrying seed proxy in 300s")
                await asyncio.sleep(300)
                proxy = bootstrap
            continue

        cleanup_task = asyncio.create_task(scraper.run_cleanup_loop(storage))
        try:
            await client.run_until_disconnected()
        except KeyboardInterrupt:
            raise
        except Exception as e:
            log.error(f"⚠️ Connection dropped: {e}")
        finally:
            cleanup_task.cancel()
            try:
                await cleanup_task
            except (asyncio.CancelledError, Exception):
                pass
            await _safe_disconnect(client)

        log.warning(f"🔁 Disconnected — marking {proxy['host']}:{proxy['port']} dead, rotating...")
        await _mark_dead(storage, proxy)
        proxy = await pick_proxy(storage)
        if not proxy:
            log.warning("⏳ No verified proxy left — retrying seed proxy in 300s")
            await asyncio.sleep(300)
            proxy = bootstrap


async def main() -> None:
    """Initialize everything and run forever."""
    _validate_config()

    log.info("=" * 50)
    log.info("VLESS Telegram Scraper starting...")
    log.info("=" * 50)

    # ── Storage Backend ──
    webhook: WebhookClient | None = None
    store: SupabaseStore | None = None

    if WEBHOOK_URL:
        webhook = WebhookClient(WEBHOOK_URL, WEBHOOK_API_KEY)
        log.info(f"✅ Webhook client initialized → {WEBHOOK_URL}")
    elif SUPABASE_URL:
        store = SupabaseStore(SUPABASE_URL, SUPABASE_KEY)
        log.info("✅ Supabase client initialized (fallback mode)")
    else:
        log.critical("💥 No storage backend configured!")
        sys.exit(1)

    # ── Proxy Pool ──
    storage = create_storage()
    bootstrap = env_bootstrap()
    if bootstrap:
        await storage.upsert(bootstrap)
        log.info(f"✅ Seed proxy added to pool: {bootstrap['host']}:{bootstrap['port']}")
    if PROXY_CHANNELS:
        log.info(f"📡 Proxy sources: {len(PROXY_CHANNELS)} channel(s)")

    # ── Run-once mode (scheduled runners: GitHub Actions cron, etc.) ──
    if RUN_ONCE:
        try:
            await run_once_mode(webhook, store, storage, bootstrap)
        except Exception as e:
            log.critical(f"💥 Run-once failed: {e}", exc_info=True)
            sys.exit(1)
        finally:
            if webhook:
                await webhook.close()
        log.info("👋 Run-once complete.")
        return

    try:
        await run_persistent_mode(webhook, store, storage, bootstrap)
    except KeyboardInterrupt:
        log.info("👋 Shutting down...")
    except Exception as e:
        log.critical(f"💥 Fatal error: {e}", exc_info=True)
        sys.exit(1)
    finally:
        if webhook:
            await webhook.close()
        log.info("Disconnected. Goodbye.")


if __name__ == "__main__":
    asyncio.run(main())
