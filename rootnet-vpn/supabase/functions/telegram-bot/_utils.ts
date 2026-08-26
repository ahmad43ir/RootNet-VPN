// ============================================================
// 📁 _utils.ts — SHARED HELPERS FOR THE TELEGRAM BOT FUNCTION
// ============================================================
// Small, dependency-free utilities. No Cloudflare-specific code —
// this function runs on Supabase Edge (Deno).
// ============================================================

export function requireEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

export function jsonResponse(
  data: Record<string, unknown>,
  status = 200,
  headers: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

export function corsHeaders(): Record<string, string> {
  return {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers':
      'Content-Type, Authorization, X-Admin-Key, X-Telegram-Bot-Api-Secret-Token',
    'Access-Control-Max-Age': '86400',
  };
}

export function corsPreflight(): Response {
  return new Response(null, { status: 204, headers: corsHeaders() });
}

export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// Escape Telegram legacy-Markdown metacharacters in dynamic text so
// user-provided strings (server names, URIs) can never break formatting.
export function escapeMarkdown(text: string): string {
  return String(text).replace(/([_*[\]`])/g, '\\$1');
}

export function log(level: 'info' | 'warn' | 'error', tag: string, message: string): void {
  console[level](`[telegram-bot][${tag}] ${message}`);
}
