package nl.delivery.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import nl.delivery.order.api.CreateOrderRequest;
import org.springframework.stereotype.Component;

@Component
public class RequestFingerprint {
    private final ObjectMapper objectMapper;

    public RequestFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String of(CreateOrderRequest request) {
        var content = new CanonicalRequest(
                request.customerReference(),
                request.sender().name(),
                normalize(request.sender().postalCode()),
                request.sender().countryCode(),
                request.recipient().name(),
                normalize(request.recipient().postalCode()),
                request.recipient().countryCode(),
                request.parcel().weightInGrams(),
                request.deliveryType().name());
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(content);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to fingerprint order request", exception);
        }
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    private record CanonicalRequest(String customerReference, String senderName, String senderPostalCode,
            String senderCountryCode, String recipientName, String recipientPostalCode,
            String recipientCountryCode, Integer weightInGrams, String deliveryType) {}
}
