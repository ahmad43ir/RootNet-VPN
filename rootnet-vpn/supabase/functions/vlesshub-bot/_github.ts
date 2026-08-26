// ============================================================
// 📁 _github.ts — MINIMAL GITHUB REST CLIENT (WORKFLOW DISPATCH)
// ============================================================
// Lets the bot start the vless-scraper GitHub Actions workflow
// on demand (/scrape) instead of relying on a schedule. Uses a
// fine-grained PAT with "Actions: read & write" on the repo
// (stored as the GH_PAT function secret).
// ============================================================

const GH_API = 'https://api.github.com';

export interface DispatchResult {
  ok: boolean;
  status?: number;
  body?: string;
}

export async function dispatchWorkflow(opts: {
  pat: string;
  repo: string; // "owner/repo"
  workflowFile: string; // e.g. "scrape.yml"
  ref: string; // branch name or SHA
  inputs?: Record<string, string>;
}): Promise<DispatchResult> {
  const url = `${GH_API}/repos/${opts.repo}/actions/workflows/${opts.workflowFile}/dispatches`;
  const body: Record<string, unknown> = { ref: opts.ref };
  if (opts.inputs && Object.keys(opts.inputs).length > 0) {
    body.inputs = opts.inputs;
  }

  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${opts.pat}`,
        Accept: 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        'Content-Type': 'application/json',
        'User-Agent': 'rootnet-telegram-bot',
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(15_000),
    });
    // GitHub returns 204 No Content on a successful dispatch.
    if (res.status === 204) return { ok: true, status: res.status };
    const text = await res.text();
    return { ok: false, status: res.status, body: text };
  } catch (e) {
    return { ok: false, body: (e as Error).message };
  }
}
