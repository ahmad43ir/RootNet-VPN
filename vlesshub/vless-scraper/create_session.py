# ============================================================
# create_session.py — Telethon StringSession generator
# ============================================================
# Drives the Telegram login in steps so it can run non-interactively:
#   1. python create_session.py send-code --phone <phone>
#   2. python create_session.py sign-in --phone <phone> --code <code> [--password <2fa>]
#      (run with the previous phone_code_hash; on password prompt re-run with --password)
#
# Reads API_ID / API_HASH from .env. Prints the session string to stdout
# as `SESSION=<string>` so it can be piped/parsed.
# ============================================================

import argparse
import asyncio
import os
import sys

from telethon import TelegramClient
from telethon.errors import SessionPasswordNeededError
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


def creds() -> tuple[int, str]:
    env = load_env()
    api_id = env.get("API_ID") or os.environ.get("API_ID", "")
    api_hash = env.get("API_HASH") or os.environ.get("API_HASH", "")
    if not api_id or not api_hash:
        print("❌ API_ID / API_HASH missing. Set them in .env first.", file=sys.stderr)
        sys.exit(1)
    return int(api_id), api_hash


def proxy_kwargs() -> dict:
    """MTProto proxy from MT_PROXY_HOST / MT_PROXY_PORT / MT_PROXY_SECRET env."""
    env = load_env()
    host = env.get("MT_PROXY_HOST") or os.environ.get("MT_PROXY_HOST", "")
    port = env.get("MT_PROXY_PORT") or os.environ.get("MT_PROXY_PORT", "")
    secret = env.get("MT_PROXY_SECRET") or os.environ.get("MT_PROXY_SECRET", "")
    if not (host and port and secret):
        return {}
    return {
        "connection": ConnectionTcpMTProxyRandomizedIntermediate,
        "proxy": (host, int(port), secret),
    }


async def send_code(client: TelegramClient, phone: str) -> None:
    result = await client.send_code_request(phone, force_sms=True)
    print(f"CODE_SENT=1")
    print(f"PHONE_CODE_HASH={result.phone_code_hash}")
    print("Now ask the user for the SMS code and run sign-in.")


async def sign_in(client: TelegramClient, phone: str, code: str, password: str | None) -> None:
    env = load_env()
    phone_code_hash = env.get("PHONE_CODE_HASH") or os.environ.get("PHONE_CODE_HASH", "")
    if not phone_code_hash:
        print("❌ PHONE_CODE_HASH missing (run send-code first).", file=sys.stderr)
        sys.exit(1)
    try:
        await client.sign_in(phone=phone, code=code, phone_code_hash=phone_code_hash)
    except SessionPasswordNeededError:
        if not password:
            print("PASSWORD_NEEDED=1")
            sys.exit(2)
        await client.sign_in(password=password)
    if not await client.is_user_authorized():
        print("❌ Not authorized.", file=sys.stderr)
        sys.exit(1)
    me = await client.get_me()
    print(f"✅ Logged in as @{me.username or me.first_name} (id {me.id})", file=sys.stderr)
    print(f"SESSION={client.session.save()}")


async def interactive() -> None:
    """Full interactive login in the terminal; saves the session to .env."""
    api_id, api_hash = creds()
    client = TelegramClient(
        StringSession(),
        api_id,
        api_hash,
        device_model="vless-scraper",
        connection_retries=10,
        retry_delay=3,
        timeout=15,
        request_retries=3,
        **proxy_kwargs(),
    )
    try:
        print("Interactive Telegram login — type your phone, then the code you receive.")
        print("(Enter the code as soon as it arrives; codes are short-lived.)\n")
        await client.start()
        me = await client.get_me()
        session = client.session.save()
        print(f"\n✅ Logged in as @{me.username or me.first_name} (id {me.id})")
        print("Writing session to .env ...")
        lines = []
        with open(".env", encoding="utf-8") as f:
            for raw in f:
                if raw.strip().startswith("TELEGRAM_SESSION="):
                    lines.append(f"TELEGRAM_SESSION={session}\n")
                else:
                    lines.append(raw)
        with open(".env", "w", encoding="utf-8") as f:
            f.writelines(lines)
        print("✅ Done. TELEGRAM_SESSION saved to .env")
    finally:
        await client.disconnect()


async def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)

    sc = sub.add_parser("send-code")
    sc.add_argument("--phone", required=True)

    si = sub.add_parser("sign-in")
    si.add_argument("--phone", required=True)
    si.add_argument("--code", required=True)
    si.add_argument("--password", default=None)

    sub.add_parser("interactive")

    args = parser.parse_args()

    if args.cmd == "interactive":
        await interactive()
        return

    api_id, api_hash = creds()
    client = TelegramClient(
        StringSession(),
        api_id,
        api_hash,
        device_model="vless-scraper",
        connection_retries=10,
        retry_delay=3,
        timeout=15,
        request_retries=3,
        **proxy_kwargs(),
    )
    try:
        await client.connect()
        if args.cmd == "send-code":
            await send_code(client, args.phone)
        else:
            await sign_in(client, args.phone, args.code, args.password)
    finally:
        await client.disconnect()


if __name__ == "__main__":
    asyncio.run(main())
