package com.eci.orders.adapter.lambda.proxy;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.eci.orders.adapter.dto.ErrorBody;
import com.eci.orders.adapter.dto.ErrorCode;
import com.eci.orders.adapter.dto.OrderView;
import com.eci.orders.adapter.json.Json;
import com.eci.orders.testsupport.BrokenOrderRepository;
import com.eci.orders.testsupport.ProxyEvents;
import com.eci.orders.testsupport.TestServices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architecture B: the entire HTTP contract of the API, verified on a laptop.
 *
 * <p>Status codes, headers and error bodies are all produced by Java code here, so every one of
 * them is a unit test away. In Architecture A most of these assertions would require a deployed
 * stage.</p>
 */
@DisplayName("Architecture B - OrdersApiHandler (proxy integration)")
class OrdersApiHandlerTest {

    private final OrdersApiHandler handler = new OrdersApiHandler(TestServices.seeded());

    @Nested
    @DisplayName("GET /orders/{id}")
    class GetOrder {

        @Test
        @DisplayName("200 with the order when it exists")
        void returnsOrder() {
            APIGatewayProxyResponseEvent response = handler.handleRequest(ProxyEvents.get("ORD-1001"), null);

            assertEquals(200, response.getStatusCode());
            assertEquals("application/json; charset=utf-8", response.getHeaders().get("Content-Type"));

            OrderView body = Json.read(response.getBody(), OrderView.class);
            assertEquals("ORD-1001", body.getId());
            assertEquals("CUST-42", body.getCustomerId());
            assertEquals("MacBook Air", body.getProduct());
            assertEquals(1, body.getQuantity());
        }

        @Test
        @DisplayName("404 with ORDER_NOT_FOUND when it does not exist")
        void returnsNotFound() {
            APIGatewayProxyResponseEvent response = handler.handleRequest(ProxyEvents.get("ORD-999"), null);

            assertEquals(404, response.getStatusCode());
            ErrorBody body = Json.read(response.getBody(), ErrorBody.class);
            assertEquals(ErrorCode.ORDER_NOT_FOUND, body.getError());
            assertEquals("Order ORD-999 does not exist", body.getMessage());
        }

        @Test
        @DisplayName("works when the event carries no pathParameters, as in a console test")
        void fallsBackToThePath() {
            APIGatewayProxyRequestEvent event =
                    ProxyEvents.event("GET", null, "/orders/ORD-1002");

            APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

            assertEquals(200, response.getStatusCode());
            assertEquals("ORD-1002", Json.read(response.getBody(), OrderView.class).getId());
        }

        @Test
        @DisplayName("405 with an Allow header when the method is not supported")
        void rejectsUnsupportedMethod() {
            APIGatewayProxyRequestEvent event =
                    ProxyEvents.event("PUT", "/orders/{id}", "/orders/ORD-1001");

            APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

            assertEquals(405, response.getStatusCode());
            assertEquals("GET", response.getHeaders().get("Allow"));
            assertEquals(ErrorCode.METHOD_NOT_ALLOWED, Json.read(response.getBody(), ErrorBody.class).getError());
        }
    }

    @Nested
    @DisplayName("POST /orders")
    class CreateOrder {

        @Test
        @DisplayName("201 with the created order and a Location header")
        void createsOrder() {
            String body = "{\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}";

            APIGatewayProxyResponseEvent response = handler.handleRequest(ProxyEvents.post(body), null);

            assertEquals(201, response.getStatusCode());
            assertEquals("/dev/orders/ORD-2001", response.getHeaders().get("Location"));

            OrderView created = Json.read(response.getBody(), OrderView.class);
            assertEquals("ORD-2001", created.getId());
            assertEquals("CUST-42", created.getCustomerId());
            assertEquals("MacBook Air", created.getProduct());
            assertEquals(1, created.getQuantity());
        }

        @Test
        @DisplayName("the created order can be read back immediately")
        void createdOrderIsRetrievable() {
            String body = "{\"customerId\":\"CUST-55\",\"product\":\"Monitor\",\"quantity\":2}";
            handler.handleRequest(ProxyEvents.post(body), null);

            APIGatewayProxyResponseEvent response = handler.handleRequest(ProxyEvents.get("ORD-2001"), null);

            assertEquals(200, response.getStatusCode());
            assertEquals("CUST-55", Json.read(response.getBody(), OrderView.class).getCustomerId());
        }

        @Test
        @DisplayName("400 VALIDATION_ERROR when a field is missing")
        void rejectsIncompleteBody() {
            String body = "{\"customerId\":\"CUST-42\",\"quantity\":1}";

            APIGatewayProxyResponseEvent response = handler.handleRequest(ProxyEvents.post(body), null);

            assertEquals(400, response.getStatusCode());
            ErrorBody error = Json.read(response.getBody(), ErrorBody.class);
            assertEquals(ErrorCode.VALIDATION_ERROR, error.getError());
            assertTrue(error.getMessage().contains("product"), error.getMessage());
        }

        @Test
        @DisplayName("400 VALIDATION_ERROR when the quantity is not positive")
        void rejectsNonPositiveQuantity() {
            String body = "{\"customerId\":\"CUST-42\",\"product\":\"Mouse\",\"quantity\":0}";

            APIGatewayProxyResponseEvent response = handler.handleRequest(ProxyEvents.post(body), null);

            assertEquals(400, response.getStatusCode());
            assertEquals(ErrorCode.VALIDATION_ERROR, Json.read(response.getBody(), ErrorBody.class).getError());
        }

        @Test
        @DisplayName("400 MALFORMED_REQUEST when the body is not JSON")
        void rejectsMalformedJson() {
            APIGatewayProxyResponseEvent response = handler.handleRequest(ProxyEvents.post("not json"), null);

            assertEquals(400, response.getStatusCode());
            assertEquals(ErrorCode.MALFORMED_REQUEST, Json.read(response.getBody(), ErrorBody.class).getError());
        }

        @Test
        @DisplayName("400 MALFORMED_REQUEST when there is no body at all")
        void rejectsMissingBody() {
            APIGatewayProxyResponseEvent response = handler.handleRequest(ProxyEvents.post(null), null);

            assertEquals(400, response.getStatusCode());
            assertEquals(ErrorCode.MALFORMED_REQUEST, Json.read(response.getBody(), ErrorBody.class).getError());
        }

        @Test
        @DisplayName("decodes a base64-encoded body")
        void decodesBase64Body() {
            String json = "{\"customerId\":\"CUST-42\",\"product\":\"iPad\",\"quantity\":1}";
            APIGatewayProxyRequestEvent event = ProxyEvents.post(
                            Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)))
                    .withIsBase64Encoded(true);

            APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

            assertEquals(201, response.getStatusCode());
            assertEquals("iPad", Json.read(response.getBody(), OrderView.class).getProduct());
        }
    }

    @Nested
    @DisplayName("GET /orders?customerId=...")
    class FindOrdersByCustomer {

        @Test
        @DisplayName("200 with the orders of the customer")
        void returnsOrdersOfCustomer() {
            APIGatewayProxyResponseEvent response =
                    handler.handleRequest(ProxyEvents.findByCustomer("CUST-42"), null);

            assertEquals(200, response.getStatusCode());
            OrderView[] orders = Json.read(response.getBody(), OrderView[].class);
            assertEquals(List.of("ORD-1001", "ORD-1002"),
                    List.of(orders[0].getId(), orders[1].getId()));
        }

        @Test
        @DisplayName("200 with an empty array for a customer with no orders")
        void returnsEmptyArray() {
            APIGatewayProxyResponseEvent response =
                    handler.handleRequest(ProxyEvents.findByCustomer("CUST-100"), null);

            assertEquals(200, response.getStatusCode());
            assertEquals("[]", response.getBody());
        }

        @Test
        @DisplayName("400 MISSING_QUERY_PARAMETER when customerId is absent")
        void rejectsMissingQueryParameter() {
            APIGatewayProxyResponseEvent response =
                    handler.handleRequest(ProxyEvents.findByCustomer(null), null);

            assertEquals(400, response.getStatusCode());
            assertEquals(ErrorCode.MISSING_QUERY_PARAMETER,
                    Json.read(response.getBody(), ErrorBody.class).getError());
        }

        @Test
        @DisplayName("400 VALIDATION_ERROR when customerId is present but blank")
        void rejectsBlankQueryParameter() {
            APIGatewayProxyResponseEvent response =
                    handler.handleRequest(ProxyEvents.findByCustomer("   "), null);

            assertEquals(400, response.getStatusCode());
            assertEquals(ErrorCode.VALIDATION_ERROR, Json.read(response.getBody(), ErrorBody.class).getError());
        }

        @Test
        @DisplayName("405 with an Allow header for an unsupported method on the collection")
        void rejectsUnsupportedMethod() {
            APIGatewayProxyResponseEvent response =
                    handler.handleRequest(ProxyEvents.event("PATCH", "/orders", "/orders"), null);

            assertEquals(405, response.getStatusCode());
            assertEquals("GET, POST", response.getHeaders().get("Allow"));
        }
    }

    @Nested
    @DisplayName("unexpected input")
    class UnexpectedInput {

        @Test
        @DisplayName("404 ROUTE_NOT_FOUND for a path this API does not serve")
        void unknownRoute() {
            APIGatewayProxyResponseEvent response =
                    handler.handleRequest(ProxyEvents.event("GET", "/invoices", "/invoices"), null);

            assertEquals(404, response.getStatusCode());
            assertEquals(ErrorCode.ROUTE_NOT_FOUND, Json.read(response.getBody(), ErrorBody.class).getError());
        }

        @Test
        @DisplayName("400 MALFORMED_REQUEST when invoked without an event")
        void nullEvent() {
            APIGatewayProxyResponseEvent response = handler.handleRequest(null, null);

            assertEquals(400, response.getStatusCode());
            assertEquals(ErrorCode.MALFORMED_REQUEST, Json.read(response.getBody(), ErrorBody.class).getError());
        }

        @Test
        @DisplayName("500 INTERNAL_ERROR without leaking details when the store fails")
        void internalFailure() {
            OrdersApiHandler failing = new OrdersApiHandler(BrokenOrderRepository.service());

            APIGatewayProxyResponseEvent response = failing.handleRequest(ProxyEvents.get("ORD-1001"), null);

            assertEquals(500, response.getStatusCode());
            ErrorBody error = Json.read(response.getBody(), ErrorBody.class);
            assertEquals(ErrorCode.INTERNAL_ERROR, error.getError());
            assertEquals("An unexpected error occurred", error.getMessage());
            assertFalse(error.getMessage().contains("Exception"), "internal details must not leak");
        }

        @Test
        @DisplayName("204 responses carry neither body nor content type")
        void noContentHasNoBody() {
            APIGatewayProxyResponseEvent response = HttpResponses.noContent();

            assertEquals(204, response.getStatusCode());
            assertNull(response.getBody());
            assertNull(response.getHeaders());
        }

        @Test
        @DisplayName("a 405 response is produced for every method the resource does not define")
        void allowHeaderIsAlwaysPresent() {
            Map<String, String> cases = Map.of("DELETE", "GET, POST", "PUT", "GET, POST");

            cases.forEach((method, allow) -> {
                APIGatewayProxyResponseEvent response =
                        handler.handleRequest(ProxyEvents.event(method, "/orders", "/orders"), null);
                assertEquals(405, response.getStatusCode(), method);
                assertEquals(allow, response.getHeaders().get("Allow"), method);
            });
        }
    }

}
