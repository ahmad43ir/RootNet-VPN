// ============================================================
// 📁 _handlers.ts — MESSAGE / CALLBACK / COMMAND HANDLERS
// ============================================================
// Port of telegram-bot/bot.py's handlers. The Python bot was a
// long-poll process; this runs inside the edge function in webhook
// mode. Per-chat UI state is persisted (bot_chat_state) instead of
// living in module memory.
// ============================================================

import * as tg from './_telegram.ts';
import { dispatchWorkflow } from './_github.ts';
import { getChatState, saveChatState } from './_state.ts';
import {
  addScraperProxy,
  backfillFlags,
  checkDuplicate,
  countActiveServers,
  countVpnFiles,
  deleteAllScraperProxies,
  deleteAllServers,
  deleteScraperProxy,
  deleteServer,
  deleteVpnFile,
  fetchServers,
  getAppConfig,
  getLastScrapeTime,
  getScraperChannels,
  getVpnFile,
  insertServer,
  listScraperProxies,
  listVpnFiles,
  saveVpnFile,
  setLastScrapeTime,
  setScraperChannels,
  updateAppConfig,
  type InsertContext,
  type ServerRow,
  type VpnFileRow,
} from './_db.ts';
import { extractChannel, parseFile } from './_parser.ts';

export const MENU_BTN_UPLOAD = '📤 Upload';

// File extensions whose raw content is also stored in vpn_files (File tab),
// mirroring the scraper's FILE_UPLOAD_EXTENSIONS. Keep in sync with the
// vpn_files_filename_check CHECK constraint.
const FILE_UPLOAD_EXTENSIONS = [
  '.npv', '.npvt', '.npt', '.json', '.sip', '.conf', '.config', '.ovpn', '.txt',
];

/** Uint8Array → base64 (chunked, so big files don't blow the call stack). */
function bytesToBase64(bytes: Uint8Array): string {
  let binary = '';
  const chunk = 0x8000; // 32 KB per spread
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}
export const MENU_BTN_SERVERS = '🟢 Servers';
export const MENU_BTN_FILES = '📁 VPN Files';
export const MENU_BTN_WEB = '🔗 Web pages';
export const MENU_BTN_SCRAPER = '🤖 Scraper';
export const MENU_BTN_VERSION = '📱 Version';
export const MENU_BTN_HELP = '❓ Help';

const VERSION_TEXT =
  '*📱 Version management*\n\n' +
  'Set the minimum required app version. Apps below this version will be blocked.\n\n' +
  'Use the buttons below, or slash commands:\n' +
  '`/version` show config\n' +
  '`/setmin X.Y.Z` set minimum\n' +
  '`/setlatest X.Y.Z` set latest\n' +
  '`/setbuild N` set build number\n' +
  '`/forceupdate on|off` toggle force';

const MENU_TEXT =
  'VlessHub � official config publishing channel: @Vless_hub_bot.\n\n' +
  'New VPN configs (VLESS · VMess · Trojan · SS · Hysteria2 · WireGuard · SOCKS) are published here and flow straight into the app.\n\n' +
  '📤 Upload — import configs as servers\n' +
  '🟢 Servers — list & delete servers\n' +
  '📁 VPN Files — browse & download raw config files (.npvt, .sip, .npv, .json, etc.)\n' +
  '🤖 Scraper — run the config scraper / manage proxies & channels\n' +
  '🔗 Web pages — quick links\n' +
  '❓ Help — how it works & what each option does';

// Telegram /command menu (registered via setMyCommands so they show up
// in the client's command list when you type "/").
const BOT_COMMANDS: tg.TgBotCommand[] = [
  { command: 'start', description: 'Main menu' },
  { command: 'scrape', description: 'Run the config scraper right now' },
  { command: 'addproxy', description: 'Add an MTProto proxy (tg://proxy?... or host:port:secret)' },
  { command: 'delproxy', description: 'Remove a proxy (host, id, or "all")' },
  { command: 'listproxy', description: 'Show the proxy pool' },
  { command: 'addchannel', description: 'Add a config channel to the scraper' },
  { command: 'delchannel', description: 'Remove a config channel' },
  { command: 'listchannel', description: 'Show the config channel list' },
  { command: 'stats', description: 'Active server count' },
  { command: 'backfillflags', description: 'Geolocate and fix server flags' },
  { command: 'myid', description: 'Show your Telegram user ID' },
  { command: 'version', description: 'Show current version config' },
  { command: 'setmin', description: 'Set minimum required version (e.g. /setmin 2.0.1)' },
  { command: 'setlatest', description: 'Set latest version (e.g. /setlatest 2.1.0)' },
  { command: 'setbuild', description: 'Set latest build number (e.g. /setbuild 300)' },
  { command: 'forceupdate', description: 'Toggle force update on/off' },
  { command: 'help', description: 'How everything works' },
];

const SCRAPER_TEXT =
  '*🤖 Scraper control*\n\n' +
  'The scraper pulls new VPN configs (VLESS / VMess / Trojan / SS / Hysteria2 / WireGuard / SOCKS — links or `.npv` / `.npvt` files) ' +
  'from the channels below and posts them to the app.\n\n' +
  'All config files (including encrypted `.npvt` / `.npv` that can\'t be auto-parsed) are also stored in the **VPN Files** section for manual download.\n\n' +
  'Use the buttons below, or the slash commands:\n' +
  '`/scrape` run now · `/addproxy` · `/delproxy` · `/listproxy` · `/addchannel` · `/delchannel` · `/listchannel`\n\n' +
  'If the run says it can\'t connect, tap "➕ Add proxy" and send a working proxy, then run it again.';

const VPN_FILES_TEXT =
  '*📁 VPN Files*\n\n' +
  'Browse raw config files stored by the scraper and manual uploads. ' +
  'Encrypted files (.npvt, .npv) that couldn\'t be auto-parsed are available here for manual download.\n\n' +
  'Tap a file to download it. Encrypted files show a 🔒 badge.';

const HELP_TEXT =
  '*RootNet bot — menu guide*\n\n' +
  '📤 *Upload*\n' +
  'Import configs as servers. Send a `.txt` / `.npv` / `.npvt` / `.json` file, or paste the links directly.\n\n' +
  '🟢 *Servers*\n' +
  'Open the list, tap servers to select them, then hit *Delete selected* — or *Delete all*. Deletion always asks for confirmation.\n\n' +
  '📁 *VPN Files*\n' +
  'Browse and download raw config files uploaded by the scraper or manually. Useful for encrypted files (.npvt, .npv) that the bot can\'t parse automatically. Tap a file to download it.\n\n' +
  '🔗 *Web pages*\n' +
  'Shortcut buttons to the landing page, APIs, and the Supabase dashboard.\n\n' +
  '🏷 *Server names*\n' +
  'Links taken from a channel keep that channel\'s name and are numbered in upload order — e.g. `@mychannel 1`, `@mychannel 2`. ' +
  'The channel is read from the forward origin when you forward the messages to the bot, or from `@name` / `t.me/name` / `tel:@name` in the text. ' +
  'Links without any channel are named `Untitled 1`, `2`, ...\n\n' +
  '📱 *Version management*\n' +
  '`/version` show current version config\n' +
  '`/setmin X.Y.Z` set minimum required version (older apps blocked)\n' +
  '`/setlatest X.Y.Z` set latest version\n' +
  '`/setbuild N` set latest build number\n' +
  '`/forceupdate on|off` toggle force update\n\n' +
  '🌐 *Scraper settings*\n' +
  'The scraper auto-collects new VPN configs from the configured channels: `/scrape` starts a run right now (it reports back here when done); ' +
  '`/addproxy`, `/delproxy`, `/listproxy` manage the MTProto proxy pool it connects through; ' +
  '`/addchannel`, `/delchannel`, `/listchannel` manage which channels it listens to.\n\n' +
  '*Commands*: /start menu · /stats server count · /backfillflags fix flags · /scrape run scraper · /myid your Telegram ID · /version version config';

const WEB_LINKS: [string, string][] = [
  ['Landing page (proxy)', 'https://rootnet-proxy.mobileahmad43-a18.workers.dev'],
  ['Worker API', 'https://rootnet-api.mobileahmad43-a18.workers.dev'],
  ['Ingestion API (health)', 'https://vless-ingestion-api.mobileahmad43-a18.workers.dev/health'],
  ['Supabase dashboard', 'https://app.supabase.com/project/bprkazfxqmanrybiexnh'],
];

export interface BotContext {
  token: string;
  supabase: any;
  adminIds: Set<number>;
  insertCtx: InsertContext;
}

// ─── Markup builders ─────────────────────────────────────────

function mainKeyboard(): unknown {
  return {
    keyboard: [
      [MENU_BTN_UPLOAD],
      [MENU_BTN_SERVERS],
      [MENU_BTN_FILES, MENU_BTN_WEB, MENU_BTN_SCRAPER, MENU_BTN_VERSION],
      [MENU_BTN_HELP],
    ],
    resize_keyboard: true,
    input_field_placeholder: 'Choose an option',
  };
}

function inlineMenuButton(): unknown {
  return { inline_keyboard: [[{ text: '◀ Menu', callback_data: 'menu' }]] };
}

function versionKeyboard(): unknown {
  return {
    inline_keyboard: [
      [
        { text: '📋 Show config', callback_data: 'version:show' },
      ],
      [
        { text: '⬇️ Set min version', callback_data: 'version:setmin' },
        { text: '⬆️ Set latest', callback_data: 'version:setlatest' },
      ],
      [
        { text: '🔢 Set build', callback_data: 'version:setbuild' },
        { text: '🔄 Force update', callback_data: 'version:forceupdate' },
      ],
      [{ text: '◀ Menu', callback_data: 'menu' }],
    ],
  };
}

function versionBackKeyboard(): unknown {
  return {
    inline_keyboard: [
      [
        { text: '◀ Version', callback_data: 'version' },
        { text: '◀ Menu', callback_data: 'menu' },
      ],
    ],
  };
}

function forceUpdateToggleKeyboard(current: boolean): unknown {
  return {
    inline_keyboard: [
      [
        { text: current ? '✅ Force: ON' : '⬜ Force: OFF', callback_data: 'version:toggleforce' },
      ],
      [{ text: '◀ Version', callback_data: 'version' }],
    ],
  };
}

function scraperKeyboard(): unknown {
  return {
    inline_keyboard: [
      [
        { text: '➕ Add proxy', callback_data: 'scraper:addproxy' },
        { text: '🗑 Remove proxy', callback_data: 'scraper:delproxy' },
      ],
      [{ text: '📋 Proxy pool', callback_data: 'scraper:listproxy' }],
      [
        { text: '➕ Add channel', callback_data: 'scraper:addchannel' },
        { text: '🗑 Remove channel', callback_data: 'scraper:delchannel' },
      ],
      [{ text: '📋 Channels', callback_data: 'scraper:listchannel' }],
      [{ text: '▶️ Run scrape', callback_data: 'scraper:scrape' }],
      [{ text: '◀ Menu', callback_data: 'menu' }],
    ],
  };
}

function scraperBackKeyboard(): unknown {
  return {
    inline_keyboard: [
      [
        { text: '◀ Scraper', callback_data: 'scraper' },
        { text: '◀ Menu', callback_data: 'menu' },
      ],
    ],
  };
}

function vpnFilesKeyboard(page: number = 0, hasMore: boolean = false): unknown {
  // This will be dynamically built in sendVpnFilesList
  return { inline_keyboard: [] };
}

function vpnFilesListMarkup(files: VpnFileRow[], page: number, hasMore: boolean): unknown {
  const rows: Record<string, unknown>[][] = [];
  for (const f of files) {
    const encrypted = f.is_encrypted ? ' 🔒' : '';
    const sizeKB = Math.round(f.size_bytes / 1024);
    const channel = f.source_channel ? ` @${f.source_channel}` : '';
    rows.push([{ text: `${encrypted} ${f.filename} (${sizeKB} KB)${channel}`, callback_data: `vpnfile:download:${f.id}` }]);
  }
  const nav: Record<string, unknown>[] = [];
  if (page > 0) nav.push({ text: '⬅️ Prev', callback_data: `vpnfile:page:${page - 1}` });
  if (hasMore) nav.push({ text: 'Next ➡️', callback_data: `vpnfile:page:${page + 1}` });
  if (nav.length > 0) rows.push(nav);
  rows.push([{ text: '◀ Menu', callback_data: 'menu' }]);
  return { inline_keyboard: rows };
}

function serversSelectMarkup(servers: ServerRow[], selected: Set<number>): unknown {
  const rows: Record<string, unknown>[][] = [];
  for (const s of servers) {
    const mark = selected.has(s.id) ? '☑️' : '⬜';
    const label = `${mark} ${s.flag} ${s.name}`;
    rows.push([{ text: label, callback_data: `toggle:${s.id}` }]);
  }
  rows.push([
    { text: `🗑 Delete selected (${selected.size})`, callback_data: 'delbulk' },
    { text: '⛔ Delete all', callback_data: 'delall' },
  ]);
  rows.push([{ text: '◀ Menu', callback_data: 'menu' }]);
  return { inline_keyboard: rows };
}

function serversSelectText(servers: ServerRow[], selected: Set<number>): string {
  if (servers.length === 0) return 'No active servers in the database.';
  const title = `🟢 Servers (${servers.length})`;
  const hint =
    selected.size > 0
      ? `${selected.size} selected — tap "Delete selected" when done.`
      : 'Tap servers to select them, then delete them together.';
  return `${title}\n${hint}`;
}

// ─── Dispatcher ──────────────────────────────────────────────

function isAdmin(ctx: BotContext, userId?: number): boolean {
  return userId !== undefined && ctx.adminIds.has(userId);
}

// ─── Menu-message lifecycle ───────────────────────────────────
// The bot used to send a NEW menu/option message on every navigation
// (each main-keyboard tap, /start, page flips), so the chat filled up
// with stacked keyboards. Instead, one "live" menu message is tracked
// per chat (bot_chat_state.menu_message_id): sending a new page first
// deletes the previous one. Content replies (results, errors, files)
// are not tracked — only navigation pages are.

/** Send a menu page, replacing (deleting) the previously tracked one. */
async function showMenu(
  ctx: BotContext,
  chatId: number,
  text: string,
  opts: tg.TgSendOptions = {},
): Promise<void> {
  const state = await getChatState(ctx.supabase, chatId);
  if (state.menuMessageId !== null) {
    await tg.deleteMessage(ctx.token, chatId, state.menuMessageId);
  }
  const sent = await tg.sendMessage(ctx.token, chatId, text, opts);
  await saveChatState(ctx.supabase, { ...state, menuMessageId: sent?.message_id ?? null });
}

/** Mark the tapped message as the live menu page, deleting the previously
 *  tracked one when it's a different message (e.g. user tapped an old menu). */
async function adoptMenuMessage(ctx: BotContext, chatId: number, messageId: number): Promise<void> {
  const state = await getChatState(ctx.supabase, chatId);
  if (state.menuMessageId !== null && state.menuMessageId !== messageId) {
    await tg.deleteMessage(ctx.token, chatId, state.menuMessageId);
  }
  await saveChatState(ctx.supabase, { ...state, menuMessageId: messageId });
}

/** Send a transient result/status message (scrape report, upload summary,
 *  command answer), replacing the previously tracked result so status
 *  messages don't pile up. Returns the new message id (for in-place edits). */
async function showResult(
  ctx: BotContext,
  chatId: number,
  text: string,
  opts: tg.TgSendOptions = {},
): Promise<number | null> {
  const state = await getChatState(ctx.supabase, chatId);
  if (state.resultMessageId !== null) {
    await tg.deleteMessage(ctx.token, chatId, state.resultMessageId);
  }
  const sent = await tg.sendMessage(ctx.token, chatId, text, opts);
  await saveChatState(ctx.supabase, { ...state, resultMessageId: sent?.message_id ?? null });
  return sent?.message_id ?? null;
}

export async function routeUpdate(ctx: BotContext, update: any): Promise<void> {
  if (update.message) {
    await handleMessage(ctx, update);
  } else if (update.callback_query) {
    await handleCallback(ctx, update);
  }
  // Other update types (channel_post, poll_answer, ...) are ignored.
}

// ─── Commands ────────────────────────────────────────────────

/** Parse an MTProto proxy from user input (single proxy expected). */
function parseProxyInput(input: string): { host: string; port: number; secret?: string } | null {
  const t = input.trim();
  if (!t) return null;

  const q =
    t.match(/tg:\/\/proxy\?([^\s]+)/i) ?? t.match(/https?:\/\/t\.me\/proxy\?([^\s]+)/i);
  if (q) {
    const qs = new URLSearchParams(q[1]);
    const host = qs.get('server');
    const port = qs.get('port');
    if (host && port && /^\d{2,5}$/.test(port)) {
      return { host, port: parseInt(port, 10), secret: qs.get('secret') ?? undefined };
    }
    return null;
  }

  const mm = t.match(/mtproto:\/\/([^\s@/]+)@([^\s/:]+):(\d+)/i);
  if (mm) return { host: mm[2], port: parseInt(mm[3], 10), secret: mm[1] };

  const mp = t.match(/^([^\s:]+):(\d{2,5}):([^\s]+)$/);
  if (mp && !/^(https?:|socks\d?:)/i.test(t)) {
    return { host: mp[1], port: parseInt(mp[2], 10), secret: mp[3] };
  }

  return null;
}

async function handleCommand(
  ctx: BotContext,
  chatId: number,
  userId: number | undefined,
  fullText: string,
): Promise<void> {
  const token = ctx.token;
  const command = fullText.split(' ')[0];

  // /myid works for anyone (used to discover your admin id).
  if (command === '/myid' || command.startsWith('/myid ')) {
    await showResult(ctx, chatId, `Your Telegram user ID: \`${userId}\``, {
      parse_mode: 'Markdown',
    });
    return;
  }

  if (!isAdmin(ctx, userId)) {
    await showResult(ctx, chatId, 'Access denied.');
    return;
  }

  if (command === '/start' || command.startsWith('/start ')) {
    await tg.setMyCommands(token, BOT_COMMANDS);
    await showMenu(ctx, chatId, MENU_TEXT, { reply_markup: mainKeyboard() });
    return;
  }

  if (command === '/stats') {
    const total = await countActiveServers(ctx.supabase);
    const text = total !== null ? `Active servers in DB: ${total}` : 'Could not reach Supabase.';
    await showResult(ctx, chatId, text);
    return;
  }

  if (command === '/help') {
    await showMenu(ctx, chatId, HELP_TEXT, {
      parse_mode: 'Markdown',
      reply_markup: mainKeyboard(),
    });
    return;
  }

  if (command === '/backfillflags') {
    const statusId = await showResult(ctx, chatId, '⏳ Geolocating all servers...');
    const result = await backfillFlags(ctx.supabase, ctx.insertCtx);
    const text =
      '🌍 Flag backfill complete\n' +
      `Scanned: ${result.scanned}\n` +
      `Updated: ${result.updated}\n` +
      `Failed: ${result.failed}`;
    if (statusId !== null) {
      await tg.editMessageText(token, chatId, statusId, text);
    } else {
      await showResult(ctx, chatId, text);
    }
    return;
  }

  // ── Version management ──────────────────────────────────────

  if (command === '/version') {
    const config = await getAppConfig(ctx.supabase);
    if (!config) {
      await showResult(ctx, chatId, '⚠️ Could not read app_config from Supabase.');
      return;
    }
    const text =
      '*📱 Current version config*\n\n' +
      `Latest version: \`${config.latest_version}\`\n` +
      `Latest build: \`${config.latest_build}\`\n` +
      `Minimum version: \`${config.minimum_version}\`\n` +
      `Force update: ${config.force_update ? '✅ ON' : '❌ OFF'}\n` +
      `Update URL: ${config.update_url}\n` +
      `Release notes: ${config.release_notes || '(none)'}`;
    await showResult(ctx, chatId, text, { parse_mode: 'Markdown' });
    return;
  }

  if (command === '/setmin' || command.startsWith('/setmin ')) {
    const arg = fullText.slice('/setmin'.length).trim();
    if (!arg || !/^\d+\.\d+\.\d+$/.test(arg)) {
      await showResult(ctx, chatId, 'Usage: `/setmin 2.0.1` (format: X.Y.Z)', {
        parse_mode: 'Markdown',
      });
      return;
    }
    const config = await getAppConfig(ctx.supabase);
    if (config?.latest_version && compareVersion(arg, config.latest_version) > 0) {
      await showResult(
        ctx,
        chatId,
        `⚠️ Cannot set minimum \`${arg}\` — it is NEWER than latest (\`${config.latest_version}\`). Set /setlatest first.`,
        { parse_mode: 'Markdown' },
      );
      return;
    }
    const ok = await updateAppConfig(ctx.supabase, { minimum_version: arg });
    await showResult(
      ctx,
      chatId,
      ok
        ? `✅ Minimum version set to \`${arg}\`\n\nApps below this version will be blocked.`
        : '⚠️ Failed to update (Supabase error).',
      { parse_mode: 'Markdown' },
    );
    return;
  }

  if (command === '/setlatest' || command.startsWith('/setlatest ')) {
    const arg = fullText.slice('/setlatest'.length).trim();
    if (!arg || !/^\d+\.\d+\.\d+$/.test(arg)) {
      await showResult(ctx, chatId, 'Usage: `/setlatest 2.1.0` (format: X.Y.Z)', {
        parse_mode: 'Markdown',
      });
      return;
    }
    const config = await getAppConfig(ctx.supabase);
    if (config?.minimum_version && compareVersion(arg, config.minimum_version) < 0) {
      await showResult(
        ctx,
        chatId,
        `⚠️ Cannot set latest \`${arg}\` — it is OLDER than minimum (\`${config.minimum_version}\`). Fix /setmin first.`,
        { parse_mode: 'Markdown' },
      );
      return;
    }
    const ok = await updateAppConfig(ctx.supabase, { latest_version: arg });
    await showResult(
      ctx,
      chatId,
      ok
        ? `✅ Latest version set to \`${arg}\``
        : '⚠️ Failed to update (Supabase error).',
      { parse_mode: 'Markdown' },
    );
    return;
  }

  if (command === '/setbuild' || command.startsWith('/setbuild ')) {
    const arg = fullText.slice('/setbuild'.length).trim();
    const num = parseInt(arg, 10);
    if (!arg || isNaN(num) || num < 0) {
      await showResult(ctx, chatId, 'Usage: `/setbuild 300` (positive integer)', {
        parse_mode: 'Markdown',
      });
      return;
    }
    const ok = await updateAppConfig(ctx.supabase, { latest_build: num });
    await showResult(
      ctx,
      chatId,
      ok
        ? `✅ Latest build set to \`${num}\``
        : '⚠️ Failed to update (Supabase error).',
      { parse_mode: 'Markdown' },
    );
    return;
  }

  if (command === '/forceupdate' || command.startsWith('/forceupdate ')) {
    const arg = fullText.slice('/forceupdate'.length).trim().toLowerCase();
    let newValue: boolean;
    if (arg === 'on' || arg === '1' || arg === 'true') {
      newValue = true;
    } else if (arg === 'off' || arg === '0' || arg === 'false') {
      newValue = false;
    } else {
      // Toggle current value
      const config = await getAppConfig(ctx.supabase);
      newValue = !(config?.force_update ?? false);
    }
    const ok = await updateAppConfig(ctx.supabase, { force_update: newValue });
    await showResult(
      ctx,
      chatId,
      ok
        ? `✅ Force update: ${newValue ? '✅ ON' : '❌ OFF'}`
        : '⚠️ Failed to update (Supabase error).',
      { parse_mode: 'Markdown' },
    );
    return;
  }

  // ── Proxy pool management ─────────────────────────────────

  if (command === '/addproxy' || command.startsWith('/addproxy ')) {
    const arg = fullText.slice('/addproxy'.length).trim();
    if (!arg) {
      await showResult(
        ctx,
        chatId,
        'Usage: `/addproxy tg://proxy?server=..&port=..&secret=..`\nor `/addproxy mtproto://secret@host:port`\nor `/addproxy host:port:secret`',
        { parse_mode: 'Markdown' },
      );
      return;
    }
    const p = parseProxyInput(arg);
    if (!p) {
      await showResult(
        ctx,
        chatId,
        'Could not parse that proxy. Use `tg://proxy?...`, `mtproto://secret@host:port`, or `host:port:secret`.',
        { parse_mode: 'Markdown' },
      );
      return;
    }
    const res = await addScraperProxy(ctx.supabase, p.host, p.port, p.secret);
    const text =
      res === 'added'
        ? `✅ Added proxy \`${p.host}:${p.port}\` to the pool.`
        : res === 'exists'
          ? `Already in the pool: \`${p.host}:${p.port}\``
          : '⚠️ Could not save proxy (Supabase error).';
    await showResult(ctx, chatId, text, { parse_mode: 'Markdown' });
    return;
  }

  if (command === '/delproxy' || command.startsWith('/delproxy ')) {
    const arg = fullText.slice('/delproxy'.length).trim();
    if (!arg) {
      await showResult(
        ctx,
        chatId,
        'Usage: `/delproxy <host>` or `/delproxy <id>` or `/delproxy all`',
        { parse_mode: 'Markdown' },
      );
      return;
    }
    const deleted = arg === 'all' ? await deleteAllScraperProxies(ctx.supabase) : await deleteScraperProxy(ctx.supabase, arg);
    await showResult(ctx, chatId, `🗑 Deleted ${deleted} proxy/proxies.`);
    return;
  }

  if (command === '/listproxy') {
    const rows = await listScraperProxies(ctx.supabase);
    if (rows.length === 0) {
      await showResult(ctx, chatId, 'Proxy pool is empty. Add one with /addproxy.');
      return;
    }
    const lines = rows.map((r) => {
      const status = !r.is_active ? '❌ dead' : r.last_ok === true ? '✅ ok' : '🟡 untested';
      const last = r.last_checked ? ` last:${r.last_checked.slice(0, 16)}` : '';
      return `${status} \`${r.id}\`. \`${r.host}:${r.port}\`${last}`;
    });
    await showResult(
      ctx,
      chatId,
      `*MTProto proxy pool (${rows.length})*\n\n${lines.join('\n')}`,
      { parse_mode: 'Markdown' },
    );
    return;
  }

  // ── Scraper run (on-demand via GitHub Actions) ─────────────

  if (command === '/scrape' || command.startsWith('/scrape ')) {
    await dispatchScrape(ctx, chatId);
    return;
  }

  // ── VLESS channel list management ─────────────────────────

  if (command === '/addchannel' || command.startsWith('/addchannel ')) {
    const arg = fullText.slice('/addchannel'.length).trim().replace(/^@/, '').trim();
    if (!arg) {
      await showResult(ctx, chatId, 'Usage: `/addchannel @channel_username`', {
        parse_mode: 'Markdown',
      });
      return;
    }
    const channels = await getScraperChannels(ctx.supabase);
    if (channels.includes(arg)) {
      await showResult(ctx, chatId, `@${arg} is already in the scraper channel list.`);
      return;
    }
    channels.push(arg);
    if (await setScraperChannels(ctx.supabase, channels)) {
      await showResult(ctx, chatId, `✅ Added @${arg} to the scraper channels.`);
    } else {
      await showResult(ctx, chatId, '⚠️ Could not save channels (Supabase error).');
    }
    return;
  }

  if (command === '/delchannel' || command.startsWith('/delchannel ')) {
    const arg = fullText.slice('/delchannel'.length).trim().replace(/^@/, '').trim();
    if (!arg) {
      await showResult(ctx, chatId, 'Usage: `/delchannel @channel_username`', {
        parse_mode: 'Markdown',
      });
      return;
    }
    const channels = await getScraperChannels(ctx.supabase);
    const idx = channels.indexOf(arg);
    if (idx === -1) {
      await showResult(ctx, chatId, `@${arg} is not in the scraper channel list.`);
      return;
    }
    channels.splice(idx, 1);
    if (await setScraperChannels(ctx.supabase, channels)) {
      await showResult(ctx, chatId, `🗑 Removed @${arg} from the scraper channels.`);
    } else {
      await showResult(ctx, chatId, '⚠️ Could not save channels (Supabase error).');
    }
    return;
  }

  if (command === '/listchannel') {
    const channels = await getScraperChannels(ctx.supabase);
    const text =
      channels.length === 0
        ? 'No channels configured yet. Add one with /addchannel.'
        : `*Channels (${channels.length})*\n\n${channels.map((c) => `@${c}`).join('\n')}`;
    await showResult(ctx, chatId, text, { parse_mode: 'Markdown' });
    return;
  }

  // Unknown command -> main menu.
  await showMenu(ctx, chatId, MENU_TEXT, { reply_markup: mainKeyboard() });
}

/**
 * Dispatch the vless-scraper GitHub Actions workflow and report the
 * outcome in the given chat. Shared by /scrape and the "Run scrape"
 * scraper-menu button.
 */
async function dispatchScrape(ctx: BotContext, chatId: number, force = false): Promise<void> {
  const token = ctx.token;
  const ghPat = Deno.env.get('GH_PAT') ?? '';
  if (!ghPat) {
    await showResult(
      ctx,
      chatId,
      '⚠️ `/scrape` is not configured yet — the GitHub token (GH_PAT function secret) is missing.',
      { parse_mode: 'Markdown' },
    );
    return;
  }

  // Check last scrape time (unless forced)
  if (!force) {
    const lastScrape = await getLastScrapeTime(ctx.supabase);
    const now = new Date();
    const minIntervalMs = 5 * 60 * 1000; // 5 minutes
    if (lastScrape) {
      const lastScrapeTime = new Date(lastScrape).getTime();
      const elapsedMs = now.getTime() - lastScrapeTime;
      if (elapsedMs < minIntervalMs) {
        const remainingSec = Math.ceil((minIntervalMs - elapsedMs) / 1000);
        const sentId = await showResult(
          ctx,
          chatId,
          `⏳ **Scraper recently ran** ${formatDuration(elapsedMs)} ago.\n\n` +
          `To avoid hitting Telegram/GitHub rate limits, please wait **${remainingSec}s** before running again.\n\n` +
          `Are you sure you want to run it now anyway?`,
          { parse_mode: 'Markdown', reply_markup: confirmScrapeKeyboard() },
        );
        // Store pending confirmation in chat state
        await saveChatState(ctx.supabase, {
          chatId,
          pendingScrapeConfirm: true,
          scrapeMessageId: sentId,
        });
        return;
      }
    }

    // Check if a workflow is already running/queued
    const running = await checkRunningWorkflow(ghPat);
    if (running) {
      await showResult(
        ctx,
        chatId,
        '🔄 **Scraper is already running** (or queued) on GitHub Actions.\n\n' +
        'Please wait for the current run to complete before starting a new one.\n' +
        'You\'ll get a notification here when it finishes.',
        { parse_mode: 'Markdown' },
      );
      return;
    }
  }

  const repo = Deno.env.get('GH_REPO') ?? 'ahmad43ir/rootnet';
  const ref = Deno.env.get('GH_REF') ?? 'master';

  const sentId = await showResult(ctx, chatId, '🚀 Dispatching scraper run...');
  const res = await dispatchWorkflow({
    pat: ghPat,
    repo,
    workflowFile: 'scrape.yml',
    ref,
    inputs: { chat_id: String(chatId) },
  });

  if (res.ok) {
    await setLastScrapeTime(ctx.supabase);
    const text =
      '✅ Scraper run started on GitHub Actions.\n\n' +
      'It connects through the proxy pool, scans the config channels, and posts new configs to the worker. ' +
      'I\'ll report the result here in ~2–5 minutes.\n\n' +
      'If it can\'t connect (broken proxy), tap "➕ Add proxy" in the Scraper menu and send a working one, then run it again.';
    if (sentId !== null) {
      await tg.editMessageText(token, chatId, sentId, text, { parse_mode: 'Markdown' });
    } else {
      await showResult(ctx, chatId, text, { parse_mode: 'Markdown' });
    }
  } else {
    const text =
      `❌ Could not start the run (HTTP ${res.status ?? 'network error'}).\n` +
      `${(res.body ?? '').slice(0, 200)}\n\n` +
      `Make sure the GH_PAT token has "Actions: read & write" access on ${repo} and the scraper branch is ${ref}.`;
    if (sentId !== null) {
      await tg.editMessageText(token, chatId, sentId, text);
    } else {
      await showResult(ctx, chatId, text);
    }
  }
}

/** 3-segment semver compare: 1 if a>b, -1 if a<b, 0 if equal (matches
 *  the Android app's compareVersions in VersionInfo.kt). */
function compareVersion(a: string, b: string): number {
  const pa = a.split('.').map((n) => parseInt(n, 10) || 0);
  const pb = b.split('.').map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < 3; i++) {
    const x = pa[i] ?? 0;
    const y = pb[i] ?? 0;
    if (x > y) return 1;
    if (x < y) return -1;
  }
  return 0;
}

/** Format milliseconds into human-readable duration. */
function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  const sec = Math.floor(ms / 1000);
  if (sec < 60) return `${sec}s`;
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}m ${sec % 60}s`;
  const hr = Math.floor(min / 60);
  return `${hr}h ${min % 60}m`;
}

/** Keyboard for confirming scrape despite recent run. */
function confirmScrapeKeyboard(): unknown {
  return {
    inline_keyboard: [
      [
        { text: '✅ Yes, run anyway', callback_data: 'scrape:confirm' },
        { text: '❌ Cancel', callback_data: 'scrape:cancel' },
      ],
    ],
  };
}

/** Check if a scraper workflow is currently running or queued. */
async function checkRunningWorkflow(ghPat: string): Promise<boolean> {
  try {
    const repo = Deno.env.get('GH_REPO') ?? 'ahmad43ir/rootnet';
    const response = await fetch(
      `https://api.github.com/repos/${repo}/actions/runs?workflow_id=scrape.yml&status=in_progress&status=queued&per_page=5`,
      {
        headers: {
          Authorization: `Bearer ${ghPat}`,
          Accept: 'application/vnd.github+json',
          'X-GitHub-Api-Version': '2022-11-28',
        },
      },
    );
    if (!response.ok) return false;
    const data = await response.json();
    return (data.workflow_runs?.length ?? 0) > 0;
  } catch {
    return false; // On error, assume not running to not block
  }
}

/**
 * Route a plain-text message that the bot is waiting for after a menu
 * button press (e.g. a proxy link or a channel name).
 */
async function handlePendingInput(
  ctx: BotContext,
  chatId: number,
  state: any,
  text: string,
): Promise<void> {
  const mode: string = state.pendingInput ?? '';
  await saveChatState(ctx.supabase, { ...state, pendingInput: null });

  if (mode === 'proxy') {
    const p = parseProxyInput(text);
    if (!p) {
      await showResult(
        ctx,
        chatId,
        'Could not parse that as an MTProto proxy. Use `tg://proxy?...`, `mtproto://secret@host:port`, or `host:port:secret`.',
        { parse_mode: 'Markdown' },
      );
      return;
    }
    const res = await addScraperProxy(ctx.supabase, p.host, p.port, p.secret);
    const msg =
      res === 'added'
        ? `✅ Added proxy \`${p.host}:${p.port}\` to the pool.`
        : res === 'exists'
          ? `Already in the pool: \`${p.host}:${p.port}\``
          : '⚠️ Could not save proxy (Supabase error).';
    await showResult(ctx, chatId, msg, { parse_mode: 'Markdown' });
    return;
  }

  if (mode === 'channel') {
    const arg = text.trim().replace(/^@/, '').trim();
    if (!arg) {
      await showResult(ctx, chatId, 'Send a channel username, e.g. `@myvlesschannel`.', {
        parse_mode: 'Markdown',
      });
      return;
    }
    const channels = await getScraperChannels(ctx.supabase);
    if (channels.includes(arg)) {
      await showResult(ctx, chatId, `@${arg} is already in the scraper channel list.`);
      return;
    }
    channels.push(arg);
    if (await setScraperChannels(ctx.supabase, channels)) {
      await showResult(ctx, chatId, `✅ Added @${arg} to the scraper channels.`);
    } else {
      await showResult(ctx, chatId, '⚠️ Could not save channels (Supabase error).');
    }
    return;
  }

  // ── Version management pending inputs ─────────────────────

  if (mode === 'setmin') {
    const arg = text.trim();
    if (!arg || !/^\d+\.\d+\.\d+$/.test(arg)) {
      await showResult(ctx, chatId, 'Invalid format. Use `X.Y.Z`, e.g. `2.0.1`.', {
        parse_mode: 'Markdown',
      });
      return;
    }
    const config = await getAppConfig(ctx.supabase);
    if (config?.latest_version && compareVersion(arg, config.latest_version) > 0) {
      await showResult(
        ctx,
        chatId,
        `⚠️ Cannot set minimum \`${arg}\` — it is NEWER than latest (\`${config.latest_version}\`). Set /setlatest first.`,
        { parse_mode: 'Markdown', reply_markup: versionKeyboard() },
      );
      return;
    }
    const ok = await updateAppConfig(ctx.supabase, { minimum_version: arg });
    await showResult(
      ctx,
      chatId,
      ok
        ? `✅ Minimum version set to \`${arg}\`\n\nApps below this version will be blocked.`
        : '⚠️ Failed to update (Supabase error).',
      { parse_mode: 'Markdown', reply_markup: versionKeyboard() },
    );
    return;
  }

  if (mode === 'setlatest') {
    const arg = text.trim();
    if (!arg || !/^\d+\.\d+\.\d+$/.test(arg)) {
      await showResult(ctx, chatId, 'Invalid format. Use `X.Y.Z`, e.g. `2.1.0`.', {
        parse_mode: 'Markdown',
      });
      return;
    }
    const config = await getAppConfig(ctx.supabase);
    if (config?.minimum_version && compareVersion(arg, config.minimum_version) < 0) {
      await showResult(
        ctx,
        chatId,
        `⚠️ Cannot set latest \`${arg}\` — it is OLDER than minimum (\`${config.minimum_version}\`). Fix /setmin first.`,
        { parse_mode: 'Markdown', reply_markup: versionKeyboard() },
      );
      return;
    }
    const ok = await updateAppConfig(ctx.supabase, { latest_version: arg });
    await showResult(
      ctx,
      chatId,
      ok
        ? `✅ Latest version set to \`${arg}\``
        : '⚠️ Failed to update (Supabase error).',
      { parse_mode: 'Markdown', reply_markup: versionKeyboard() },
    );
    return;
  }

  if (mode === 'setbuild') {
    const arg = text.trim();
    const num = parseInt(arg, 10);
    if (!arg || isNaN(num) || num < 0) {
      await showResult(ctx, chatId, 'Invalid number. Use a positive integer, e.g. `300`.', {
        parse_mode: 'Markdown',
      });
      return;
    }
    const ok = await updateAppConfig(ctx.supabase, { latest_build: num });
    await showResult(
      ctx,
      chatId,
      ok
        ? `✅ Latest build set to \`${num}\``
        : '⚠️ Failed to update (Supabase error).',
      { parse_mode: 'Markdown', reply_markup: versionKeyboard() },
    );
    return;
  }
}

// ─── Messages ────────────────────────────────────────────────

/** Channel handle from where the message was forwarded from, if any. */
function channelFromForward(message: any): string | null {
  // Bot API 7.0+ uses forward_origin; older forwards carry forward_from_chat.
  const chat = message.forward_origin?.chat ?? message.forward_from_chat ?? null;
  if (!chat || chat.type !== 'channel') return null;
  const username = chat.username;
  if (typeof username === 'string' && username) return `@${username}`;
  return null;
}

async function handleMessage(ctx: BotContext, update: any): Promise<void> {
  const token = ctx.token;
  const message = update.message;
  const chatId: number = message.chat.id;
  const userId: number | undefined = message.from?.id;
  const text: string = message.text ?? '';

  // Commands first (command handlers do their own admin checks).
  if (text.startsWith('/')) {
    const st = await getChatState(ctx.supabase, chatId);
    if (st.pendingInput) await saveChatState(ctx.supabase, { ...st, pendingInput: null });
    await handleCommand(ctx, chatId, userId, text);
    return;
  }

  if (!isAdmin(ctx, userId)) {
    await showResult(
      ctx,
      chatId,
      `Access denied. (Your Telegram user ID is \`${userId}\` — type /myid.)`,
      { parse_mode: 'Markdown' },
    );
    return;
  }

  // Menu buttons.
  if (text === MENU_BTN_UPLOAD) {
    await showMenu(
      ctx,
      chatId,
      'Send me a `.txt`, `.npv`, `.npvt`, `.json`, or `.sip` file with VPN configs, or paste the text. ' +
        "They'll be uploaded as servers. The raw file is also saved to the VPN Files section " +
        '(so encrypted `.npvt` / `.npv` stay downloadable).',
      { parse_mode: 'Markdown', reply_markup: mainKeyboard() },
    );
    return;
  }

  if (text === MENU_BTN_SERVERS) {
    await sendServersList(ctx, chatId);
    return;
  }

  if (text === MENU_BTN_WEB) {
    const buttons: Record<string, unknown>[][] = WEB_LINKS.map(([label, url]) => [
      { text: label, url },
    ]);
    buttons.push([{ text: '◀ Menu', callback_data: 'menu' }]);
    await showMenu(ctx, chatId, '🔗 Web pages', {
      reply_markup: { inline_keyboard: buttons },
    });
    return;
  }

  if (text === MENU_BTN_HELP) {
    await showMenu(ctx, chatId, HELP_TEXT, {
      parse_mode: 'Markdown',
      reply_markup: mainKeyboard(),
    });
    return;
  }

  if (text === MENU_BTN_SCRAPER) {
    await showMenu(ctx, chatId, SCRAPER_TEXT, {
      parse_mode: 'Markdown',
      reply_markup: scraperKeyboard(),
    });
    return;
  }

  if (text === MENU_BTN_FILES) {
    await sendVpnFilesList(ctx, chatId, 0);
    return;
  }

  if (text === MENU_BTN_VERSION) {
    const config = await getAppConfig(ctx.supabase);
    const text2 = config
      ? VERSION_TEXT +
        `\n\n*Current:* \`${config.latest_version}\` (build ${config.latest_build})\n` +
        `*Minimum:* \`${config.minimum_version}\`\n` +
        `*Force:* ${config.force_update ? '✅ ON' : '❌ OFF'}`
      : VERSION_TEXT;
    await showMenu(ctx, chatId, text2, {
      parse_mode: 'Markdown',
      reply_markup: versionKeyboard(),
    });
    return;
  }

  // If the bot is waiting for a value (e.g. a proxy link or channel
  // name after tapping a scraper button), route it to that handler.
  const pendingState = await getChatState(ctx.supabase, chatId);
  if (pendingState.pendingInput) {
    await handlePendingInput(ctx, chatId, pendingState, text);
    return;
  }

  // Otherwise: config content (pasted text or a document).
  let content: string;
  let source: string;
  let vpnFileNote: string | null = null; // raw-file save status shown in replies
  if (message.document) {
    const filePath = await tg.getFile(token, message.document.file_id);
    if (!filePath) {
      await showResult(ctx, chatId, 'Could not read the file.');
      return;
    }
    const fileName = message.document.file_name ?? 'document';
    try {
      content = await tg.downloadFileText(token, filePath);
      source = fileName;
    } catch {
      await showResult(ctx, chatId, 'Could not read the file.');
      return;
    }
    // Store the raw attachment in vpn_files too (app File tab) — even when
    // configs can't be auto-parsed (e.g. encrypted .npvt). Mirrors the
    // scraper's FILE_UPLOAD_EXTENSIONS upload path.
    if (FILE_UPLOAD_EXTENSIONS.some((ext) => fileName.toLowerCase().endsWith(ext))) {
      try {
        const bytes = await tg.downloadFileBytes(token, filePath);
        const saved = await saveVpnFile(ctx.supabase, {
          filename: fileName,
          mime_type: message.document.mime_type ?? null,
          size_bytes: bytes.length,
          contentBase64: bytesToBase64(bytes),
          uploaded_by: userId,
        });
        vpnFileNote = saved.saved
          ? `📁 Raw file saved to *VPN Files* (${(bytes.length / 1024).toFixed(1)} KB).`
          : saved.duplicate
            ? '📁 File already exists in VPN Files — skipped.'
            : '⚠️ Could not save the raw file to VPN Files.';
      } catch (e) {
        console.warn('[handlers] raw file save failed:', (e as Error).message);
        vpnFileNote = '⚠️ Could not save the raw file to VPN Files.';
      }
    }
  } else if (text) {
    content = text;
    source = 'text';
  } else {
    return; // sticker / photo / etc. — ignore
  }

  if (!content || !content.trim()) {
    await showResult(ctx, chatId, 'No readable content. Send a file or paste text.');
    return;
  }

  console.info(`[handlers] Processing ${content.length} chars from ${source}`);

  const parsed = parseFile(content);
  if (parsed.length === 0) {
    await showResult(
      ctx,
      chatId,
      'No VPN configs found in that. Supported formats: VLESS/VMess/Trojan/SS/WireGuard URIs, or NPV JSON exports.' +
        (vpnFileNote ? `\n\n${vpnFileNote}` : ''),
      { reply_markup: mainKeyboard() },
    );
    return;
  }

  const batchChannel = channelFromForward(message) ?? extractChannel(content);
  const channelLabel = batchChannel ? ` from ${batchChannel}` : '';
  await showResult(ctx, chatId, `Parsed ${parsed.length} config(s)${channelLabel}. Uploading...`);
  const result = await processContent(ctx, content, batchChannel);
  const summary = formatSummary(parsed.length, result);
  await showResult(
    ctx,
    chatId,
    vpnFileNote ? `${summary}\n\n${vpnFileNote}` : summary,
    {
      parse_mode: 'Markdown',
      reply_markup: mainKeyboard(),
    },
  );
}

async function sendServersList(ctx: BotContext, chatId: number): Promise<void> {
  const state = await getChatState(ctx.supabase, chatId);
  await saveChatState(ctx.supabase, { ...state, listMode: true, selectedIds: [] });
  const servers = await fetchServers(ctx.supabase);
  const selected = new Set<number>();
  await showMenu(ctx, chatId, serversSelectText(servers, selected), {
    reply_markup: serversSelectMarkup(servers, selected),
  });
}

const VPN_FILES_PAGE_SIZE = 10;

async function sendVpnFilesList(ctx: BotContext, chatId: number, page: number): Promise<void> {
  const offset = page * VPN_FILES_PAGE_SIZE;
  
  const [files, total] = await Promise.all([
    listVpnFiles(ctx.supabase, { limit: VPN_FILES_PAGE_SIZE, offset }),
    countVpnFiles(ctx.supabase),
  ]);
  
  const hasMore = offset + files.length < total;
  const totalPages = Math.ceil(total / VPN_FILES_PAGE_SIZE);
  
  if (files.length === 0) {
    await showMenu(ctx, chatId, '📁 No VPN files found.', {
      reply_markup: mainKeyboard(),
    });
    return;
  }
  
  let text = `*📁 VPN Files* (page ${page + 1}/${totalPages}, ${total} total)\n\n`;
  text += '🔒 = encrypted (likely .npvt/.npv)\n';
  text += 'Tap a file to download.\n';
  
  await showMenu(ctx, chatId, text, {
    parse_mode: 'Markdown',
    reply_markup: vpnFilesListMarkup(files, page, hasMore),
  });
}

// ─── Callbacks ───────────────────────────────────────────────

async function handleCallback(ctx: BotContext, update: any): Promise<void> {
  const token = ctx.token;
  const query = update.callback_query;
  const data: string = query?.data ?? '';
  const chatId: number | undefined = query.message?.chat?.id;
  const messageId: number | undefined = query.message?.message_id;
  const userId: number | undefined = query.from?.id;

  await tg.answerCallbackQuery(token, query.id);

  if (chatId === undefined || messageId === undefined) return;
  if (!isAdmin(ctx, userId)) {
    await tg.editMessageText(token, chatId, messageId, 'Access denied.');
    return;
  }

  // The tapped message becomes the live menu page (callback handlers
  // edit it in place) — the previously tracked menu, if different, is
  // deleted so keyboards don't pile up in the chat.
  await adoptMenuMessage(ctx, chatId, messageId);

  if (data === 'menu') {
    await tg.editMessageText(token, chatId, messageId, MENU_TEXT, {
      reply_markup: mainKeyboard(),
    });
    return;
  }

  // ── Scraper sub-menu ────────────────────────────────────────

  if (data === 'scraper') {
    await tg.editMessageText(token, chatId, messageId, SCRAPER_TEXT, {
      parse_mode: 'Markdown',
      reply_markup: scraperKeyboard(),
    });
    return;
  }

  if (data === 'scraper:addproxy') {
    const state = await getChatState(ctx.supabase, chatId);
    await saveChatState(ctx.supabase, { ...state, pendingInput: 'proxy' });
    await tg.editMessageText(
      token,
      chatId,
      messageId,
      '✍️ *Send me the proxy to add:*\n\n' +
        '`tg://proxy?server=..&port=..&secret=..`\n' +
        'or `mtproto://secret@host:port`\n' +
        'or `host:port:secret`',
      { parse_mode: 'Markdown', reply_markup: scraperBackKeyboard() },
    );
    return;
  }

  if (data === 'scraper:addchannel') {
    const state = await getChatState(ctx.supabase, chatId);
    await saveChatState(ctx.supabase, { ...state, pendingInput: 'channel' });
    await tg.editMessageText(
      token,
      chatId,
      messageId,
      '✍️ *Send the channel username to add,* e.g. `@myvlesschannel`.',
      { parse_mode: 'Markdown', reply_markup: scraperBackKeyboard() },
    );
    return;
  }

  if (data === 'scraper:listproxy') {
    const rows = await listScraperProxies(ctx.supabase);
    const text =
      rows.length === 0
        ? 'Proxy pool is empty. Tap "➕ Add proxy".'
        : `*MTProto proxy pool (${rows.length})*\n\n${rows
            .map((r) => {
              const status = !r.is_active ? '❌ dead' : r.last_ok === true ? '✅ ok' : '🟡 untested';
              const last = r.last_checked ? ` last:${r.last_checked.slice(0, 16)}` : '';
              return `${status} \`${r.id}\`. \`${r.host}:${r.port}\`${last}`;
            })
            .join('\n')}`;
    await tg.editMessageText(token, chatId, messageId, text, {
      parse_mode: 'Markdown',
      reply_markup: scraperKeyboard(),
    });
    return;
  }

  if (data === 'scraper:listchannel') {
    const channels = await getScraperChannels(ctx.supabase);
    const text =
      channels.length === 0
        ? 'No channels configured yet. Tap "➕ Add channel".'
        : `*Channels (${channels.length})*\n\n${channels.map((c) => `@${c}`).join('\n')}`;
    await tg.editMessageText(token, chatId, messageId, text, {
      parse_mode: 'Markdown',
      reply_markup: scraperKeyboard(),
    });
    return;
  }

  if (data === 'scraper:delproxy') {
    const rows = await listScraperProxies(ctx.supabase);
    if (rows.length === 0) {
      await tg.editMessageText(token, chatId, messageId, 'Proxy pool is empty. Tap "➕ Add proxy".', {
        parse_mode: 'Markdown',
        reply_markup: scraperKeyboard(),
      });
      return;
    }
    const kb: Record<string, unknown>[][] = rows.map((r) => [
      { text: `🗑 ${r.host}:${r.port}`, callback_data: `scraper:confirmdelproxy:${r.id}` },
    ]);
    kb.push([{ text: '🗑 Delete all', callback_data: 'scraper:delallproxies' }]);
    kb.push([{ text: '◀ Scraper', callback_data: 'scraper' }]);
    await tg.editMessageText(token, chatId, messageId, '*Remove a proxy — tap to confirm:*', {
      parse_mode: 'Markdown',
      reply_markup: { inline_keyboard: kb },
    });
    return;
  }

  if (data.startsWith('scraper:confirmdelproxy:')) {
    const id = data.split(':').pop() ?? '';
    const deleted = await deleteScraperProxy(ctx.supabase, id);
    await tg.editMessageText(token, chatId, messageId, `🗑 Deleted ${deleted} proxy/proxies.`, {
      parse_mode: 'Markdown',
      reply_markup: scraperKeyboard(),
    });
    return;
  }

  if (data === 'scraper:delallproxies') {
    await tg.editMessageText(
      token,
      chatId,
      messageId,
      '⚠️ Delete ALL proxies from the pool? This cannot be undone.',
      {
        parse_mode: 'Markdown',
        reply_markup: {
          inline_keyboard: [
            [
              { text: '✅ Yes, delete all', callback_data: 'scraper:confirmdelallproxies' },
              { text: '❌ No', callback_data: 'scraper' },
            ],
          ],
        },
      },
    );
    return;
  }

  if (data === 'scraper:confirmdelallproxies') {
    const deleted = await deleteAllScraperProxies(ctx.supabase);
    await tg.editMessageText(token, chatId, messageId, `🗑 Deleted all ${deleted} proxy/proxies.`, {
      parse_mode: 'Markdown',
      reply_markup: scraperKeyboard(),
    });
    return;
  }

  if (data === 'scraper:delchannel') {
    const channels = await getScraperChannels(ctx.supabase);
    if (channels.length === 0) {
      await tg.editMessageText(
        token,
        chatId,
        messageId,
        'No channels configured yet. Tap "➕ Add channel".',
        { parse_mode: 'Markdown', reply_markup: scraperKeyboard() },
      );
      return;
    }
    const kb: Record<string, unknown>[][] = channels.map((c) => [
      { text: `🗑 @${c}`, callback_data: `scraper:confirmdelchannel:${c}` },
    ]);
    kb.push([{ text: '◀ Scraper', callback_data: 'scraper' }]);
    await tg.editMessageText(token, chatId, messageId, '*Remove a channel — tap to confirm:*', {
      parse_mode: 'Markdown',
      reply_markup: { inline_keyboard: kb },
    });
    return;
  }

  if (data.startsWith('scraper:confirmdelchannel:')) {
    const name = data.slice('scraper:confirmdelchannel:'.length);
    const channels = await getScraperChannels(ctx.supabase);
    const idx = channels.indexOf(name);
    if (idx === -1) {
      await tg.editMessageText(token, chatId, messageId, `@${name} is not in the scraper channel list.`, {
        parse_mode: 'Markdown',
        reply_markup: scraperKeyboard(),
      });
      return;
    }
    channels.splice(idx, 1);
    const ok = await setScraperChannels(ctx.supabase, channels);
    await tg.editMessageText(
      token,
      chatId,
      messageId,
      ok ? `🗑 Removed @${name} from the scraper channels.` : '⚠️ Could not save channels (Supabase error).',
      { parse_mode: 'Markdown', reply_markup: scraperKeyboard() },
    );
    return;
  }

  if (data === 'scraper:scrape') {
    await dispatchScrape(ctx, chatId);
    return;
  }

  if (data === 'scrape:confirm') {
    // User confirmed to run scrape despite recent run
    await saveChatState(ctx.supabase, { chatId, pendingScrapeConfirm: false });
    await dispatchScrape(ctx, chatId, true);
    return;
  }

  if (data === 'scrape:cancel') {
    await saveChatState(ctx.supabase, { chatId, pendingScrapeConfirm: false });
    await tg.editMessageText(token, chatId, messageId, '❌ Scrape cancelled.', {
      reply_markup: scraperKeyboard(),
    });
    return;
  }

  // ── VPN Files callbacks ──────────────────────────────────────

  if (data.startsWith('vpnfile:page:')) {
    const page = parseInt(data.split(':')[2], 10);
    if (!isNaN(page) && page >= 0) {
      await sendVpnFilesList(ctx, chatId, page);
    }
    return;
  }

  if (data.startsWith('vpnfile:download:')) {
    const fileId = parseInt(data.split(':')[2], 10);
    if (!isNaN(fileId)) {
      const file = await getVpnFile(ctx.supabase, fileId);
      if (file) {
        // Decode base64 content
        const content = atob(file.content);
        // Send as document
        await tg.sendDocument(token, chatId, file.filename, content, file.mime_type);
      } else {
        await tg.answerCallbackQuery(token, query.id, 'File not found');
      }
    }
    return;
  }

  // ── Version management callbacks ────────────────────────────

  if (data === 'version') {
    const config = await getAppConfig(ctx.supabase);
    const text = config
      ? VERSION_TEXT +
        `\n\n*Current:* \`${config.latest_version}\` (build ${config.latest_build})\n` +
        `*Minimum:* \`${config.minimum_version}\`\n` +
        `*Force:* ${config.force_update ? '✅ ON' : '❌ OFF'}`
      : VERSION_TEXT;
    await tg.editMessageText(token, chatId, messageId, text, {
      parse_mode: 'Markdown',
      reply_markup: versionKeyboard(),
    });
    return;
  }

  if (data === 'version:show') {
    const config = await getAppConfig(ctx.supabase);
    if (!config) {
      await tg.editMessageText(token, chatId, messageId, '⚠️ Could not read app_config.', {
        reply_markup: versionKeyboard(),
      });
      return;
    }
    const text =
      '*📱 Current version config*\n\n' +
      `Latest version: \`${config.latest_version}\`\n` +
      `Latest build: \`${config.latest_build}\`\n` +
      `Minimum version: \`${config.minimum_version}\`\n` +
      `Force update: ${config.force_update ? '✅ ON' : '❌ OFF'}\n` +
      `Update URL: ${config.update_url}\n` +
      `Release notes: ${config.release_notes || '(none)'}`;
    await tg.editMessageText(token, chatId, messageId, text, {
      parse_mode: 'Markdown',
      reply_markup: versionKeyboard(),
    });
    return;
  }

  if (data === 'version:setmin') {
    const state = await getChatState(ctx.supabase, chatId);
    await saveChatState(ctx.supabase, { ...state, pendingInput: 'setmin' });
    await tg.editMessageText(
      token,
      chatId,
      messageId,
      '✍️ *Send the minimum required version,* e.g. `2.0.1`\n\n' +
        'Apps below this version will be blocked.',
      { parse_mode: 'Markdown', reply_markup: versionBackKeyboard() },
    );
    return;
  }

  if (data === 'version:setlatest') {
    const state = await getChatState(ctx.supabase, chatId);
    await saveChatState(ctx.supabase, { ...state, pendingInput: 'setlatest' });
    await tg.editMessageText(
      token,
      chatId,
      messageId,
      '✍️ *Send the latest version,* e.g. `2.1.0`',
      { parse_mode: 'Markdown', reply_markup: versionBackKeyboard() },
    );
    return;
  }

  if (data === 'version:setbuild') {
    const state = await getChatState(ctx.supabase, chatId);
    await saveChatState(ctx.supabase, { ...state, pendingInput: 'setbuild' });
    await tg.editMessageText(
      token,
      chatId,
      messageId,
      '✍️ *Send the build number,* e.g. `300`',
      { parse_mode: 'Markdown', reply_markup: versionBackKeyboard() },
    );
    return;
  }

  if (data === 'version:forceupdate') {
    const config = await getAppConfig(ctx.supabase);
    await tg.editMessageText(
      token,
      chatId,
      messageId,
      `🔄 *Force update is currently:* ${config?.force_update ? '✅ ON' : '❌ OFF'}\n\nTap to toggle:`,
      { parse_mode: 'Markdown', reply_markup: forceUpdateToggleKeyboard(config?.force_update ?? false) },
    );
    return;
  }

  if (data === 'version:toggleforce') {
    const config = await getAppConfig(ctx.supabase);
    const newValue = !(config?.force_update ?? false);
    const ok = await updateAppConfig(ctx.supabase, { force_update: newValue });
    await tg.editMessageText(
      token,
      chatId,
      messageId,
      ok
        ? `✅ Force update: ${newValue ? '✅ ON' : '❌ OFF'}`
        : '⚠️ Failed to update (Supabase error).',
      { parse_mode: 'Markdown', reply_markup: versionKeyboard() },
    );
    return;
  }

  if (data.startsWith('toggle:')) {
    const serverId = Number(data.split(':')[1]);
    const state = await getChatState(ctx.supabase, chatId);
    const selected = new Set(state.selectedIds);
    if (selected.has(serverId)) selected.delete(serverId);
    else selected.add(serverId);
    await saveChatState(ctx.supabase, { ...state, selectedIds: [...selected] });
    const servers = await fetchServers(ctx.supabase);
    await tg.editMessageText(token, chatId, messageId, serversSelectText(servers, selected), {
      reply_markup: serversSelectMarkup(servers, selected),
    });
    return;
  }

  if (data === 'delbulk') {
    const state = await getChatState(ctx.supabase, chatId);
    const selected = new Set(state.selectedIds);
    if (selected.size === 0) {
      await tg.editMessageText(token, chatId, messageId, 'Nothing selected yet — tap servers first.', {
        reply_markup: inlineMenuButton(),
      });
      return;
    }
    await tg.editMessageText(token, chatId, messageId, `Delete ${selected.size} selected server(s)?`, {
      reply_markup: {
        inline_keyboard: [
          [
            { text: `✅ Yes, delete ${selected.size}`, callback_data: 'confirmbulk' },
            { text: '❌ No', callback_data: 'menu' },
          ],
        ],
      },
    });
    return;
  }

  if (data === 'confirmbulk') {
    const state = await getChatState(ctx.supabase, chatId);
    const ids = [...state.selectedIds];
    let deleted = 0;
    for (const serverId of ids) {
      if (await deleteServer(ctx.supabase, serverId)) deleted++;
      await new Promise((resolve) => setTimeout(resolve, 150));
    }
    const servers = await fetchServers(ctx.supabase);
    await saveChatState(ctx.supabase, { ...state, selectedIds: [] });
    const selected = new Set<number>();
    await tg.editMessageText(
      token,
      chatId,
      messageId,
      `🗑 Deleted ${deleted} server(s).\n\n${serversSelectText(servers, selected)}`,
      { reply_markup: serversSelectMarkup(servers, selected) },
    );
    return;
  }

  if (data === 'delall') {
    const state = await getChatState(ctx.supabase, chatId);
    const servers = await fetchServers(ctx.supabase);
    if (servers.length === 0) {
      await tg.editMessageText(token, chatId, messageId, 'Nothing to delete — list is empty.', {
        reply_markup: inlineMenuButton(),
      });
      return;
    }
    await tg.editMessageText(
      token,
      chatId,
      messageId,
      `Delete ALL ${servers.length} server(s)? ` +
        'This cannot be undone.',
      {
        reply_markup: {
          inline_keyboard: [
            [
              { text: `✅ Yes, delete all ${servers.length}`, callback_data: 'confirmdelall' },
              { text: '❌ No', callback_data: 'menu' },
            ],
          ],
        },
      },
    );
    return;
  }

  if (data === 'confirmdelall') {
    const state = await getChatState(ctx.supabase, chatId);
    const deleted = await deleteAllServers(ctx.supabase);
    const servers = await fetchServers(ctx.supabase);
    await saveChatState(ctx.supabase, { ...state, selectedIds: [] });
    const selected = new Set<number>();
    await tg.editMessageText(
      token,
      chatId,
      messageId,
      `⛔ Deleted all ${deleted} server(s).\n\n` +
        serversSelectText(servers, selected),
      { reply_markup: serversSelectMarkup(servers, selected) },
    );
    return;
  }
}

// ─── Upload processing ───────────────────────────────────────

interface ProcessResult {
  found: number;
  imported: number;
  duplicates: number;
  invalid: number;
  total: number | null;
}

async function processContent(
  ctx: BotContext,
  content: string,
  batchChannel: string | null,
): Promise<ProcessResult> {
  const parsed = parseFile(content);
  if (parsed.length === 0) {
    return { found: 0, imported: 0, duplicates: 0, invalid: 0, total: null };
  }

  let imported = 0;
  let duplicates = 0;
  let invalid = 0;
  for (const entry of parsed) {
    if (await checkDuplicate(ctx.supabase, entry.config)) {
      duplicates++;
      continue;
    }
    // Name servers as "<channel> <number>" in upload order (channels tag
    // their links with tel:@... at the end, or come from the forward origin
    // / @name / t.me text); otherwise "Untitled <number>".
    const channel = extractChannel(entry.config) ?? batchChannel ?? 'Untitled';
    const name = `${channel} ${imported + 1}`;
    if (await insertServer(ctx.supabase, entry, ctx.insertCtx, name)) imported++;
    else invalid++;
    // Be gentle with the geo-api rate limiter between inserts.
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  const total = await countActiveServers(ctx.supabase);
  return { found: parsed.length, imported, duplicates, invalid, total };
}

function formatSummary(uriCount: number, result: ProcessResult): string {
  const lines = [
    `*${uriCount} config(s) received*`,
    `✅ Imported: ${result.imported}`,
    `↔ Duplicates skipped: ${result.duplicates}`,
    `⚠ Failed: ${result.invalid}`,
  ];
  if (result.total !== null) {
    lines.push(`📊 Active servers in DB: ${result.total}`);
  }
  return lines.join('\n');
}