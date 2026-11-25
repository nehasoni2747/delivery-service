# ADR 002: Explicit unavailable status, bounded HTTP call

**Status:** Accepted

Use connect and response timeouts and no automatic retries. Persist `PRICING_UNAVAILABLE` for dependency errors or invalid responses and never report a false confirmation. The explicit status preserves the attempted order and gives callers a stable result. A recovery/re-evaluation policy is deliberately left as a production follow-up instead of silently retrying or changing historical pricing.
