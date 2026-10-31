package com.eci.orders.adapter.lambda.custom;

/**
 * Input of the single-order operations in Architecture A.
 *
 * <p>API Gateway builds this document from the path with a mapping template, so the Lambda never
 * sees a URL:</p>
 *
 * <pre>{@code
 * GET /orders/ORD-1001   ->   { "id": "ORD-1001" }
 * }</pre>
 */
public class OrderIdInput {

    private String id;

    public OrderIdInput() {
    }

    public OrderIdInput(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
