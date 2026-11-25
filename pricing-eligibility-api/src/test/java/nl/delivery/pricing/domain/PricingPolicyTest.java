package nl.delivery.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import nl.delivery.pricing.api.EvaluationRequest;
import org.junit.jupiter.api.Test;

class PricingPolicyTest {
    private final PricingPolicy policy = new PricingPolicy();

    @Test
    void appliesPublishedBaseWeightAndUrbanChargesUsingDecimalArithmetic() {
        var result = policy.evaluate(request("1012 AB", "5611EM", 2_750, DeliveryType.NEXT_DAY));

        assertThat(result.eligible()).isTrue();
        assertThat(result.price().amount().toPlainString()).isEqualTo("8.95");
        assertThat(result.priceComponents()).extracting("type")
                .containsExactly("BASE_PRICE", "WEIGHT_SURCHARGE", "URBAN_AREA_SURCHARGE");
        assertThat(result.priceComponents()).extracting("amount")
                .containsExactly(new java.math.BigDecimal("6.95"), new java.math.BigDecimal("1.00"),
                        new java.math.BigDecimal("1.00"));
    }

    @Test
    void appliesWeightBoundaries() {
        int[] grams = {2_000, 2_001, 5_000, 5_001, 10_000, 10_001, 20_000};
        String[] prices = {"4.95", "5.95", "5.95", "7.45", "7.45", "9.95", "9.95"};

        for (int i = 0; i < grams.length; i++) {
            var result = policy.evaluate(request("5611EM", "5611 EM", grams[i], DeliveryType.STANDARD));
            assertThat(result.price().amount().toPlainString()).isEqualTo(prices[i]);
        }
    }

    @Test
    void rejectsWeightAboveLimitAndSameDayAcrossAreas() {
        var tooHeavy = policy.evaluate(request("5611EM", "5611EM", 20_001, DeliveryType.STANDARD));
        var differentAreas = policy.evaluate(request("1012AB", "5611EM", 500, DeliveryType.SAME_DAY));

        assertThat(tooHeavy.rejectionCode()).isEqualTo("WEIGHT_LIMIT_EXCEEDED");
        assertThat(differentAreas.rejectionCode()).isEqualTo("SAME_DAY_ROUTE_UNAVAILABLE");
    }

    @Test
    void permitsSameDayWithinAreaAfterNormalizingPostalCodes() {
        var result = policy.evaluate(request("1012 AB", "1012cd", 500, DeliveryType.SAME_DAY));

        assertThat(result.eligible()).isTrue();
        assertThat(result.price().amount().toPlainString()).isEqualTo("12.95");
    }

    private static EvaluationRequest request(String origin, String destination, int weight, DeliveryType type) {
        return new EvaluationRequest(UUID.randomUUID(), origin, destination, weight, type);
    }
}
