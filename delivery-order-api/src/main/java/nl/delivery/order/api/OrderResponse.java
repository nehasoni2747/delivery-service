package nl.delivery.order.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        String customerReference,
        String status,
        Price price,
        String rejectionCode,
        String message,
        OffsetDateTime createdAt) {

    public record Price(BigDecimal amount, String currency) {}
}
