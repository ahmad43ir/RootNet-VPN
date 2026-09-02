/**
 * rootnet-proxy — reverse proxy for chobgroup.pages.dev
 *
 * Serves the Chob Group / RootNet landing pages (including the APK download
 * at /downloads/*) through a workers.dev domain, so users in regions where
 * pages.dev is blocked (e.g. Iran) can still reach the site.
 *
 * How it works:
 *   - Every request is forwarded to https://chobgroup.pages.dev with the same
 *     path and query string.
 *   - Redirects are followed server-side (on Cloudflare's network, not the
 *     client's), so Pages clean-URL redirects (e.g. /privacy.html -> /privacy)
 *     never send the client to pages.dev directly.
 *   - The site uses relative internal links, so no HTML rewriting is needed.
 *
 * Deploy:  npx wrangler deploy   (from rootnet-proxy/)
 * URL:     https://rootnet-proxy.mobileahmad43-a18.workers.dev
 */
const ORIGIN = "https://chobgroup.pages.dev";

export default {
  async fetch(request) {
    const url = new URL(request.url);

    // Fixed origin only — this is a single-site reverse proxy, not an open one.
    const target = ORIGIN + url.pathname + url.search;

    // Forward the client's method/headers/body, minus hop-by-hop headers.
    // Host is intentionally not set: Workers derive it from the target URL.
    const headers = new Headers(request.headers);
    for (const h of [
      "host",
      "connection",
      "keep-alive",
      "transfer-encoding",
      "upgrade",
      "proxy-authorization",
      "proxy-connection",
      "te",
    ]) {
      headers.delete(h);
    }

    const init = {
      method: request.method,
      headers,
      redirect: "follow", // resolve pages.dev redirects server-side
    };
    if (request.method !== "GET" && request.method !== "HEAD") {
      init.body = request.body;
    }

    try {
      return await fetch(target, init);
    } catch (err) {
      return new Response(
        `502 Bad Gateway — origin unreachable: ${err.message}`,
        {
          status: 502,
          headers: { "content-type": "text/plain; charset=utf-8" },
        },
      );
    }
  },
};
