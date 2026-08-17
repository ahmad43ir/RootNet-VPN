// ============================================================
// 📁 _rate-limit.ts — RATE LIMITING (SELF-CONTAINED)
// ============================================================
// Minimal rate limiting using the existing check_rate_limit() RPC.
// Self-contained — no dependency on rootnet-api module.
// ============================================================

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
      return { allowed: true, remaining: maxRequests };
    }

    return {
      allowed: data?.allowed ?? true,
      remaining: data?.remaining ?? maxRequests,
    };
  } catch (err) {
    console.error('[rate-limit] Unexpected error:', err);
    return { allowed: true, remaining: maxRequests };
  }
}
