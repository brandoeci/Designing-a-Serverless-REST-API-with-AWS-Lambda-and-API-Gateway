package com.eci.orders.adapter.lambda.custom;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eci.orders.adapter.dto.OrderView;
import com.eci.orders.application.OrderService;
import com.eci.orders.infrastructure.ApplicationFactory;

/**
 * Architecture A handler for {@code GET /orders/{id}}.
 *
 * <p>Note what this signature does <em>not</em> mention: no event type, no headers, no status code,
 * no JSON. The AWS Java runtime deserializes the document produced by the API Gateway mapping
 * template into {@link OrderIdInput} and serializes the returned {@link OrderView} back to JSON.
 * The HTTP status code is decided by API Gateway, not here.</p>
 *
 * <p>Handler string for the Lambda configuration:
 * {@code com.eci.orders.adapter.lambda.custom.GetOrderHandler::handleRequest}</p>
 */
public class GetOrderHandler implements RequestHandler<OrderIdInput, OrderView> {

    private final OrderService orderService;

    /** Used by AWS: the runtime requires a public no-argument constructor. */
    public GetOrderHandler() {
        this(ApplicationFactory.orderService());
    }

    /** Used by tests, which inject an isolated application instance. */
    public GetOrderHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public OrderView handleRequest(OrderIdInput input, Context context) {
        try {
            return OrderView.from(orderService.getOrder(input == null ? null : input.getId()));
        } catch (RuntimeException e) {
            throw CustomErrorMapper.toApiError(e, context);
        }
    }
}
