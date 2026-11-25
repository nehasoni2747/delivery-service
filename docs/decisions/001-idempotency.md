# ADR 001: Database-backed idempotency

**Status:** Accepted

Persist each idempotency key under a database unique constraint, together with a SHA-256 fingerprint of the normalized request. Commit a `PRICING_PENDING` order before network I/O; concurrent API instances converge on that row. Same-key/same-content replays return the resource, and same-key/different-content requests conflict. This avoids process-local locks and avoids holding a database transaction during an upstream request. A crash can leave a pending order, so production needs monitoring and recovery.
