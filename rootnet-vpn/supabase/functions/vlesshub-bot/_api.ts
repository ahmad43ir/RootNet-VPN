// ============================================================
// 📁 _api.ts — VlessHub Cloudflare data-plane client
// ============================================================
// Thin HTTP wrapper around the `vlesshub-api` Worker (D1).
// Replaces the old direct-Supabase calls so VlessHub's data lives
// fully separated from RootNet.
// ============================================================

export function apiBase(): string {
  return (Deno.env.get('VLESSHUB_API_URL') ?? '').replace(/\/+$/, '');
}

function headers(): Record<string, string> {
  return { 'x-admin-key': Deno.env.get('VLESSHUB_API_KEY') ?? '', 'content-type': 'application/json' };
}

export async function apiGet<T = any>(path: string): Promise<T | null> {
  try {
    const res = await fetch(apiBase() + path, { headers: headers() });
    if (!res.ok) {
      console.warn('[api] GET', path, '→', res.status);
      return null;
    }
    return await res.json();
  } catch (e) {
    console.warn('[api] GET', path, 'threw:', (e as Error).message);
    return null;
  }
}

export async function apiPost<T = any>(path: string, body: any): Promise<T | null> {
  try {
    const res = await fetch(apiBase() + path, {
      method: 'POST',
      headers: headers(),
      body: JSON.stringify(body ?? {}),
    });
    if (!res.ok) {
      console.warn('[api] POST', path, '→', res.status);
      return null;
    }
    return await res.json();
  } catch (e) {
    console.warn('[api] POST', path, 'threw:', (e as Error).message);
    return null;
  }
}

export async function apiPut<T = any>(path: string, body: any): Promise<T | null> {
  try {
    const res = await fetch(apiBase() + path, {
      method: 'PUT',
      headers: headers(),
      body: JSON.stringify(body ?? {}),
    });
    if (!res.ok) {
      console.warn('[api] PUT', path, '→', res.status);
      return null;
    }
    return await res.json();
  } catch (e) {
    console.warn('[api] PUT', path, 'threw:', (e as Error).message);
    return null;
  }
}

export async function apiDelete<T = any>(path: string): Promise<T | null> {
  try {
    const res = await fetch(apiBase() + path, { method: 'DELETE', headers: headers() });
    if (!res.ok) {
      console.warn('[api] DELETE', path, '→', res.status);
      return null;
    }
    return await res.json();
  } catch (e) {
    console.warn('[api] DELETE', path, 'threw:', (e as Error).message);
    return null;
  }
}
