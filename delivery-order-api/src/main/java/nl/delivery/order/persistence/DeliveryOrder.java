package nl.delivery.order.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import nl.delivery.order.api.CreateOrderRequest;
import nl.delivery.order.api.PricingEvaluationResponse;

@Entity
@Table(name = "delivery_orders")
public class DeliveryOrder {
    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "customer_reference", nullable = false, length = 100)
    private String customerReference;

    @Column(name = "sender_name", nullable = false, length = 120)
    private String senderName;

    @Column(name = "sender_postal_code", nullable = false, length = 10)
    private String senderPostalCode;

    @Column(name = "sender_country_code", nullable = false, length = 2)
    private String senderCountryCode;

    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    @Column(name = "recipient_postal_code", nullable = false, length = 10)
    private String recipientPostalCode;

    @Column(name = "recipient_country_code", nullable = false, length = 2)
    private String recipientCountryCode;

    @Column(name = "weight_in_grams", nullable = false)
    private int weightInGrams;

    @Column(name = "delivery_type", nullable = false, length = 20)
    private String deliveryType;

    @Column(name = "price_amount", precision = 10, scale = 2)
    private BigDecimal priceAmount;

    @Column(name = "price_currency", length = 3)
    private String priceCurrency;

    @Column(name = "rejection_code", length = 64)
    private String rejectionCode;

    @Column(name = "rejection_message", length = 500)
    private String rejectionMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DeliveryOrder() {}

    public DeliveryOrder(String idempotencyKey, String requestFingerprint,
            CreateOrderRequest request, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.status = OrderStatus.PRICING_PENDING;
        this.customerReference = request.customerReference();
        this.senderName = request.sender().name();
        this.senderPostalCode = normalize(request.sender().postalCode());
        this.senderCountryCode = request.sender().countryCode();
        this.recipientName = request.recipient().name();
        this.recipientPostalCode = normalize(request.recipient().postalCode());
        this.recipientCountryCode = request.recipient().countryCode();
        this.weightInGrams = request.parcel().weightInGrams();
        this.deliveryType = request.deliveryType().name();
        this.createdAt = createdAt;
    }

    public void complete(PricingEvaluationResponse evaluation) {
        if (evaluation.eligible()) {
            this.status = OrderStatus.CONFIRMED;
            this.priceAmount = evaluation.price().amount();
            this.priceCurrency = evaluation.price().currency();
            this.rejectionCode = null;
            this.rejectionMessage = null;
        } else {
            this.status = OrderStatus.REJECTED;
            this.rejectionCode = evaluation.rejectionCode();
            this.rejectionMessage = evaluation.message();
        }
    }

    public void markPricingUnavailable() {
        this.status = OrderStatus.PRICING_UNAVAILABLE;
        this.rejectionCode = "PRICING_UNAVAILABLE";
        this.rejectionMessage = "The pricing service could not evaluate this order.";
    }

    private static String normalize(String postalCode) {
        return postalCode.replaceAll("\\s", "").toUpperCase(java.util.Locale.ROOT);
    }

    public UUID getId() { return id; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String getCustomerReference() { return customerReference; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getPriceAmount() { return priceAmount; }
    public String getPriceCurrency() { return priceCurrency; }
    public String getRejectionCode() { return rejectionCode; }
    public String getRejectionMessage() { return rejectionMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
