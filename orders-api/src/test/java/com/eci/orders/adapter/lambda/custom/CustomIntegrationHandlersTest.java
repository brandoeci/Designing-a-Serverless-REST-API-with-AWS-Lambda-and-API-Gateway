package com.eci.orders.adapter.lambda.custom;

import com.eci.orders.adapter.dto.ErrorBody;
import com.eci.orders.adapter.dto.ErrorCode;
import com.eci.orders.adapter.dto.OrderView;
import com.eci.orders.adapter.json.Json;
import com.eci.orders.application.OrderService;
import com.eci.orders.testsupport.BrokenOrderRepository;
import com.eci.orders.testsupport.TestServices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architecture A handlers: API Gateway transforms the request, so these handlers receive plain
 * documents and never see HTTP.
 *
 * <p>Because the gateway decides the status code, what can be asserted locally is the shape of the
 * returned document and the error code embedded in the failure message. The mapping from those
 * error codes to 400/404 lives in the API Gateway configuration, and that part can only be tested
 * against a deployed stage (see {@code scripts/smoke-test.sh}). That gap is one of the findings of
 * the architecture comparison.</p>
 */
@DisplayName("Architecture A - custom integration handlers")
class CustomIntegrationHandlersTest {

    private final OrderService service = TestServices.seeded();

    private final GetOrderHandler getOrder = new GetOrderHandler(service);
    private final CreateOrderHandler createOrder = new CreateOrderHandler(service);
    private final FindOrdersByCustomerHandler findOrders = new FindOrdersByCustomerHandler(service);
    private final DeleteOrderHandler deleteOrder = new DeleteOrderHandler(service);

    @Test
    @DisplayName("GET: returns the order mapped from {\"id\": \"ORD-1001\"}")
    void getsExistingOrder() {
        OrderView view = getOrder.handleRequest(new OrderIdInput("ORD-1001"), null);

        assertEquals("ORD-1001", view.getId());
        assertEquals("CUST-42", view.getCustomerId());
        assertEquals("MacBook Air", view.getProduct());
        assertEquals(1, view.getQuantity());
    }

    @Test
    @DisplayName("GET: an unknown order fails with the ORDER_NOT_FOUND error document")
    void failsWithNotFoundDocument() {
        ApiError failure =
                assertThrows(ApiError.class, () -> getOrder.handleRequest(new OrderIdInput("ORD-999"), null));

        ErrorBody body = Json.read(failure.getMessage(), ErrorBody.class);
        assertEquals(ErrorCode.ORDER_NOT_FOUND, body.getError());
        assertEquals("Order ORD-999 does not exist", body.getMessage());
    }

    @Test
    @DisplayName("GET: a blank id is invalid input, not a missing order")
    void failsWithValidationErrorForBlankId() {
        ApiError failure =
                assertThrows(ApiError.class, () -> getOrder.handleRequest(new OrderIdInput("  "), null));

        assertEquals(ErrorCode.VALIDATION_ERROR, Json.read(failure.getMessage(), ErrorBody.class).getError());
    }

    @Test
    @DisplayName("POST: creates an order from the document built by the mapping template")
    void createsOrder() {
        OrderView view = createOrder.handleRequest(new CreateOrderInput("CUST-42", "MacBook Air", 1), null);

        assertEquals("ORD-2001", view.getId());
        assertEquals("MacBook Air", view.getProduct());
    }

    @Test
    @DisplayName("POST: a missing field fails with VALIDATION_ERROR naming the field")
    void rejectsIncompletePayload() {
        ApiError failure = assertThrows(ApiError.class,
                () -> createOrder.handleRequest(new CreateOrderInput("CUST-42", null, 1), null));

        ErrorBody body = Json.read(failure.getMessage(), ErrorBody.class);
        assertEquals(ErrorCode.VALIDATION_ERROR, body.getError());
        assertTrue(body.getMessage().contains("product"), body.getMessage());
    }

    @Test
    @DisplayName("POST: an empty document is rejected, not silently defaulted")
    void rejectsEmptyPayload() {
        ApiError failure = assertThrows(ApiError.class, () -> createOrder.handleRequest(null, null));

        assertEquals(ErrorCode.VALIDATION_ERROR, Json.read(failure.getMessage(), ErrorBody.class).getError());
    }

    @Test
    @DisplayName("GET search: returns the orders of the customer")
    void findsOrdersByCustomer() {
        List<OrderView> orders = findOrders.handleRequest(new CustomerQueryInput("CUST-42"), null);

        assertEquals(List.of("ORD-1001", "ORD-1002"), orders.stream().map(OrderView::getId).toList());
    }

    @Test
    @DisplayName("GET search: an unknown customer yields an empty array, not an error")
    void findsNoOrdersForUnknownCustomer() {
        assertTrue(findOrders.handleRequest(new CustomerQueryInput("CUST-100"), null).isEmpty());
    }

    @Test
    @DisplayName("GET search: a missing query parameter fails with MISSING_QUERY_PARAMETER")
    void rejectsMissingQueryParameter() {
        ApiError failure = assertThrows(ApiError.class,
                () -> findOrders.handleRequest(new CustomerQueryInput(null), null));

        assertEquals(ErrorCode.MISSING_QUERY_PARAMETER,
                Json.read(failure.getMessage(), ErrorBody.class).getError());
    }

    @Test
    @DisplayName("DELETE: returns nothing when the order existed")
    void deletesOrder() {
        assertNull(deleteOrder.handleRequest(new OrderIdInput("ORD-1001"), null));

        ApiError afterwards =
                assertThrows(ApiError.class, () -> getOrder.handleRequest(new OrderIdInput("ORD-1001"), null));
        assertEquals(ErrorCode.ORDER_NOT_FOUND, Json.read(afterwards.getMessage(), ErrorBody.class).getError());
    }

    @Test
    @DisplayName("DELETE: an unknown order fails with ORDER_NOT_FOUND")
    void failsDeletingUnknownOrder() {
        ApiError failure =
                assertThrows(ApiError.class, () -> deleteOrder.handleRequest(new OrderIdInput("ORD-999"), null));

        ErrorBody body = Json.read(failure.getMessage(), ErrorBody.class);
        assertEquals(ErrorCode.ORDER_NOT_FOUND, body.getError());
        assertEquals("Order ORD-999 does not exist", body.getMessage());
    }

    @Test
    @DisplayName("an unexpected failure becomes INTERNAL_ERROR and leaks nothing")
    void mapsUnexpectedFailures() {
        GetOrderHandler broken = new GetOrderHandler(BrokenOrderRepository.service());

        ApiError failure =
                assertThrows(ApiError.class, () -> broken.handleRequest(new OrderIdInput("ORD-1001"), null));

        ErrorBody body = Json.read(failure.getMessage(), ErrorBody.class);
        assertEquals(ErrorCode.INTERNAL_ERROR, body.getError());
        assertEquals("An unexpected error occurred", body.getMessage());
        assertFalse(body.getMessage().contains("storage unavailable"), "internal details must not leak");
    }

    @Test
    @DisplayName("handlers can also be built by AWS through their no-argument constructor")
    void hasNoArgumentConstructors() {
        assertEquals("ORD-1001", new GetOrderHandler().handleRequest(new OrderIdInput("ORD-1001"), null).getId());
    }
}
