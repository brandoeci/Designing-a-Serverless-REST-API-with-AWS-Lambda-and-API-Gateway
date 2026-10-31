package com.eci.orders.application;

/**
 * Everything the application needs in order to create an order.
 *
 * <p>The fields are nullable and {@code quantity} is a boxed {@link Integer} on purpose: this type
 * carries <em>unvalidated</em> input that originated outside the application, so "the client did
 * not send a quantity" must be representable and distinguishable from "the client sent 0".</p>
 */
public record CreateOrderRequest(String customerId, String product, Integer quantity) {
}
