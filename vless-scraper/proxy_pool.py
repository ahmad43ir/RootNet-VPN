# ============================================================
# 📁 proxy_pool.py — MTProto PROXY POOL FOR THE VLESS SCRAPER
# ============================================================
# The scraper connects to Telegram through an MTProto proxy.
# Instead of a single hardcoded proxy, it maintains a *pool*:
#
#   1. collect  — parse MTProto proxies from the proxy channels
#                 (formats: tg://proxy?... , t.me/proxy?... ,
#                  mtproto://secret@host:port , host:port:secret)
#   2. store    — persist the pool in Supabase (scraper_proxies),
#                 so the manager bot can add/remove proxies too.
#                 Falls back to a local JSON file when Supabase
#                 credentials are missing.
#   3. test     — connect to Telegram through each candidate via
#                 Telethon; dead proxies are marked inactive and
#                 removed from rotation.
#   4. rotate   — pick the best tested proxy for the connection;
#                 on connection loss the caller re-picks.
#
# Storage schema (Supabase, RLS service_role only):
#   scraper_proxies(id, host, port, secret, source, added_at,
#                   last_checked, last_ok, is_active)
#   scraper_config(key, value)           — key `vless_channels`
# ============================================================

import asyncio
import json
import logging
import os
import re
import urllib.parse
from datetime import datetime, timedelta, timezone

import httpx

from telethon import TelegramClient
from telethon.network import ConnectionTcpMTProxyRandomizedIntermediate
from telethon.sessions import StringSession

log = logging.getLogger("vless-scraper")

# ─── Parsing ─────────────────────────────────────────────────

# tg://proxy?server=..&port=..&secret=.. or https://t.me/proxy?...
QUERY_PROXY_RE = re.compile(r"(?:tg://proxy|https?://t\.me/proxy)\?([^\s\"'<>]+)", re.I)
# mtproto://<secret>@<host>:<port>
MT_PROTO_RE = re.compile(r"mtproto://([^\s@/]+)@([^\s/:]+):(\d+)", re.I)
# bare <host>:<port>:<secret> (e.g. 1.2.3.4:8080:dd...)
HOST_PORT_SECRET_RE = re.compile(
    r"\b((?:\d{1,3}\.){3}\d{1,3}|[a-zA-Z0-9][a-zA-Z0-9._-]*):(\d{2,5}):([A-Za-z0-9+/=]+)\b"
)


def parse_proxy_candidates(text: str) -> list[dict]:
    """
    Extract candidate MTProto proxies from a message body.

    Returns deduplicated (host, port) entries preserving order of
    first occurrence. Secrets are kept verbatim — the live connection
    test decides whether a candidate actually works.
    """
    if not text:
        return []

    found: list[dict] = []

    for m in QUERY_PROXY_RE.finditer(text):
        qs = urllib.parse.parse_qs(m.group(1))
        server = (qs.get("server") or [""])[0]
        port = (qs.get("port") or [""])[0]
        if server and port.isdigit():
            found.append(
                {
                    "host": server.strip(),
                    "port": int(port),
                    "secret": (qs.get("secret") or [""])[0].strip() or None,
                }
            )

    for m in MT_PROTO_RE.finditer(text):
        found.append({"host": m.group(2), "port": int(m.group(3)), "secret": m.group(1)})

    for m in HOST_PORT_SECRET_RE.finditer(text):
        found.append({"host": m.group(1), "port": int(m.group(2)), "secret": m.group(3)})

    seen: set[tuple[str, int]] = set()
    out: list[dict] = []
    for p in found:
        key = (p["host"].lower(), p["port"])
        if key in seen:
            continue
        seen.add(key)
        out.append(p)
    return out


def env_bootstrap() -> dict | None:
    """The known-good proxy from .env, used as a fallback/seed."""
    host = os.environ.get("MT_PROXY_HOST", "")
    port = os.environ.get("MT_PROXY_PORT", "")
    if host and port.isdigit():
        return {"host": host, "port": int(port), "secret": os.environ.get("MT_PROXY_SECRET") or None}
    return None


# ─── Storage ──────────────────────────────────────────────────

class SupabaseStorage:
    """Persistent pool in Supabase (scraper_proxies) — shared with the bot."""

    TABLE = "scraper_proxies"
    CONFIG_TABLE = "scraper_config"

    def __init__(self, url: str, key: str) -> None:
        self.base = f"{url.rstrip('/')}/rest/v1"
        self.key = key

    def _headers(self, prefer: str = "return=minimal") -> dict[str, str]:
        return {
            "apikey": self.key,
            "Authorization": f"Bearer {self.key}",
            "Prefer": prefer,
        }

    async def load(self) -> list[dict]:
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                r = await client.get(
                    f"{self.base}/{self.TABLE}",
                    headers=self._headers(),
                    params={"select": "*", "order": "id.asc"},
                )
                r.raise_for_status()
                return r.json() or []
        except Exception as e:
            log.warning(f"  ⚠️ Could not load proxy pool from Supabase: {e}")
            return []

    async def upsert(self, proxy: dict, source: str = "auto") -> None:
        body = {
            "host": proxy["host"],
            "port": int(proxy["port"]),
            "secret": proxy.get("secret"),
            "source": source or "auto",
        }
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                r = await client.post(
                    f"{self.base}/{self.TABLE}",
                    headers=self._headers("resolution=merge-duplicates"),
                    json=body,
                )
                if r.status_code not in (200, 201, 409):
                    log.warning(f"  ⚠️ Proxy upsert HTTP {r.status_code}: {r.text[:160]}")
        except Exception as e:
            log.warning(f"  ⚠️ Proxy upsert failed: {e}")

    async def update_status(self, host: str, port: int, *, last_ok: bool, checked: str) -> None:
        body = {"last_ok": last_ok, "last_checked": checked}
        await self._patch(host, port, body)

    async def deactivate(self, host: str, port: int) -> None:
        await self._patch(host, port, {"is_active": False, "deactivated_at": datetime.now(timezone.utc).isoformat()})

    async def cleanup_dead_proxies(self, max_age_days: int = 3) -> int:
        """Delete dead proxies (is_active=False) older than max_age_days. Returns count deleted.

        Also covers legacy rows deactivated BEFORE the `deactivated_at` column
        existed (20260818000004) — those have deactivated_at NULL, so we fall
        back to `last_checked` as the age proxy for them.
        """
        try:
            cutoff = (datetime.now(timezone.utc) - timedelta(days=max_age_days)).isoformat()
            # Dead rows older than the cutoff, OR legacy dead rows (NULL
            # deactivated_at) whose last_checked is also older than the cutoff.
            filters = {
                "select": "id",
                "is_active": "eq.false",
                "or": (
                    f"(deactivated_at.lt.{cutoff},"
                    f"and(deactivated_at.is.null,last_checked.lt.{cutoff}))"
                ),
            }
            async with httpx.AsyncClient(timeout=15) as client:
                # Count first (before delete)
                count_r = await client.get(
                    f"{self.base}/{self.TABLE}",
                    headers=self._headers(),
                    params=filters,
                )
                count = 0
                if count_r.status_code == 200:
                    count = len(count_r.json() or [])

                # Then delete
                r = await client.delete(
                    f"{self.base}/{self.TABLE}",
                    headers=self._headers(),
                    params=filters,
                )
                if r.status_code in (200, 204):
                    return count
                return 0
        except Exception as e:
            log.warning(f"  ⚠️ Cleanup dead proxies failed: {e}")
            return 0

    async def _patch(self, host: str, port: int, body: dict) -> None:
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                r = await client.patch(
                    f"{self.base}/{self.TABLE}",
                    headers=self._headers(),
                    params={"host": f"eq.{host}", "port": f"eq.{int(port)}"},
                    json=body,
                )
                if r.status_code not in (200, 204):
                    log.warning(f"  ⚠️ Proxy patch HTTP {r.status_code}: {r.text[:160]}")
        except Exception as e:
            log.warning(f"  ⚠️ Proxy patch failed: {e}")

    async def get_config(self, key: str) -> str | None:
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                r = await client.get(
                    f"{self.base}/{self.CONFIG_TABLE}",
                    headers=self._headers(),
                    params={"key": f"eq.{key}", "select": "value"},
                )
                r.raise_for_status()
                data = r.json()
                return data[0]["value"] if data else None
        except Exception as e:
            log.warning(f"  ⚠️ Could not read scraper config '{key}': {e}")
            return None

    async def set_config(self, key: str, value: str) -> None:
        body = {
            "key": key,
            "value": value,
            "updated_at": datetime.now(timezone.utc).isoformat(),
        }
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                r = await client.post(
                    f"{self.base}/{self.CONFIG_TABLE}",
                    headers=self._headers("resolution=merge-duplicates"),
                    params={"on_conflict": "key"},
                    json=body,
                )
                if r.status_code not in (200, 201, 409):
                    log.warning(f"  ⚠️ Config upsert HTTP {r.status_code}: {r.text[:160]}")
        except Exception as e:
            log.warning(f"  ⚠️ Config upsert failed: {e}")

    def supports_config(self) -> bool:
        return True


class JsonStorage:
    """Local JSON fallback so the scraper still works without Supabase."""

    def __init__(self, path: str = "proxy_pool.json") -> None:
        self.path = path
        self._rows: list[dict] | None = None

    def _load_file(self) -> list[dict]:
        if self._rows is None:
            try:
                with open(self.path, encoding="utf-8") as f:
                    self._rows = json.load(f)
            except (FileNotFoundError, json.JSONDecodeError):
                self._rows = []
        return self._rows

    def _save(self) -> None:
        with open(self.path, "w", encoding="utf-8") as f:
            json.dump(self._rows or [], f, indent=2)

    async def load(self) -> list[dict]:
        return self._load_file()

    async def upsert(self, proxy: dict, source: str = "auto") -> None:
        rows = self._load_file()
        for r in rows:
            if r["host"].lower() == proxy["host"].lower() and int(r["port"]) == int(proxy["port"]):
                return
        rows.append(
            {
                "id": len(rows) + 1,
                "host": proxy["host"],
                "port": int(proxy["port"]),
                "secret": proxy.get("secret"),
                "source": source or "auto",
                "added_at": datetime.now(timezone.utc).isoformat(),
                "last_checked": None,
                "last_ok": None,
                "is_active": True,
            }
        )
        self._save()

    async def update_status(self, host: str, port: int, *, last_ok: bool, checked: str) -> None:
        for r in self._load_file():
            if r["host"].lower() == host.lower() and int(r["port"]) == int(port):
                r["last_ok"] = last_ok
                r["last_checked"] = checked
        self._save()

    async def deactivate(self, host: str, port: int) -> None:
        for r in self._load_file():
            if r["host"].lower() == host.lower() and int(r["port"]) == int(port):
                r["is_active"] = False
                r["deactivated_at"] = datetime.now(timezone.utc).isoformat()
        self._save()

    async def cleanup_dead_proxies(self, max_age_days: int = 3) -> int:
        """Delete dead proxies (is_active=False) older than max_age_days. Returns count deleted."""
        try:
            cutoff = datetime.now(timezone.utc) - timedelta(days=max_age_days)
            rows = self._load_file()
            original_len = len(rows)
            self._rows = [
                r for r in rows
                if not (r.get("is_active") is False and r.get("deactivated_at"))
                or datetime.fromisoformat(r["deactivated_at"].replace("Z", "+00:00")) >= cutoff
            ]
            deleted = original_len - len(self._rows)
            self._save()
            return deleted
        except Exception as e:
            log.warning(f"  ⚠️ Cleanup dead proxies failed: {e}")
            return 0

    def supports_config(self) -> bool:
        return False


def create_storage() -> SupabaseStorage | JsonStorage:
    url = os.environ.get("SUPABASE_URL", "")
    key = os.environ.get("SUPABASE_KEY", "")
    if url and key:
        return SupabaseStorage(url, key)
    return JsonStorage()


# ─── Testing ──────────────────────────────────────────────────

async def test_proxy(proxy: dict, api_id: int, api_hash: str, timeout: float = 12.0) -> bool:
    """
    Test a single MTProto proxy: connect to Telegram's MTProto API
    through it with a throwaway (anonymous) session.

    A successful connect() means the proxy reached a real Telegram DC
    and completed the auth-key handshake — a dead/broken proxy fails
    at TCP or at the handshake step.
    """
    client = TelegramClient(
        StringSession(),
        api_id,
        api_hash,
        connection=ConnectionTcpMTProxyRandomizedIntermediate,
        proxy=(proxy["host"], int(proxy["port"]), proxy.get("secret") or ""),
        connection_retries=1,
        retry_delay=1,
        request_retries=1,
        timeout=timeout,
        auto_reconnect=False,
    )
    try:
        await asyncio.wait_for(client.connect(), timeout=timeout)
        return client.is_connected()
    except Exception:
        return False
    finally:
        try:
            await client.disconnect()
        except Exception:
            pass


async def test_proxies(
    proxies: list[dict],
    api_id: int,
    api_hash: str,
    concurrency: int = 4,
    timeout: float = 12.0,
) -> dict[tuple[str, int], bool]:
    """Test many proxies concurrently. Returns {(host, port): ok}."""
    results: dict[tuple[str, int], bool] = {}
    sem = asyncio.Semaphore(concurrency)

    async def one(p: dict) -> None:
        async with sem:
            results[(p["host"].lower(), int(p["port"]))] = await test_proxy(p, api_id, api_hash, timeout)

    await asyncio.gather(*(one(p) for p in proxies))
    return results


# ─── Pool logic ───────────────────────────────────────────────

MAX_TEST_PER_RUN = 3

async def refresh_pool(
    client: TelegramClient,
    storage: SupabaseStorage | JsonStorage,
    proxy_channels: list[str],
    api_id: int,
    api_hash: str,
    messages_per_channel: int = 1,
) -> dict:
    """
    One full pool refresh: scrape the proxy channels' latest messages (default: 1 per channel),
    add any new proxies to the pool, test up to MAX_TEST_PER_RUN proxies (newly scraped first,
    then oldest-untested/failed/stale from pool), and deactivate the dead.

    Returns a summary dict for logging.
    """
    summary = {
        "scanned_channels": len(proxy_channels),
        "candidates": 0,
        "added": 0,
        "tested": 0,
        "working": 0,
    }

    # 1. Collect candidates from the latest messages of each channel.
    candidates: list[dict] = []
    for ch in proxy_channels:
        try:
            entity = await client.get_entity(ch)
            messages = await client.get_messages(entity, limit=messages_per_channel)
            for msg in messages:
                if not msg:
                    continue
                # Plain text + URLs hidden in link entities (text_link/url),
                # so proxies behind custom display text are still found.
                text = msg.message or ""
                entities = getattr(msg, "entities", None) or []
                urls = [e.url for e in entities if getattr(e, "url", None)]
                if urls:
                    text = f"{text}\n" + "\n".join(urls)
                if text:
                    candidates += parse_proxy_candidates(text)
        except Exception as e:
            log.warning(f"  ⚠️ Could not scrape proxy channel @{ch}: {e}")
    summary["candidates"] = len(candidates)

    # 2. Add unknown candidates to the pool, track which are newly added.
    known = {(r.get("host", "").lower(), int(r.get("port") or 0)) for r in await storage.load()}
    newly_added: list[dict] = []
    for p in candidates:
        key = (p["host"].lower(), int(p["port"]))
        if key in known:
            continue
        await storage.upsert(p)
        known.add(key)
        newly_added.append(p)
        summary["added"] += 1
    if summary["added"]:
        log.info(f"  ➕ {summary['added']} new proxy(ies) found in proxy channels")

    # 3. Build test list: newly scraped proxies FIRST, then pool proxies that need testing.
    #    Cap total tests at MAX_TEST_PER_RUN (3).
    rows = await storage.load()
    now = datetime.now(timezone.utc)

    # First: newly added proxies (never tested)
    to_test: list[dict] = []
    for p in newly_added:
        if len(to_test) >= MAX_TEST_PER_RUN:
            break
        to_test.append(p)

    # Second: existing pool proxies that are untested/failed/stale
    if len(to_test) < MAX_TEST_PER_RUN:
        # Sort by: never tested first, then failed, then stale (oldest checked first)
        pool_to_test = []
        for r in rows:
            if not r.get("is_active", True):
                continue
            # Skip if already in to_test (newly added)
            if any(r["host"].lower() == t["host"].lower() and int(r["port"]) == int(t["port"]) for t in to_test):
                continue
            checked = r.get("last_checked")
            checked_dt = None
            if checked:
                try:
                    checked_dt = datetime.fromisoformat(str(checked).replace("Z", "+00:00"))
                except (ValueError, TypeError):
                    checked_dt = None
            if r.get("last_ok") is None or r.get("last_ok") is False:
                pool_to_test.append((0, r))  # priority 0: never tested or failed
            elif checked_dt is None or (now - checked_dt) > timedelta(hours=12):
                pool_to_test.append((1, checked_dt or now, r))  # priority 1: stale

        # Sort: priority 0 first, then priority 1 by oldest checked_dt
        pool_to_test.sort(key=lambda x: (x[0], x[1] if len(x) > 1 else now))

        for item in pool_to_test:
            if len(to_test) >= MAX_TEST_PER_RUN:
                break
            to_test.append(item[-1])  # last element is the proxy dict

    summary["tested"] = len(to_test)
    if to_test:
        log.info(f"  🔎 Testing {len(to_test)} proxy(ies) (cap: {MAX_TEST_PER_RUN})...")
        results = await test_proxies(to_test, api_id, api_hash)
        for r in to_test:
            ok = results.get((r["host"].lower(), int(r["port"])), False)
            await storage.update_status(r["host"], int(r["port"]), last_ok=ok, checked=now.isoformat())
            if ok:
                summary["working"] += 1
            else:
                await storage.deactivate(r["host"], int(r["port"]))
        log.info(
            f"  🧹 Proxy test done: {summary['working']} working, "
            f"{len(to_test) - summary['working']} dead (deactivated)"
        )
    else:
        summary["working"] = sum(1 for r in rows if r.get("last_ok") is True and r.get("is_active", True))

    # Cleanup dead proxies older than 3 days
    deleted = await storage.cleanup_dead_proxies(3)
    if deleted:
        log.info(f"  🗑️ Cleaned up {deleted} dead proxy(ies) older than 3 days")
    summary["cleaned_up"] = deleted

    return summary


async def pick_proxy(storage: SupabaseStorage | JsonStorage, exclude: tuple[str, int] | None = None) -> dict | None:
    """
    Pick the best proxy for connecting: an active, last-tested-OK one
    (newest first). Returns None when the pool has no verified proxy.
    """
    rows = await storage.load()
    ranked = sorted(
        rows,
        key=lambda r: (r.get("last_ok") is True, r.get("id") or 0),
        reverse=True,
    )
    for r in ranked:
        if not r.get("is_active", True):
            continue
        if not r.get("last_ok"):
            continue
        if exclude and (r["host"].lower(), int(r["port"])) == exclude:
            continue
        return r
    return None
