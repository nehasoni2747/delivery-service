package nl.delivery.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import nl.delivery.pricing.api.EvaluationRequest;
import nl.delivery.pricing.api.EvaluationResponse;
import org.springframework.stereotype.Component;

@Component
public class PricingPolicy {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal URBAN_SURCHARGE = new BigDecimal("1.00");
    private final Clock clock;

    public PricingPolicy() {
        this(Clock.systemUTC());
    }

    PricingPolicy(Clock clock) {
        this.clock = clock;
    }

    public EvaluationResponse evaluate(EvaluationRequest request) {
        var evaluatedAt = OffsetDateTime.now(clock);
        var origin = normalize(request.originPostalCode());
        var destination = normalize(request.destinationPostalCode());

        if (request.weightInGrams() > 20_000) {
            return rejected(request, evaluatedAt, "WEIGHT_LIMIT_EXCEEDED",
                    "The maximum supported parcel weight is 20000 grams.");
        }
        if (request.deliveryType() == DeliveryType.SAME_DAY
                && !origin.substring(0, 4).equals(destination.substring(0, 4))) {
            return rejected(request, evaluatedAt, "SAME_DAY_ROUTE_UNAVAILABLE",
                    "Same-day delivery requires origin and destination in the same postal-code area.");
        }

        var components = new java.util.ArrayList<EvaluationResponse.PriceComponent>();
        var total = basePrice(request.deliveryType());
        components.add(component("BASE_PRICE", total));

        var weightSurcharge = weightSurcharge(request.weightInGrams());
        components.add(component("WEIGHT_SURCHARGE", weightSurcharge));
        total = total.add(weightSurcharge);

        if (isUrban(origin) || isUrban(destination)) {
            components.add(component("URBAN_AREA_SURCHARGE", URBAN_SURCHARGE));
            total = total.add(URBAN_SURCHARGE);
        }

        var price = new EvaluationResponse.Price(total.setScale(2, RoundingMode.UNNECESSARY), "EUR");
        return new EvaluationResponse(request.requestId(), true, price, List.copyOf(components),
                null, null, evaluatedAt);
    }

    static String normalize(String postalCode) {
        return postalCode.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    private static BigDecimal basePrice(DeliveryType type) {
        return switch (type) {
            case STANDARD -> new BigDecimal("4.95");
            case NEXT_DAY -> new BigDecimal("6.95");
            case SAME_DAY -> new BigDecimal("11.95");
        };
    }

    private static BigDecimal weightSurcharge(int grams) {
        if (grams <= 2_000) return ZERO;
        if (grams <= 5_000) return new BigDecimal("1.00");
        if (grams <= 10_000) return new BigDecimal("2.50");
        return new BigDecimal("5.00");
    }

    private static boolean isUrban(String normalizedPostalCode) {
        int digits = Integer.parseInt(normalizedPostalCode.substring(0, 4));
        return digits >= 1_000 && digits <= 1_099;
    }

    private static EvaluationResponse.PriceComponent component(String type, BigDecimal amount) {
        return new EvaluationResponse.PriceComponent(type, amount);
    }

    private static EvaluationResponse rejected(EvaluationRequest request, OffsetDateTime at,
            String code, String message) {
        return new EvaluationResponse(request.requestId(), false, null, List.of(), code, message, at);
    }
}
