package com.eci.orders.domain;

/**
 * An order placed by a customer.
 *
 * <p>This is the central concept of the application. It is immutable and self-validating: if an
 * {@code Order} instance exists, it is valid. It knows nothing about HTTP, JSON, AWS Lambda or
 * API Gateway, which is what allows every rule below to be tested with plain JUnit.</p>
 */
public record Order(String id, String customerId, String product, int quantity) {

    public Order {
        if (isBlank(id)) {
            throw new InvalidOrderException("id must not be blank");
        }
        if (isBlank(customerId)) {
            throw new InvalidOrderException("customerId must not be blank");
        }
        if (isBlank(product)) {
            throw new InvalidOrderException("product must not be blank");
        }
        if (quantity <= 0) {
            throw new InvalidOrderException("quantity must be greater than zero");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
