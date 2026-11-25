# ADR 003: Interpret base-price table as authoritative

**Status:** Accepted

Calculate price as the published delivery-type base price plus the weight tier and, once per route, the urban surcharge when either postal code is in 1000AA–1099ZZ. The assignment's sample component breakdown does not agree with its base-price table; use the table, make every component explicit, and document the choice. Use `BigDecimal` throughout and store confirmed amounts at scale two.
