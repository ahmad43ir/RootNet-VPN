// ============================================================
// 📁 _handlers.ts — MESSAGE / CALLBACK / COMMAND HANDLERS
// ============================================================
// Port of telegram-bot/bot.py's handlers. The Python bot was a
// long-poll process; this runs inside the edge function in webhook
// mode. Per-chat UI state is persisted (bot_chat_state) instead of
// living in module memory.
// ============================================================

import * as tg from './_telegram.ts';
import { getChatState, saveChatState } from './_state.ts';
import {
  addBpbService,
  backfillFlags,
  checkDuplicate,
  countActiveServers,
  countVpnFiles,
  deleteAllServers,
  deleteBpbService,
  deleteServer,
  deleteVpnFile,
  fetchServers,
  getAppConfig,
  getVpnFile,
  insertServer,
  listBpbServices,
  listVpnFiles,
  saveVpnFile,
  toggleBpbService,
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
  'RootNet VPN — official config publishing channel: @root_net_manage_bot.\n\n' +
  'New VPN configs (VLESS · VMess · Trojan · SS · Hysteria2 · WireGuard · SOCKS) are published here and flow straight into the app.\n\n' +
  '📤 Upload — import configs as servers\n' +
  '🟢 Servers — list & delete servers\n' +
  '📁 VPN Files — browse & download raw config files (.npvt, .sip, .npv, .json, etc.)\n' +
  '❓ Help — how it works & what each option does';

// Telegram /command menu (registered via setMyCommands so they show up
// in the client's command list when you type "/").
const BOT_COMMANDS: tg.TgBotCommand[] = [
  { command: 'start', description: 'Main menu' },
  { command: 'stats', description: 'Active server count' },
  { command: 'backfillflags', description: 'Geolocate and fix server flags' },
  { command: 'myid', description: 'Show your Telegram user ID' },
  { command: 'version', description: 'Show current version config' },
  { command: 'setmin', description: 'Set minimum required version (e.g. /setmin 2.0.1)' },
  { command: 'setlatest', description: 'Set latest version (e.g. /setlatest 2.1.0)' },
  { command: 'setbuild', description: 'Set latest build number (e.g. /setbuild 300)' },
  { command: 'forceupdate', description: 'Toggle force update on/off' },
  { command: 'subs', description: 'List BPB subscriptions' },
  { command: 'addsub', description: 'Add a BPB sub (/addsub BPB-3 <url>)' },
  { command: 'togglesub', description: 'Activate/deactivate a BPB sub' },
  { command: 'delsub', description: 'Delete a BPB sub (/delsub BPB-1)' },
  { command: 'help', description: 'How everything works' },
];

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
  '🛰 *BPB subscriptions*\n' +
  '`/subs` list subscriptions with active state\n' +
  '`/addsub BPB-3 <sub url>` register a worker subscription\n' +
  '`/togglesub BPB-3` activate/deactivate (inactive subs are skipped)\n' +
  '`/delsub BPB-3` remove it\n\n' +
  '*Commands*: /start menu · /stats server count · /backfillflags fix flags · /myid your Telegram ID · /version version config';



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
      [MENU_BTN_FILES, MENU_BTN_VERSION],
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

/** Send a transient result/status message (upload summary,
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

  // ── BPB subscription management ──────────────────────────────

  if (command === '/subs') {
    const rows = await listBpbServices(ctx.supabase);
    if (!rows) {
      await showResult(ctx, chatId, '⚠️ Could not read bpb_services from Supabase.');
      return;
    }
    if (rows.length === 0) {
      await showResult(
        ctx,
        chatId,
        'No BPB subscriptions registered.\n\nAdd one: `/addsub BPB-1 https://<worker>.workers.dev/<secret>/sub`',
        { parse_mode: 'Markdown' },
      );
      return;
    }
    const active = rows.filter((r) => r.is_active).length;
    const lines = rows.map((r) => {
      let host = r.sub_url;
      try { host = new URL(r.sub_url).host; } catch { /* keep raw */ }
      return `${r.is_active ? '✅' : '❌'} *${r.label}* — ${host}`;
    });
    await showResult(
      ctx,
      chatId,
      `*🛰 BPB subscriptions* (${active}/${rows.length} active)\n\n${lines.join('\n')}\n\n` +
        '`/addsub <label> <url>` · `/togglesub <label>` · `/delsub <label>`',
      { parse_mode: 'Markdown' },
    );
    return;
  }

  if (command === '/addsub' || command.startsWith('/addsub ')) {
    const arg = fullText.slice('/addsub'.length).trim();
    const parts = arg.split(/\s+/);
    if (parts.length < 2 || !parts[0] || !parts[1]) {
      await showResult(
        ctx,
        chatId,
        'Usage: `/addsub BPB-3 https://<worker>.workers.dev/<secret>/sub/raw`',
        { parse_mode: 'Markdown' },
      );
      return;
    }
    const label = parts[0];
    const url = parts.slice(1).join('');
    if (!/^https?:\/\//i.test(url)) {
      await showResult(ctx, chatId, '⚠️ The subscription URL must start with http:// or https://');
      return;
    }
    const result = await addBpbService(ctx.supabase, label, url);
    await showResult(
      ctx,
      chatId,
      result.ok
        ? `✅ Subscription *${label}* added and active.\nThe app's Refresh will pick it up (random pick among active subs).`
        : `⚠️ ${result.error ?? 'Failed to add.'}`,
      { parse_mode: 'Markdown' },
    );
    return;
  }

  if (command === '/delsub' || command.startsWith('/delsub ')) {
    const label = fullText.slice('/delsub'.length).trim();
    if (!label) {
      await showResult(ctx, chatId, 'Usage: `/delsub BPB-1`', { parse_mode: 'Markdown' });
      return;
    }
    const deleted = await deleteBpbService(ctx.supabase, label);
    await showResult(
      ctx,
      chatId,
      deleted > 0
        ? `🗑 Deleted subscription *${label}*.`
        : deleted === 0
          ? `No subscription labeled *${label}*. See /subs`
          : '⚠️ Failed to delete (Supabase error).',
      { parse_mode: 'Markdown' },
    );
    return;
  }

  if (command === '/togglesub' || command.startsWith('/togglesub ')) {
    const label = fullText.slice('/togglesub'.length).trim();
    if (!label) {
      await showResult(ctx, chatId, 'Usage: `/togglesub BPB-1`', { parse_mode: 'Markdown' });
      return;
    }
    const next = await toggleBpbService(ctx.supabase, label);
    await showResult(
      ctx,
      chatId,
      next === null
        ? `No subscription labeled *${label}*. See /subs`
        : `${next ? '✅ Activated' : '❌ Deactivated'} *${label}*.` +
            (next ? '' : '\nInactive subs are skipped by the app.'),
      { parse_mode: 'Markdown' },
    );
    return;
  }

  // Unknown command -> main menu.
  await showMenu(ctx, chatId, MENU_TEXT, { reply_markup: mainKeyboard() });
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

/**
 * Route a plain-text message that the bot is waiting for after a menu
 * button press.
 */
async function handlePendingInput(
  ctx: BotContext,
  chatId: number,
  state: any,
  text: string,
): Promise<void> {
  const mode: string = state.pendingInput ?? '';
  await saveChatState(ctx.supabase, { ...state, pendingInput: null });

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

  if (text === MENU_BTN_HELP) {
    await showMenu(ctx, chatId, HELP_TEXT, {
      parse_mode: 'Markdown',
      reply_markup: mainKeyboard(),
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

  // If the bot is waiting for a value (e.g. a version number after
  // tapping a menu button), route it to that handler.
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