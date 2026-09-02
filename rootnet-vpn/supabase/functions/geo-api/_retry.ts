// ============================================================
// 📁 _retry.ts — RETRY WITH EXPONENTIAL BACKOFF
// ============================================================
// Wraps an async operation with retry logic.
// Uses exponential backoff: 300ms → 600ms (base * 2^attempt)
// ============================================================

import { log } from './_utils.ts';
import type { RetryConfig } from './_types.ts';

// ─── Default Configuration ────────────────────────────────────────────────────

const DEFAULT_CONFIG: RetryConfig = {
  maxRetries: 2,
  baseDelayMs: 300,
};

// ─── Error Classification ─────────────────────────────────────────────────────

/**
 * Determines if an error is retryable.
 * Timeout, network errors, 5xx, and 429 are retryable.
 * 4xx errors (except 429) are NOT retryable.
 */
function isRetryable(error: Error, status?: number): boolean {
  if (status !== undefined) {
    // 429 (rate limit) → retryable
    if (status === 429) return true;
    // 5xx → retryable
    if (status >= 500 && status < 600) return true;
    // 4xx (except 429) → NOT retryable
    if (status >= 400 && status < 500) return false;
  }

  // Network / timeout errors → retryable
  const msg = error.message.toLowerCase();
  if (msg.includes('timeout') || msg.includes('timed out')) return true;
  if (msg.includes('econnrefused') || msg.includes('connection refused')) return true;
  if (msg.includes('econnreset') || msg.includes('connection reset')) return true;
  if (msg.includes('network') || msg.includes('dns')) return true;
  if (msg.includes('fetch failed')) return true;

  return false;
}

// ─── Retry Wrapper ────────────────────────────────────────────────────────────

export interface RetryableOperation<T> {
  (): Promise<{ data: T | null; status?: number }>;
}

/**
 * Execute an async operation with retry logic.
 *
 * @param operation - The operation to execute
 * @param operationName - Human-readable name for logging
 * @param config - Retry configuration
 * @returns The result of the operation, or null if all retries failed
 */
export async function withRetry<T>(
  operation: RetryableOperation<T>,
  operationName: string,
  config: RetryConfig = DEFAULT_CONFIG,
): Promise<{ data: T | null; status?: number }> {
  let lastError: Error | null = null;
  let lastStatus: number | undefined;

  for (let attempt = 0; attempt <= config.maxRetries; attempt++) {
    try {
      const result = await operation();

      // If the operation returned data, treat as success regardless of status
      if (result.data !== null) {
        if (attempt > 0) {
          log('info', 'retry', `${operationName} succeeded on attempt ${attempt + 1}`);
        }
        return result;
      }

      // No data — check if status is retryable
      lastStatus = result.status;
      if (result.status !== undefined && !isRetryable(new Error(`Status ${result.status}`), result.status)) {
        // Non-retryable status (e.g. 400, 404)
        return result;
      }

      // Retryable failure — prepare for retry
      lastError = new Error(`Status: ${result.status}`);
    } catch (err) {
      lastError = err instanceof Error ? err : new Error(String(err));
      if (!isRetryable(lastError)) {
        // Non-retryable error — give up immediately
        log('warn', 'retry', `${operationName} failed with non-retryable error: ${lastError.message}`);
        return { data: null, status: lastStatus };
      }
    }

    // If this was the last attempt, log and give up
    if (attempt >= config.maxRetries) {
      log('error', 'retry',
        `${operationName} failed after ${config.maxRetries + 1} attempts: ${lastError?.message || 'unknown'}`);
      return { data: null, status: lastStatus };
    }

    // Wait with exponential backoff before retrying
    const delay = config.baseDelayMs * Math.pow(2, attempt);
    log('warn', 'retry',
      `${operationName} attempt ${attempt + 1} failed, retrying in ${delay}ms`);

    await new Promise((resolve) => setTimeout(resolve, delay));
  }

  return { data: null, status: lastStatus };
}
