package nl.delivery.order.service;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException() {
        super("Delivery order was not found.");
    }
}
