package com.eci.orders.adapter.lambda.custom;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eci.orders.application.OrderService;
import com.eci.orders.infrastructure.ApplicationFactory;

/**
 * Architecture A handler for {@code DELETE /orders/{id}}.
 *
 * <p>Reuses {@link OrderIdInput} and the same mapping template as the {@code GET}, because a delete
 * needs exactly the same input: the identifier extracted from the path.</p>
 *
 * <p>The return type is {@link Void} and the method returns {@code null}: there is nothing to say
 * about a successful deletion. The AWS runtime writes that as the literal {@code null}, so the
 * {@code 204} integration response in API Gateway uses an empty response template to drop it — a
 * 204 must not carry a body. In Architecture B the same decision is one method call,
 * {@code HttpResponses.noContent()}.</p>
 *
 * <p>Handler string:
 * {@code com.eci.orders.adapter.lambda.custom.DeleteOrderHandler::handleRequest}</p>
 */
public class DeleteOrderHandler implements RequestHandler<OrderIdInput, Void> {

    private final OrderService orderService;

    public DeleteOrderHandler() {
        this(ApplicationFactory.orderService());
    }

    public DeleteOrderHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public Void handleRequest(OrderIdInput input, Context context) {
        try {
            orderService.deleteOrder(input == null ? null : input.getId());
            return null;
        } catch (RuntimeException e) {
            throw CustomErrorMapper.toApiError(e, context);
        }
    }
}
