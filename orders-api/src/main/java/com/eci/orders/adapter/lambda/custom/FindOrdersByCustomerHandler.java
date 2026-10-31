package com.eci.orders.adapter.lambda.custom;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.eci.orders.adapter.dto.ErrorCode;
import com.eci.orders.adapter.dto.OrderView;
import com.eci.orders.application.OrderService;
import com.eci.orders.infrastructure.ApplicationFactory;

import java.util.List;

/**
 * Architecture A handler for {@code GET /orders?customerId=...}.
 *
 * <p>Returning an empty list is a success: the customer simply has no orders. Returning 404 for
 * that case would mean "the search resource does not exist", which is false.</p>
 *
 * <p>Handler string:
 * {@code com.eci.orders.adapter.lambda.custom.FindOrdersByCustomerHandler::handleRequest}</p>
 */
public class FindOrdersByCustomerHandler implements RequestHandler<CustomerQueryInput, List<OrderView>> {

    private final OrderService orderService;

    public FindOrdersByCustomerHandler() {
        this(ApplicationFactory.orderService());
    }

    public FindOrdersByCustomerHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public List<OrderView> handleRequest(CustomerQueryInput input, Context context) {
        String customerId = input == null ? null : input.getCustomerId();
        if (customerId == null || customerId.isBlank()) {
            // The mapping template sends null when ?customerId= is absent.
            throw ApiError.of(ErrorCode.MISSING_QUERY_PARAMETER,
                    "Query parameter customerId is required");
        }
        try {
            return OrderView.fromAll(orderService.findOrdersByCustomer(customerId));
        } catch (RuntimeException e) {
            throw CustomErrorMapper.toApiError(e, context);
        }
    }
}
