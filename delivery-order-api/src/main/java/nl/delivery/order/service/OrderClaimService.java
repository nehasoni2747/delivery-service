package nl.delivery.order.service;

import java.time.Clock;
import nl.delivery.order.api.CreateOrderRequest;
import nl.delivery.order.persistence.DeliveryOrder;
import nl.delivery.order.persistence.DeliveryOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderClaimService {
    public record Claim(DeliveryOrder order, boolean created) {}

    private final DeliveryOrderRepository repository;
    private final Clock clock;

    public OrderClaimService(DeliveryOrderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Claim claim(String key, String fingerprint, CreateOrderRequest request) {
        var existing = repository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            if (!existing.get().getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException();
            }
            return new Claim(existing.get(), false);
        }
        return new Claim(repository.saveAndFlush(
                new DeliveryOrder(key, fingerprint, request, clock.instant())), true);
    }
}
