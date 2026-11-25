package nl.delivery.pricing.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EvaluationResponse(
        UUID requestId,
        boolean eligible,
        Price price,
        List<PriceComponent> priceComponents,
        String rejectionCode,
        String message,
        OffsetDateTime evaluatedAt) {

    public record Price(BigDecimal amount, String currency) {}
    public record PriceComponent(String type, BigDecimal amount) {}
}
