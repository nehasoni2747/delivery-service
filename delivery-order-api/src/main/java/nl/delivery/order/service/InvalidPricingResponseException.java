package nl.delivery.order.service;

public class InvalidPricingResponseException extends RuntimeException {
    public InvalidPricingResponseException() {
        super("Pricing service returned an invalid evaluation.");
    }
}
