package com.eci.orders.adapter.lambda.custom;

/**
 * Input of the search operation in Architecture A.
 *
 * <pre>{@code
 * GET /orders?customerId=CUST-42   ->   { "customerId": "CUST-42" }
 * }</pre>
 *
 * <p>When the query parameter is absent the mapping template sends {@code null}, which the handler
 * turns into a 400. The gateway could reject it instead with a request validator; that trade-off is
 * discussed in the README.</p>
 */
public class CustomerQueryInput {

    private String customerId;

    public CustomerQueryInput() {
    }

    public CustomerQueryInput(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
}
