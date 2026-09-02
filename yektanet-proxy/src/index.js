// Yektanet CDN proxy — serves yektanet scripts through a clean domain
// so the upstream CDN URL (cdn.yektanet.com) is not exposed to users.

const YEKTANET_CDN = 'https://cdn.yektanet.com';

export default {
  async fetch(request) {
    const url = new URL(request.url);

    // Only allow GET
    if (request.method !== 'GET') {
      return new Response('Method not allowed', { status: 405 });
    }

    // Extract the path after /yektanet/
    const path = url.pathname.replace(/^\/yektanet\/?/, '/');
    if (!path || path === '/') {
      return new Response('Yektanet proxy', { status: 200, headers: { 'Content-Type': 'text/plain' } });
    }

    // Proxy the request to Yektanet CDN
    const target = YEKTANET_CDN + path + (url.search || '');

    try {
      const resp = await fetch(target, {
        headers: {
          'User-Agent': request.headers.get('User-Agent') || '',
          'Referer': url.origin,
        },
      });

      // Copy response headers, override CORS
      const headers = new Headers(resp.headers);
      headers.set('Access-Control-Allow-Origin', '*');
      headers.set('Cache-Control', 'public, max-age=3600');
      headers.delete('X-Frame-Options');

      return new Response(resp.body, {
        status: resp.status,
        headers,
      });
    } catch (e) {
      return new Response('Proxy error: ' + e.message, { status: 502 });
    }
  },
};
