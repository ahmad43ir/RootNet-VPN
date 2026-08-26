// ============================================================
// 📁 _fcm.ts — FCM PUSH NOTIFICATIONS FOR ROOTNET API
// ============================================================
// Handles Firebase Cloud Messaging (FCM) v1 HTTP API push
// notifications. Uses OAuth2 with a Firebase Admin SDK service
// account for authentication.
//
// The same approach as the original Cloudflare Worker:
//   1. Create a JWT assertion signed with the private key (RS256)
//   2. Exchange it for an access token at oauth2.googleapis.com
//   3. Use the access token for FCM v1 API calls
//
// Only difference: uses Deno's Web Crypto (same API as Workers).
// ============================================================

let fcmAccessTokenCache: string | null = null;
let fcmAccessTokenExpiry = 0;

/**
 * Get a cached FCM OAuth2 access token, or generate a fresh one.
 * Uses the Firebase Admin SDK service account JSON (private key + client email).
 */
async function getFCMAccessToken(fcmServiceAccount: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);

  // Return cached token if still valid (use 30min TTL for safety)
  if (fcmAccessTokenCache && now < fcmAccessTokenExpiry - 1800) {
    return fcmAccessTokenCache;
  }

  // Parse the service account JSON
  let sa: Record<string, string>;
  try {
    sa = JSON.parse(fcmServiceAccount);
  } catch (e) {
    throw new Error('Invalid FCM_SERVICE_ACCOUNT: must be valid JSON');
  }

  // ── Step 1: Build the JWT assertion ──
  const header = { alg: 'RS256', typ: 'JWT' };
  const payload = {
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    exp: now + 3600,
    iat: now,
  };

  const base64url = (obj: Record<string, unknown>): string =>
    btoa(JSON.stringify(obj))
      .replace(/=+$/, '')
      .replace(/\+/g, '-')
      .replace(/\//g, '_');

  const message = `${base64url(header)}.${base64url(payload)}`;

  // ── Step 2: Parse PEM private key ──
  const pem = sa.private_key!;
  const pemContents = pem
    .replace('-----BEGIN PRIVATE KEY-----', '')
    .replace('-----END PRIVATE KEY-----', '')
    .replace(/\n/g, '')
    .replace(/\r/g, '');

  const binaryDer = Uint8Array.from(atob(pemContents), (c) => c.charCodeAt(0));

  // ── Step 3: Import private key via Web Crypto ──
  const privateKey = await crypto.subtle.importKey(
    'pkcs8',
    binaryDer.buffer,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  );

  // ── Step 4: Sign the JWT assertion ──
  const signature = await crypto.subtle.sign(
    { name: 'RSASSA-PKCS1-v1_5' },
    privateKey,
    new TextEncoder().encode(message),
  );

  const signatureB64 = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/=+$/, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');

  const jwt = `${message}.${signatureB64}`;

  // ── Step 5: Exchange JWT for access token ──
  const tokenRes = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: jwt,
    }),
  });

  if (!tokenRes.ok) {
    const errText = await tokenRes.text();
    throw new Error(`OAuth2 token exchange failed: ${tokenRes.status} ${errText}`);
  }

  const tokenData = (await tokenRes.json()) as { access_token: string; expires_in: number };

  // Cache the access token
  fcmAccessTokenCache = tokenData.access_token;
  fcmAccessTokenExpiry = now + (tokenData.expires_in || 3600);

  return tokenData.access_token;
}

// ─── Safe JSON parse (no throw) ──────────────────────────────────────────────

function tryParseJSON(str: string): Record<string, unknown> | null {
  try {
    return JSON.parse(str);
  } catch {
    return null;
  }
}

/**
 * Send a push notification to a user's registered devices via FCM v1 API.
 *
 * @param fcmServiceAccount - JSON string of the Firebase Admin SDK service account
 * @param supabase - Supabase client with service_role key
 * @param userId - Supabase user ID to send to
 * @param title - Notification title
 * @param message - Notification body text
 * @param data - Optional custom data payload
 */
export async function sendPushNotification(
  fcmServiceAccount: string,
  supabase: any,
  userId: string,
  title: string,
  message: string,
  data?: Record<string, string>,
): Promise<{ success: boolean; sent: number; failed: number; total: number }> {
  // Fetch all device tokens for this user
  const { data: tokens, error: tokensError } = await supabase
    .from('device_tokens')
    .select('token, platform')
    .eq('user_id', userId);

  if (tokensError) {
    console.error('[fcm] Failed to fetch device tokens:', tokensError.message);
    throw new Error('Failed to fetch device tokens');
  }

  if (!tokens || tokens.length === 0) {
    throw new Error('No registered devices');
  }

  // Get FCM v1 access token
  const accessToken = await getFCMAccessToken(fcmServiceAccount);
  const sa = JSON.parse(fcmServiceAccount);
  const fcmEndpoint = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;

  let sentCount = 0;
  let failCount = 0;

  for (const device of tokens) {
    const fcmPayload = {
      message: {
        token: device.token,
        notification: { title, body: message },
        data: data || {
          type: 'notification',
          title,
          message,
          timestamp: Date.now().toString(),
        },
        android: {
          priority: 'high' as const,
          notification: {
            channel_id: 'push_notifications',
            priority: 'default' as const,
          },
        },
      },
    };

    try {
      const fcmRes = await fetch(fcmEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${accessToken}`,
        },
        body: JSON.stringify(fcmPayload),
      });

      if (fcmRes.ok) {
        sentCount++;
      } else {
        const fcmError = await fcmRes.text();
        console.error(`[fcm] Send failed for ${device.token}: ${fcmError}`);

        // If token is invalid, delete it
        const errJson = tryParseJSON(fcmError);
        const isInvalid =
          fcmError.includes('UNREGISTERED') ||
          fcmError.includes('INVALID_ARGUMENT') ||
          errJson?.error?.details?.some(
            (d: any) => d.reason === 'UNREGISTERED' || d.reason === 'INVALID_ARGUMENT',
          );

        if (isInvalid) {
          await supabase
            .from('device_tokens')
            .delete()
            .eq('token', device.token);
          console.log(`[fcm] Deleted invalid token: ${device.token}`);
        }
        failCount++;
      }
    } catch (fcmErr) {
      console.error('[fcm] Request error:', (fcmErr as Error).message);
      failCount++;
    }
  }

  return { success: true, sent: sentCount, failed: failCount, total: tokens.length };
}
