package nl.delivery.order.api;

import java.util.UUID;

public record PricingEvaluationRequest(
        UUID requestId,
        String originPostalCode,
        String destinationPostalCode,
        Integer weightInGrams,
        String deliveryType) {}
