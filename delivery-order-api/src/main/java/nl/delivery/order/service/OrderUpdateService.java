package nl.delivery.order.service;

import java.util.UUID;
import nl.delivery.order.api.PricingEvaluationResponse;
import nl.delivery.order.persistence.DeliveryOrder;
import nl.delivery.order.persistence.DeliveryOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderUpdateService {
    private final DeliveryOrderRepository repository;

    public OrderUpdateService(DeliveryOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DeliveryOrder complete(UUID id, PricingEvaluationResponse evaluation) {
        DeliveryOrder order = repository.findById(id).orElseThrow();
        order.complete(evaluation);
        return order;
    }

    @Transactional
    public DeliveryOrder markUnavailable(UUID id) {
        DeliveryOrder order = repository.findById(id).orElseThrow();
        order.markPricingUnavailable();
        return order;
    }
}
