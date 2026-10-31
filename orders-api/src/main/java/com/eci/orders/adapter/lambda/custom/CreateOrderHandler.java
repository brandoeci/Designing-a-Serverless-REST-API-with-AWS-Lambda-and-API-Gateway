package com.eci.orders.adapter.lambda.custom;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eci.orders.adapter.dto.OrderView;
import com.eci.orders.application.OrderService;
import com.eci.orders.infrastructure.ApplicationFactory;

/**
 * Architecture A handler for {@code POST /orders}.
 *
 * <p>The 201 status code and the {@code Location} header are configured in the API Gateway method
 * response, which is why nothing about them appears in this class. The price of that separation is
 * that reading this code tells you nothing about the status code the client will see.</p>
 *
 * <p>Handler string:
 * {@code com.eci.orders.adapter.lambda.custom.CreateOrderHandler::handleRequest}</p>
 */
public class CreateOrderHandler implements RequestHandler<CreateOrderInput, OrderView> {

    private final OrderService orderService;

    public CreateOrderHandler() {
        this(ApplicationFactory.orderService());
    }

    public CreateOrderHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public OrderView handleRequest(CreateOrderInput input, Context context) {
        try {
            CreateOrderInput received = input == null ? new CreateOrderInput() : input;
            return OrderView.from(orderService.createOrder(received.toCreateOrderRequest()));
        } catch (RuntimeException e) {
            throw CustomErrorMapper.toApiError(e, context);
        }
    }
}
