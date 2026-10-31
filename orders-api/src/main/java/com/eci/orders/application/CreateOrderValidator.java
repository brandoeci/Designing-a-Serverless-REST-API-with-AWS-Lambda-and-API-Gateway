package com.eci.orders.application;

import com.eci.orders.domain.InvalidOrderException;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates {@link CreateOrderRequest} instances.
 *
 * <p>Deliberately placed in the application layer, not in the HTTP adapter and not in API Gateway:
 * "a quantity must be positive" is a rule of the business, so it has to hold no matter who calls
 * the application (HTTP today, a batch job or an SQS consumer tomorrow).</p>
 *
 * <p>All violations are reported together so that a client can fix the whole payload in one round
 * trip instead of discovering one error at a time.</p>
 */
public final class CreateOrderValidator {

    public static final int MAX_CUSTOMER_ID_LENGTH = 64;
    public static final int MAX_PRODUCT_LENGTH = 120;
    public static final int MAX_QUANTITY = 1_000;

    private CreateOrderValidator() {
    }

    /**
     * @return the same request with trimmed text fields, ready to become an
     *         {@link com.eci.orders.domain.Order}
     * @throws InvalidOrderException if the request violates at least one rule
     */
    public static CreateOrderRequest validate(CreateOrderRequest request) {
        if (request == null) {
            throw new InvalidOrderException("a request body is required");
        }

        List<String> violations = new ArrayList<>();

        String customerId = trimToNull(request.customerId());
        if (customerId == null) {
            violations.add("customerId is required and must not be blank");
        } else if (customerId.length() > MAX_CUSTOMER_ID_LENGTH) {
            violations.add("customerId must not exceed " + MAX_CUSTOMER_ID_LENGTH + " characters");
        }

        String product = trimToNull(request.product());
        if (product == null) {
            violations.add("product is required and must not be blank");
        } else if (product.length() > MAX_PRODUCT_LENGTH) {
            violations.add("product must not exceed " + MAX_PRODUCT_LENGTH + " characters");
        }

        Integer quantity = request.quantity();
        if (quantity == null) {
            violations.add("quantity is required");
        } else if (quantity <= 0) {
            violations.add("quantity must be greater than zero");
        } else if (quantity > MAX_QUANTITY) {
            violations.add("quantity must not exceed " + MAX_QUANTITY);
        }

        if (!violations.isEmpty()) {
            throw new InvalidOrderException(violations);
        }

        return new CreateOrderRequest(customerId, product, quantity);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
