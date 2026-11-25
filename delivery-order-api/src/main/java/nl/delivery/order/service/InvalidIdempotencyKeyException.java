package nl.delivery.order.service;

public class InvalidIdempotencyKeyException extends RuntimeException {
    public InvalidIdempotencyKeyException() {
        super("The Idempotency-Key header must contain between 1 and 100 characters.");
    }
}
