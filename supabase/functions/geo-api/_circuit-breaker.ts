// ============================================================
// 📁 _circuit-breaker.ts — CIRCUIT BREAKER
// ============================================================
// Prevents cascading failures by temporarily disabling the
// Supabase provider when it fails repeatedly.
//
// States:
//   CLOSED  — Normal operation, requests flow to Supabase
//   OPEN    — Supabase disabled, requests skip directly to fallback
//
// Transition:
//   CLOSED → OPEN  : after `failureThreshold` consecutive failures
//   OPEN   → CLOSED: after `cooldownMs` milliseconds of cooldown
// ============================================================

import { log } from './_utils.ts';
import type { CircuitBreakerConfig } from './_types.ts';

// ─── Default Configuration ────────────────────────────────────────────────────

const DEFAULT_CONFIG: CircuitBreakerConfig = {
  failureThreshold: 5,   // 5 consecutive failures → open
  cooldownMs: 60_000,    // 60 seconds cooldown
};

// ─── State ────────────────────────────────────────────────────────────────────

interface InternalState {
  consecutiveFailures: number;
  openedAt: number | null; // timestamp when circuit opened, null if closed
  config: CircuitBreakerConfig;
}

let state: InternalState = {
  consecutiveFailures: 0,
  openedAt: null,
  config: DEFAULT_CONFIG,
};

// ─── API ──────────────────────────────────────────────────────────────────────

/**
 * Register a successful Supabase call.
 * Resets the failure count and keeps the circuit closed.
 */
export function recordSuccess(): void {
  const wasOpen = state.openedAt !== null;
  state.consecutiveFailures = 0;
  state.openedAt = null;
  if (wasOpen) {
    log('info', 'circuit-breaker', 'Circuit CLOSED — Supabase recovered');
  }
}

/**
 * Register a failed Supabase call.
 * May trigger the circuit to open if threshold is reached.
 */
export function recordFailure(): void {
  state.consecutiveFailures++;

  if (state.consecutiveFailures === state.config.failureThreshold) {
    // Just crossed the threshold — OPEN the circuit NOW
    state.openedAt = Date.now();
    log('warn', 'circuit-breaker',
      `Circuit OPENED — ${state.consecutiveFailures} consecutive failures. ` +
      `Supabase disabled for ${state.config.cooldownMs / 1000}s`);
  } else if (state.consecutiveFailures > state.config.failureThreshold && state.openedAt !== null) {
    // Circuit already open — do NOT extend the cooldown.
    // Only log if this is a noteworthy milestone.
    if (state.consecutiveFailures % 5 === 0) {
      log('warn', 'circuit-breaker',
        `Circuit still OPEN — ${state.consecutiveFailures} consecutive failures so far`);
    }
  }
}

/**
 * Check whether the circuit allows Supabase calls.
 * Auto-closes after cooldown expires.
 */
export function isCircuitOpen(): boolean {
  if (state.openedAt === null) return false; // CLOSED

  const elapsed = Date.now() - state.openedAt;
  if (elapsed >= state.config.cooldownMs) {
    // Cooldown expired — try Supabase again (half-open)
    state.consecutiveFailures = 0;
    state.openedAt = null;
    log('info', 'circuit-breaker', `Circuit CLOSED (cooldown expired after ${elapsed}ms)`);
    return false;
  }

  return true; // Still OPEN
}

/**
 * Get the current circuit breaker state (for diagnostics).
 */
export function getCircuitState(): { isOpen: boolean; remainingCooldownMs: number; consecutiveFailures: number } {
  if (state.openedAt === null) {
    return { isOpen: false, remainingCooldownMs: 0, consecutiveFailures: state.consecutiveFailures };
  }
  const remaining = Math.max(0, state.config.cooldownMs - (Date.now() - state.openedAt));
  return { isOpen: true, remainingCooldownMs: remaining, consecutiveFailures: state.consecutiveFailures };
}

/**
 * Reset the circuit breaker (for testing).
 */
export function resetCircuitBreaker(): void {
  state = {
    consecutiveFailures: 0,
    openedAt: null,
    config: DEFAULT_CONFIG,
  };
}
