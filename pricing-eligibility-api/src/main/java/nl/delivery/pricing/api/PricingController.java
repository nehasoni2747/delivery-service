package nl.delivery.pricing.api;

import jakarta.validation.Valid;
import nl.delivery.pricing.domain.PricingPolicy;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing/evaluations")
public class PricingController {
    private final PricingPolicy pricingPolicy;

    public PricingController(PricingPolicy pricingPolicy) {
        this.pricingPolicy = pricingPolicy;
    }

    @PostMapping
    public EvaluationResponse evaluate(@Valid @RequestBody EvaluationRequest request) {
        return pricingPolicy.evaluate(request);
    }
}
