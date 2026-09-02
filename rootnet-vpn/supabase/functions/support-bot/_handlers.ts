// ============================================================
// 📁 _handlers.ts — SUPPORT BOT COMMAND HANDLERS
// ============================================================
// Customer-facing bot for VlessHub & RootNet VPN support.
// Commands:
//   /start    — Welcome menu with inline buttons
//   /help     — How to use the bot
//   /download — Download links for apps
//   /contact  — Contact support
//   /faq      — Frequently asked questions
//   /status   — Service status
// ============================================================

import * as tg from './_telegram.ts';
import { log, escapeMarkdown } from './_utils.ts';

export interface BotContext {
  token: string;
  supabase: any;
  contactEmail: string;
  githubRepoVlessHub: string;
  githubRepoRootNetVPN: string;
}

// ─── Inline Keyboards ────────────────────────────────────────

function mainMenuKeyboard() {
  return {
    inline_keyboard: [
      [
        { text: '📥 Download Apps', callback_data: 'dl_menu' },
        { text: '❓ FAQ', callback_data: 'faq_menu' },
      ],
      [
        { text: '📞 Contact Support', callback_data: 'contact_menu' },
        { text: '📊 Service Status', callback_data: 'status_check' },
      ],
      [
        { text: '🌐 Website', url: 'https://chobgroup.pages.dev' },
      ],
    ],
  };
}

function downloadMenuKeyboard(ctx: BotContext) {
  return {
    inline_keyboard: [
      [
        { text: '📱 VlessHub (Android)', callback_data: 'dl_vlesshub' },
      ],
      [
        { text: '🔐 RootNet VPN (Android)', callback_data: 'dl_rootnet' },
      ],
      [
        { text: '← Back', callback_data: 'main_menu' },
      ],
    ],
  };
}

function vlesshubDownloadKeyboard(ctx: BotContext) {
  return {
    inline_keyboard: [
      [
        { text: '⬇️ Download Latest Release', url: `https://github.com/${ctx.githubRepoVlessHub}/releases/latest` },
      ],
      [
        { text: '📋 View All Releases', url: `https://github.com/${ctx.githubRepoVlessHub}/releases` },
      ],
      [
        { text: '📖 View Source Code', url: `https://github.com/${ctx.githubRepoVlessHub}` },
      ],
      [
        { text: '← Back', callback_data: 'dl_menu' },
      ],
    ],
  };
}

function rootnetDownloadKeyboard(ctx: BotContext) {
  return {
    inline_keyboard: [
      [
        { text: '⬇️ Download Latest Release', url: `https://github.com/${ctx.githubRepoRootNetVPN}/releases/latest` },
      ],
      [
        { text: '📋 View All Releases', url: `https://github.com/${ctx.githubRepoRootNetVPN}/releases` },
      ],
      [
        { text: '📖 View Source Code', url: `https://github.com/${ctx.githubRepoRootNetVPN}` },
      ],
      [
        { text: '← Back', callback_data: 'dl_menu' },
      ],
    ],
  };
}

function contactMenuKeyboard(ctx: BotContext) {
  return {
    inline_keyboard: [
      [
        { text: '📧 Send Email', url: `mailto:${ctx.contactEmail}` },
      ],
      [
        { text: '💬 Telegram Channel', url: 'https://t.me/rootnet_vpn' },
      ],
      [
        { text: '🌐 Website', url: 'https://chobgroup.pages.dev' },
      ],
      [
        { text: '← Back', callback_data: 'main_menu' },
      ],
    ],
  };
}

function faqMenuKeyboard() {
  return {
    inline_keyboard: [
      [
        { text: '❓ What is VlessHub?', callback_data: 'faq_what' },
      ],
      [
        { text: '🔧 How to use?', callback_data: 'faq_how' },
      ],
      [
        { text: '⚠️ Not working?', callback_data: 'faq_troubleshoot' },
      ],
      [
        { text: '🔒 Is it safe?', callback_data: 'faq_safety' },
      ],
      [
        { text: '← Back', callback_data: 'main_menu' },
      ],
    ],
  };
}

function backToMainKeyboard() {
  return {
    inline_keyboard: [
      [{ text: '← Back to Menu', callback_data: 'main_menu' }],
    ],
  };
}

// ─── FAQ Texts ───────────────────────────────────────────────

const FAQ_TEXTS: Record<string, string> = {
  faq_what: [
    '*What is VlessHub?*',
    '',
    'VlessHub is a free Android app that provides:',
    '• VLESS/VMess VPN configs from Telegram channels',
    '• MTProto proxies for Telegram',
    '• VPN config files (.npvt, .sip, etc.)',
    '',
    'It automatically scrapes configs from public channels and lets you import them into your favorite VPN client (v2rayNG, NekoBox, Hiddify, etc.).',
  ].join('\n'),

  faq_how: [
    '*How to use VlessHub?*',
    '',
    '1️⃣ Download and install the app',
    '2️⃣ Open the app — configs are loaded automatically',
    '3️⃣ Tap *Copy* to copy a config to clipboard',
    '4️⃣ Open your VPN client (v2rayNG/NekoBox/Hiddify)',
    '5️⃣ Import from clipboard',
    '',
    'For MTProto proxies:',
    '• Go to the *MTProto* tab',
    '• Tap *Get 10 proxies*',
    '• Tap *Open* to add directly to Telegram',
  ].join('\n'),

  faq_troubleshoot: [
    '*Config not working?*',
    '',
    '• Try a different config — some may be expired',
    '• Check your internet connection',
    '• Make sure your VPN client is up to date',
    '• Try the *Refresh* button to get fresh configs',
    '',
    'If the problem persists, contact support with:',
    '• Your device model',
    '• Android version',
    '• VPN client app name and version',
  ].join('\n'),

  faq_safety: [
    '*Is it safe?*',
    '',
    '• VlessHub is *open source* — you can verify the code',
    '• We only aggregate *public* configs from Telegram channels',
    '• We *never* store your personal data',
    '• The app runs *locally* on your device',
    '',
    '⚠️ Always use VPN services responsibly and follow your local laws.',
  ].join('\n'),
};

// ─── Command Router ──────────────────────────────────────────

export async function routeUpdate(ctx: BotContext, update: any): Promise<void> {
  // ── Callback queries (inline button presses) ──
  if (update.callback_query) {
    const cq = update.callback_query;
    const data = cq.data as string;
    const chatId = cq.message?.chat?.id;
    const messageId = cq.message?.message_id;

    if (!chatId || !messageId) return;

    if (data === 'main_menu') {
      await tg.answerCallbackQuery(ctx.token, cq.id);
      await tg.editMessageText(ctx.token, chatId, messageId, welcomeText(), {
        parse_mode: 'Markdown',
        reply_markup: mainMenuKeyboard(),
      });
    } else if (data === 'dl_menu') {
      await tg.answerCallbackQuery(ctx.token, cq.id);
      await tg.editMessageText(ctx.token, chatId, messageId, downloadText(), {
        parse_mode: 'Markdown',
        reply_markup: downloadMenuKeyboard(ctx),
      });
    } else if (data === 'dl_vlesshub') {
      await tg.answerCallbackQuery(ctx.token, cq.id);
      await tg.editMessageText(ctx.token, chatId, messageId, vlesshubText(), {
        parse_mode: 'Markdown',
        reply_markup: vlesshubDownloadKeyboard(ctx),
      });
    } else if (data === 'dl_rootnet') {
      await tg.answerCallbackQuery(ctx.token, cq.id);
      await tg.editMessageText(ctx.token, chatId, messageId, rootnetText(), {
        parse_mode: 'Markdown',
        reply_markup: rootnetDownloadKeyboard(ctx),
      });
    } else if (data === 'contact_menu') {
      await tg.answerCallbackQuery(ctx.token, cq.id);
      await tg.editMessageText(ctx.token, chatId, messageId, contactText(ctx), {
        parse_mode: 'Markdown',
        reply_markup: contactMenuKeyboard(ctx),
      });
    } else if (data === 'faq_menu') {
      await tg.answerCallbackQuery(ctx.token, cq.id);
      await tg.editMessageText(ctx.token, chatId, messageId, faqIntroText(), {
        parse_mode: 'Markdown',
        reply_markup: faqMenuKeyboard(),
      });
    } else if (data === 'status_check') {
      await tg.answerCallbackQuery(ctx.token, cq.id, 'Checking...');
      const statusText = await checkStatus(ctx);
      await tg.editMessageText(ctx.token, chatId, messageId, statusText, {
        parse_mode: 'Markdown',
        reply_markup: backToMainKeyboard(),
      });
    } else if (FAQ_TEXTS[data]) {
      await tg.answerCallbackQuery(ctx.token, cq.id);
      await tg.editMessageText(ctx.token, chatId, messageId, FAQ_TEXTS[data], {
        parse_mode: 'Markdown',
        reply_markup: faqMenuKeyboard(),
      });
    } else {
      await tg.answerCallbackQuery(ctx.token, cq.id, 'Unknown action');
    }
    return;
  }

  // ── Text messages ──
  if (update.message) {
    const msg = update.message;
    const chatId = msg.chat?.id;
    const text = (msg.text ?? '').trim();

    if (!chatId) return;

    // Handle document uploads (for future file support)
    if (msg.document) {
      await tg.sendMessage(ctx.token, chatId, '📎 Thanks for the file! Our support team will review it.', {
        reply_markup: backToMainKeyboard(),
      });
      return;
    }

    const command = text.split(' ')[0].toLowerCase();

    if (command === '/start' || command === '/menu') {
      await tg.sendMessage(ctx.token, chatId, welcomeText(), {
        parse_mode: 'Markdown',
        reply_markup: mainMenuKeyboard(),
      });
    } else if (command === '/help') {
      await tg.sendMessage(ctx.token, chatId, helpText(), {
        parse_mode: 'Markdown',
        reply_markup: mainMenuKeyboard(),
      });
    } else if (command === '/download' || command === '/dl') {
      await tg.sendMessage(ctx.token, chatId, downloadText(), {
        parse_mode: 'Markdown',
        reply_markup: downloadMenuKeyboard(ctx),
      });
    } else if (command === '/contact') {
      await tg.sendMessage(ctx.token, chatId, contactText(ctx), {
        parse_mode: 'Markdown',
        reply_markup: contactMenuKeyboard(ctx),
      });
    } else if (command === '/faq') {
      await tg.sendMessage(ctx.token, chatId, faqIntroText(), {
        parse_mode: 'Markdown',
        reply_markup: faqMenuKeyboard(),
      });
    } else if (command === '/status') {
      const statusText = await checkStatus(ctx);
      await tg.sendMessage(ctx.token, chatId, statusText, {
        parse_mode: 'Markdown',
        reply_markup: backToMainKeyboard(),
      });
    } else {
      // Unknown command — show menu
      await tg.sendMessage(ctx.token, chatId, '🤔 I didn\'t understand that. Here\'s what I can help with:', {
        reply_markup: mainMenuKeyboard(),
      });
    }
  }
}

// ─── Text Templates ──────────────────────────────────────────

function welcomeText(): string {
  return [
    '👋 *Welcome to RootNet Support!*',
    '',
    'I can help you with:',
    '• 📥 Downloading our apps',
    '• ❓ Frequently asked questions',
    '• 📞 Contacting support',
    '• 📊 Checking service status',
    '',
    'Choose an option below:',
  ].join('\n');
}

function helpText(): string {
  return [
    '*How to use this bot:*',
    '',
    '• Use the *buttons* below to navigate',
    '• Tap *Download Apps* to get VlessHub or RootNet VPN',
    '• Tap *FAQ* for common questions',
    '• Tap *Contact Support* to reach our team',
    '',
    '*Quick commands:*',
    '/download — Download apps',
    '/faq — Frequently asked questions',
    '/contact — Contact support',
    '/status — Service status',
    '/menu — Main menu',
  ].join('\n');
}

function downloadText(): string {
  return [
    '📥 *Download Apps*',
    '',
    'Choose which app you want to download:',
  ].join('\n');
}

function vlesshubText(): string {
  return [
    '📱 *VlessHub*',
    '',
    'Free VPN configs and MTProto proxies for Telegram.',
    '',
    '✨ *Features:*',
    '• Auto-scraped VLESS/VMess configs',
    '• MTProto proxy pool',
    '• VPN config files (.npvt, .sip)',
    '• One-tap import to v2rayNG/NekoBox/Hiddify',
    '',
    'Requirements: Android 6.0+',
  ].join('\n');
}

function rootnetText(): string {
  return [
    '🔐 *RootNet VPN*',
    '',
    'Full-featured VPN client with built-in proxy pool.',
    '',
    '✨ *Features:*',
    '• Built-in VPN engine',
    '• MTProto proxy support',
    '• Server browser with ping',
    '• Auto-update',
    '',
    'Requirements: Android 6.0+',
  ].join('\n');
}

function contactText(ctx: BotContext): string {
  return [
    '📞 *Contact Support*',
    '',
    `📧 Email: \`${ctx.contactEmail}\``,
    '💬 Telegram: @rootnet_vpn',
    '🌐 Website: chobgroup.pages.dev',
    '',
    'We typically respond within 24 hours.',
  ].join('\n');
}

function faqIntroText(): string {
  return [
    '❓ *Frequently Asked Questions*',
    '',
    'Choose a topic:',
  ].join('\n');
}

async function checkStatus(ctx: BotContext): Promise<string> {
  const checks: string[] = [];

  // Check Supabase
  try {
    await ctx.supabase.from('servers').select('id').limit(1);
    checks.push('✅ Database: Online');
  } catch {
    checks.push('❌ Database: Offline');
  }

  // Check VlessHub API
  try {
    const res = await fetch('https://vlesshub-api.mobileahmad43-a18.workers.dev/health', {
      signal: AbortSignal.timeout(5000),
    });
    checks.push(res.ok ? '✅ VlessHub API: Online' : '⚠️ VlessHub API: Degraded');
  } catch {
    checks.push('❌ VlessHub API: Offline');
  }

  // Check RootNet VPN API
  try {
    const res = await fetch('https://rootnet-vpn-api.mobileahmad43-a18.workers.dev/health', {
      signal: AbortSignal.timeout(5000),
    });
    checks.push(res.ok ? '✅ RootNet VPN API: Online' : '⚠️ RootNet VPN API: Degraded');
  } catch {
    checks.push('❌ RootNet VPN API: Offline');
  }

  return [
    '📊 *Service Status*',
    '',
    ...checks,
    '',
    '_Last checked: just now_',
  ].join('\n');
}
