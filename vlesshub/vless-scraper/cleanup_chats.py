# ============================================================
# cleanup_chats.py â€” delete chats with deleted/placeholder accounts
# ============================================================
# Deletes private chats where the other user is a "Deleted Account"
# and the chat contains nothing but the "This user joined Telegram."
# system placeholder (no real messages between you).
#
# Safety: run WITHOUT --execute first to list matches (dry run),
# review, then re-run with --execute to actually delete.
# ============================================================

import argparse
import asyncio
import os
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

from telethon import TelegramClient
from telethon.network import ConnectionTcpMTProxyRandomizedIntermediate
from telethon.sessions import StringSession


def load_env(path: str = ".env") -> dict:
    values: dict[str, str] = {}
    try:
        with open(path, encoding="utf-8") as f:
            for raw in f:
                line = raw.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, _, val = line.partition("=")
                values[key.strip()] = val.strip()
    except FileNotFoundError:
        pass
    return values


def client_kwargs(env: dict) -> dict:
    kwargs = {
        "connection_retries": 10,
        "retry_delay": 3,
        "timeout": 15,
        "request_retries": 3,
    }
    host = env.get("MT_PROXY_HOST")
    port = env.get("MT_PROXY_PORT")
    secret = env.get("MT_PROXY_SECRET")
    if host and port and secret:
        kwargs["connection"] = ConnectionTcpMTProxyRandomizedIntermediate
        kwargs["proxy"] = (host, int(port), secret)
    return kwargs


def is_placeholder_only(messages: list) -> bool:
    """True when every message is a system action (e.g. the join placeholder)."""
    if not messages:
        return False
    for m in messages:
        if m.message and m.message.strip():
            return False
        if m.action is None and not m.out and m.from_id is not None:
            return False
    return True


def describe(entity) -> str:
    parts = []
    first = getattr(entity, "first_name", None) or ""
    last = getattr(entity, "last_name", None) or ""
    if first or last:
        parts.append(f"{first} {last}".strip())
    username = getattr(entity, "username", None)
    if username:
        parts.append(f"@{username}")
    return " ".join(parts) or "Unknown"


async def run(execute: bool) -> None:
    env = load_env()
    api_id = env.get("API_ID")
    api_hash = env.get("API_HASH")
    session = env.get("TELEGRAM_SESSION")
    if not api_id or not api_hash or not session:
        print("❌ API_ID / API_HASH / TELEGRAM_SESSION missing in .env", file=sys.stderr)
        sys.exit(1)

    client = TelegramClient(
        StringSession(session),
        int(api_id),
        api_hash,
        device_model="chat-cleanup",
        **client_kwargs(env),
    )

    try:
        await client.connect()
        me = await client.get_me()
        print(f"✅ Connected as @{me.username or me.id}\n", file=sys.stderr)

        candidates = []
        async for dialog in client.iter_dialogs():
            if not dialog.is_user:
                continue
            entity = dialog.entity
            if getattr(entity, "id", None) == me.id:
                continue
            messages = await client.get_messages(entity, limit=10)
            count = len(messages)
            if count == 0:
                continue
            if is_placeholder_only(messages):
                candidates.append((entity, describe(entity), count))

        if not candidates:
            print("No matching chats found.")
            return

        print(f"Found {len(candidates)} chat(s) with only a join placeholder:\n")
        for i, (entity, name, count) in enumerate(candidates, 1):
            print(f"  {i}. id={entity.id}  name={name!r}  messages={count}")

        if not execute:
            print("\nDry run â€” nothing deleted. Re-run with --execute to delete these chats.")
            return

        print("\nDeleting...")
        for entity, name, count in candidates:
            try:
                await client.delete_dialog(entity)
                print(f"  âœ… Deleted chat with {name!r} (id={entity.id})")
            except Exception as e:
                print(f"  âŒ Could not delete {name!r} (id={entity.id}): {e}")
            await asyncio.sleep(0.5)

        print("\nDone.")
    finally:
        await client.disconnect()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--execute", action="store_true", help="Actually delete the chats (default is dry run)")
    args = parser.parse_args()
    asyncio.run(run(args.execute))

