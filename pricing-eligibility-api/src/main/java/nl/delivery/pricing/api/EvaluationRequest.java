package nl.delivery.pricing.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import nl.delivery.pricing.domain.DeliveryType;

public record EvaluationRequest(
        @NotNull UUID requestId,
        @NotBlank @Size(max = 10) @Pattern(regexp = "\\d{4}\\s?[A-Za-z]{2}") String originPostalCode,
        @NotBlank @Size(max = 10) @Pattern(regexp = "\\d{4}\\s?[A-Za-z]{2}") String destinationPostalCode,
        @NotNull @Positive Integer weightInGrams,
        @NotNull DeliveryType deliveryType) {
}
