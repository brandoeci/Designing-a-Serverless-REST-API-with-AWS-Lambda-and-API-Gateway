package com.eci.orders.domain;

/**
 * Raised when an operation refers to an order that does not exist.
 *
 * <p>Again: no HTTP status code here. Each adapter translates this outcome into
 * {@code 404 Not Found}.</p>
 */
public class OrderNotFoundException extends RuntimeException {

    private final String orderId;

    public OrderNotFoundException(String orderId) {
        super("Order " + orderId + " does not exist");
        this.orderId = orderId;
    }

    public String orderId() {
        return orderId;
    }
}
