// ============================================================
// 📁 _telegram.ts — MINIMAL TELEGRAM BOT API CLIENT
// ============================================================
// Plain fetch-based client — no bot-framework dependency. The Python
// bot ran a long-poll loop; an edge function cannot, so this bot runs
// in WEBHOOK mode: Telegram POSTs updates to the function and we
// answer via the Bot API send*/edit* methods.
// ============================================================

const TELEGRAM_API = 'https://api.telegram.org';

function apiUrl(token: string, method: string): string {
  return `${TELEGRAM_API}/bot${token}/${method}`;
}

async function callMethod<T>(
  token: string,
  method: string,
  body: Record<string, unknown> = {},
): Promise<T | null> {
  try {
    const res = await fetch(apiUrl(token, method), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(30_000),
    });
    if (!res.ok) {
      console.warn(`[telegram] ${method} HTTP ${res.status}: ${await res.text()}`);
      return null;
    }
    const data = await res.json();
    if (!data.ok) {
      console.warn(`[telegram] ${method} api error: ${JSON.stringify(data.description ?? data)}`);
      return null;
    }
    return data.result as T;
  } catch (e) {
    console.warn(`[telegram] ${method} failed: ${(e as Error).message}`);
    return null;
  }
}

export interface TgMessage {
  message_id: number;
  [key: string]: unknown;
}

export interface TgSendOptions {
  parse_mode?: string;
  reply_markup?: unknown;
  disable_web_page_preview?: boolean;
}

export async function sendMessage(
  token: string,
  chatId: number,
  text: string,
  opts: TgSendOptions = {},
): Promise<TgMessage | null> {
  return callMethod<TgMessage>(token, 'sendMessage', { chat_id: chatId, text, ...opts });
}

export async function editMessageText(
  token: string,
  chatId: number,
  messageId: number,
  text: string,
  opts: TgSendOptions = {},
): Promise<TgMessage | null> {
  return callMethod<TgMessage>(token, 'editMessageText', {
    chat_id: chatId,
    message_id: messageId,
    text,
    ...opts,
  });
}

export async function answerCallbackQuery(
  token: string,
  queryId: string,
  text?: string,
): Promise<void> {
  await callMethod(token, 'answerCallbackQuery', {
    callback_query_id: queryId,
    ...(text ? { text } : {}),
  });
}

/** Delete one of the bot's own messages. Bots can only delete their own
 *  messages, and only ones younger than 48 hours — failures (older/already
 *  deleted) are logged and swallowed. */
export async function deleteMessage(
  token: string,
  chatId: number,
  messageId: number,
): Promise<void> {
  await callMethod(token, 'deleteMessage', { chat_id: chatId, message_id: messageId });
}

interface TgFile {
  file_path?: string;
}

export async function getFile(token: string, fileId: string): Promise<string | null> {
  const result = await callMethod<TgFile>(token, 'getFile', { file_id: fileId });
  return result?.file_path ?? null;
}

export async function downloadFileText(token: string, filePath: string): Promise<string> {
  const res = await fetch(`${TELEGRAM_API}/file/bot${token}/${filePath}`, {
    signal: AbortSignal.timeout(30_000),
  });
  if (!res.ok) throw new Error(`download ${filePath} -> HTTP ${res.status}`);
  return res.text();
}

/** Download a Telegram file as raw bytes (for storing in vpn_files). */
export async function downloadFileBytes(token: string, filePath: string): Promise<Uint8Array> {
  const res = await fetch(`${TELEGRAM_API}/file/bot${token}/${filePath}`, {
    signal: AbortSignal.timeout(30_000),
  });
  if (!res.ok) throw new Error(`download ${filePath} -> HTTP ${res.status}`);
  return new Uint8Array(await res.arrayBuffer());
}

export async function sendDocument(
  token: string,
  chatId: number,
  filename: string,
  content: string,
  mimeType?: string | null,
): Promise<TgMessage | null> {
  // Use multipart/form-data for file upload
  const boundary = `----BotBoundary${Date.now()}`;
  const bodyParts: string[] = [];
  
  bodyParts.push(`--${boundary}`);
  bodyParts.push('Content-Disposition: form-data; name="chat_id"');
  bodyParts.push('');
  bodyParts.push(String(chatId));
  
  bodyParts.push(`--${boundary}`);
  bodyParts.push(`Content-Disposition: form-data; name="document"; filename="${filename}"`);
  if (mimeType) {
    bodyParts.push(`Content-Type: ${mimeType}`);
  }
  bodyParts.push('');
  bodyParts.push(content);
  
  bodyParts.push(`--${boundary}--`);
  bodyParts.push('');
  
  const body = bodyParts.join('\r\n');
  
  try {
    const res = await fetch(apiUrl(token, 'sendDocument'), {
      method: 'POST',
      headers: {
        'Content-Type': `multipart/form-data; boundary=${boundary}`,
        'Content-Length': String(new TextEncoder().encode(body).length),
      },
      body,
      signal: AbortSignal.timeout(60_000),
    });
    if (!res.ok) {
      console.warn(`[telegram] sendDocument HTTP ${res.status}: ${await res.text()}`);
      return null;
    }
    const data = await res.json();
    if (!data.ok) {
      console.warn(`[telegram] sendDocument api error: ${JSON.stringify(data.description ?? data)}`);
      return null;
    }
    return data.result as TgMessage;
  } catch (e) {
    console.warn(`[telegram] sendDocument failed: ${(e as Error).message}`);
    return null;
  }
}

export async function setWebhook(
  token: string,
  url: string,
  secretToken: string,
): Promise<TgMessage | null> {
  return callMethod<TgMessage>(token, 'setWebhook', {
    url,
    secret_token: secretToken,
    allowed_updates: ['message', 'callback_query'],
    drop_pending_updates: true,
  });
}

export async function deleteWebhook(token: string): Promise<TgMessage | null> {
  return callMethod<TgMessage>(token, 'deleteWebhook', { drop_pending_updates: true });
}

export async function getWebhookInfo(token: string): Promise<Record<string, unknown> | null> {
  return callMethod<Record<string, unknown>>(token, 'getWebhookInfo');
}

export interface TgBotCommand {
  command: string;
  description: string;
}

export async function setMyCommands(token: string, commands: TgBotCommand[]): Promise<boolean> {
  const result = await callMethod<unknown>(token, 'setMyCommands', { commands });
  return result !== null;
}
