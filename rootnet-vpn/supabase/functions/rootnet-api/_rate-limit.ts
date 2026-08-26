// ============================================================
// 📁 _rate-limit.ts — RATE LIMITING FOR ROOTNET API
// ============================================================
// Replaces the Cloudflare Worker's in-memory Map-based rate
// limiting with Postgres-backed atomic rate limiting.
//
// Uses the check_rate_limit() RPC that was created in the
// 20260727000001_create_rate_limits_table.sql migration.
// ============================================================

/**
 * Check if an IP address is rate limited.
 * Calls the Postgres RPC check_rate_limit() for atomic check-and-increment.
 *
 * @param supabase - Initialized Supabase client (service_role)
 * @param identifier - IP address or `user:{userId}` for user rate limiting
 * @param maxRequests - Max requests allowed in the window (default: 60)
 * @param windowMinutes - Window duration in minutes (default: 1)
 * @returns { allowed: boolean, remaining: number }
 */
export async function checkIpRateLimit(
  supabase: any,
  identifier: string,
  maxRequests = 60,
  windowMinutes = 1,
): Promise<{ allowed: boolean; remaining: number }> {
  try {
    const { data, error } = await supabase.rpc('check_rate_limit', {
      p_ip_address: identifier,
      p_max_requests: maxRequests,
      p_window_minutes: windowMinutes,
    });

    if (error) {
      console.error('[rate-limit] RPC failed:', error.message);
      return { allowed: true, remaining: maxRequests }; // Fail open
    }

    return {
      allowed: data?.allowed ?? true,
      remaining: data?.remaining ?? maxRequests,
    };
  } catch (err) {
    console.error('[rate-limit] Unexpected error:', err);
    return { allowed: true, remaining: maxRequests }; // Fail open
  }
}
