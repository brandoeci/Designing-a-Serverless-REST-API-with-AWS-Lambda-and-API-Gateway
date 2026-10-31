package com.eci.orders.adapter.lambda.custom;

import com.eci.orders.application.CreateOrderRequest;

/**
 * Input of {@code POST /orders} in Architecture A.
 *
 * <p>API Gateway maps the three fields it expects out of the request body, so this type mirrors the
 * wire format rather than the application model. {@code quantity} is a boxed {@link Integer} so
 * that "absent" survives the trip and can be answered with a 400 instead of silently becoming
 * 0.</p>
 */
public class CreateOrderInput {

    private String customerId;
    private String product;
    private Integer quantity;

    public CreateOrderInput() {
    }

    public CreateOrderInput(String customerId, String product, Integer quantity) {
        this.customerId = customerId;
        this.product = product;
        this.quantity = quantity;
    }

    /** Translates the boundary representation into the application request. */
    public CreateOrderRequest toCreateOrderRequest() {
        return new CreateOrderRequest(customerId, product, quantity);
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
