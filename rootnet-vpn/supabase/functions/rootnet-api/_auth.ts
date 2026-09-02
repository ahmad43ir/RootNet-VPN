// ============================================================
// 📁 _auth.ts — JWT AUTHENTICATION FOR ROOTNET API
// ============================================================
// Replaces the manual JWKS + Web Crypto verification in the old
// Cloudflare Worker with Supabase's built-in auth.getUser().
//
// WHY THIS IS BETTER:
//   - No manual JWKS fetching/caching needed
//   - Checks token revocation (if user deleted, auth fails)
//   - Returns the full user object
//   - Supabase handles token refresh automatically
// ============================================================

import { createClient } from 'jsr:@supabase/supabase-js@2';

export interface AuthenticatedUser {
  id: string;
  email: string | undefined;
  appMetadata: Record<string, unknown>;
}

/**
 * Extract and verify a JWT from the Authorization header.
 *
 * @returns AuthenticatedUser on success, null on failure
 */
export async function authenticate(
  req: Request,
  supabaseUrl: string,
  supabaseKey: string,
): Promise<AuthenticatedUser | null> {
  const authHeader = req.headers.get('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return null;
  }

  const token = authHeader.slice(7);
  if (!token) return null;

  try {
    // Create a Supabase client with the service_role key.
    // We use getUser(token) which validates the JWT server-side.
    const supabase = createClient(supabaseUrl, supabaseKey);
    const { data, error } = await supabase.auth.getUser(token);

    if (error || !data.user) {
      console.error('[auth] getUser failed:', error?.message || 'No user');
      return null;
    }

    return {
      id: data.user.id,
      email: data.user.email,
      appMetadata: data.user.app_metadata || {},
    };
  } catch (err) {
    console.error('[auth] Unexpected error:', err);
    return null;
  }
}

