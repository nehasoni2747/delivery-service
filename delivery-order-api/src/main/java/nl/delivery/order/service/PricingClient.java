package nl.delivery.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import nl.delivery.order.api.CreateOrderRequest;
import nl.delivery.order.api.PricingEvaluationRequest;
import nl.delivery.order.api.PricingEvaluationResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PricingClient {
    private final RestClient restClient;

    public PricingClient(RestClient pricingRestClient) {
        this.restClient = pricingRestClient;
    }

    public PricingEvaluationResponse evaluate(CreateOrderRequest request) {
        UUID requestId = UUID.randomUUID();
        var evaluationRequest = new PricingEvaluationRequest(requestId,
                normalize(request.sender().postalCode()),
                normalize(request.recipient().postalCode()),
                request.parcel().weightInGrams(),
                request.deliveryType().name());
        var call = restClient.post().uri("/api/v1/pricing/evaluations");
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            call.header("X-Correlation-Id", correlationId);
        }
        var response = call.body(evaluationRequest).retrieve().body(PricingEvaluationResponse.class);
        validate(response, requestId);
        return response;
    }

    private static void validate(PricingEvaluationResponse response, UUID requestId) {
        if (response == null || !requestId.equals(response.requestId()) || response.evaluatedAt() == null) {
            throw new InvalidPricingResponseException();
        }
        if (!response.eligible()) {
            if (isBlank(response.rejectionCode()) || isBlank(response.message())
                    || response.price() != null || response.priceComponents() == null) {
                throw new InvalidPricingResponseException();
            }
            return;
        }
        if (response.price() == null || !"EUR".equals(response.price().currency())
                || response.price().amount() == null || response.priceComponents() == null
                || response.priceComponents().isEmpty()) {
            throw new InvalidPricingResponseException();
        }
        try {
            BigDecimal amount = response.price().amount().setScale(2, RoundingMode.UNNECESSARY);
            if (response.priceComponents().stream().anyMatch(component ->
                    component == null || component.amount() == null || component.amount().signum() < 0)) {
                throw new InvalidPricingResponseException();
            }
            BigDecimal componentTotal = response.priceComponents().stream()
                    .map(PricingEvaluationResponse.PriceComponent::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (amount.signum() < 0 || amount.compareTo(componentTotal) != 0) {
                throw new InvalidPricingResponseException();
            }
        } catch (ArithmeticException exception) {
            throw new InvalidPricingResponseException();
        }
    }

    private static String normalize(String postalCode) {
        return postalCode.replaceAll("\\s", "").toUpperCase(java.util.Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
