package nl.delivery.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import nl.delivery.order.domain.DeliveryType;

public record CreateOrderRequest(
        @NotBlank @Size(max = 100) String customerReference,
        @NotNull @Valid Party sender,
        @NotNull @Valid Party recipient,
        @NotNull @Valid Parcel parcel,
        @NotNull DeliveryType deliveryType) {

    public record Party(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 10) @Pattern(regexp = "\\d{4}\\s?[A-Za-z]{2}") String postalCode,
            @NotBlank @Pattern(regexp = "NL") String countryCode) {}

    public record Parcel(@NotNull @Positive Integer weightInGrams) {}
}
