package nl.delivery.order.service;

import java.net.URI;
import java.time.ZoneId;
import java.util.UUID;
import nl.delivery.order.api.CreateOrderRequest;
import nl.delivery.order.api.OrderResponse;
import nl.delivery.order.persistence.DeliveryOrder;
import nl.delivery.order.persistence.DeliveryOrderRepository;
import nl.delivery.order.persistence.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class OrderApplicationService {
    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);
    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    private final DeliveryOrderRepository repository;
    private final OrderClaimService claimService;
    private final OrderUpdateService updateService;
    private final RequestFingerprint fingerprint;
    private final PricingClient pricingClient;

    public OrderApplicationService(DeliveryOrderRepository repository, OrderClaimService claimService,
            OrderUpdateService updateService, RequestFingerprint fingerprint, PricingClient pricingClient) {
        this.repository = repository;
        this.claimService = claimService;
        this.updateService = updateService;
        this.fingerprint = fingerprint;
        this.pricingClient = pricingClient;
    }

    public CreationResult create(String idempotencyKey, CreateOrderRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new InvalidIdempotencyKeyException();
        }
        String contentFingerprint = fingerprint.of(request);
        OrderClaimService.Claim claim;
        try {
            claim = claimService.claim(idempotencyKey, contentFingerprint, request);
        } catch (DataIntegrityViolationException race) {
            DeliveryOrder existing = repository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> race);
            ensureSamePayload(existing, contentFingerprint);
            log.atInfo().addKeyValue("event", "idempotent_order_replay")
                    .addKeyValue("orderId", existing.getId())
                    .addKeyValue("status", existing.getStatus())
                    .log("Returned idempotent order");
            return new CreationResult(toResponse(existing), false);
        }

        DeliveryOrder order = claim.order();
        if (!claim.created()) {
            log.atInfo().addKeyValue("event", "idempotent_order_replay")
                    .addKeyValue("orderId", order.getId())
                    .addKeyValue("status", order.getStatus())
                    .log("Returned idempotent order");
            return new CreationResult(toResponse(order), false);
        }

        try {
            var evaluation = pricingClient.evaluate(request);
            order = updateService.complete(order.getId(), evaluation);
            log.atInfo().addKeyValue("event", "order_evaluated")
                    .addKeyValue("orderId", order.getId())
                    .addKeyValue("pricingRequestId", evaluation.requestId())
                    .addKeyValue("status", order.getStatus())
                    .log("Order evaluation completed");
        } catch (RestClientException | InvalidPricingResponseException exception) {
            order = updateService.markUnavailable(order.getId());
            log.atWarn().addKeyValue("event", "pricing_unavailable")
                    .addKeyValue("orderId", order.getId())
                    .addKeyValue("failureType", exception.getClass().getSimpleName())
                    .log("Pricing evaluation unavailable");
        }
        return new CreationResult(toResponse(order), true);
    }

    public OrderResponse get(UUID id) {
        DeliveryOrder order = repository.findById(id).orElseThrow(OrderNotFoundException::new);
        return toResponse(order);
    }

    public static URI location(UUID id) {
        return URI.create("/api/v1/delivery-orders/" + id);
    }

    private static void ensureSamePayload(DeliveryOrder existing, String fingerprint) {
        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException();
        }
    }

    private static OrderResponse toResponse(DeliveryOrder order) {
        var price = order.getStatus() == OrderStatus.CONFIRMED
                ? new OrderResponse.Price(order.getPriceAmount(), order.getPriceCurrency()) : null;
        return new OrderResponse(order.getId(), order.getCustomerReference(), order.getStatus().name(),
                price, order.getRejectionCode(), order.getRejectionMessage(),
                order.getCreatedAt().atZone(AMSTERDAM).toOffsetDateTime());
    }

    public record CreationResult(OrderResponse response, boolean created) {}
}
