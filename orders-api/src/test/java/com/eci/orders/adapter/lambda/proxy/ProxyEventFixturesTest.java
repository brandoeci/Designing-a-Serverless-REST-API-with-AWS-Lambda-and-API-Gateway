package com.eci.orders.adapter.lambda.proxy;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.eci.orders.adapter.dto.OrderView;
import com.eci.orders.adapter.json.Json;
import com.eci.orders.testsupport.TestServices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Replays the real API Gateway proxy events stored in {@code src/test/resources/events}.
 *
 * <p>Those files are the ones documented in {@code docs/events/README.md}. Running them through the
 * handler keeps the documentation honest: if the event format in the repository stopped matching
 * what the code reads, this test would fail.</p>
 */
@DisplayName("Saved API Gateway proxy events")
class ProxyEventFixturesTest {

    private final OrdersApiHandler handler = new OrdersApiHandler(TestServices.seeded());

    @Test
    @DisplayName("get-order.json returns 200 with ORD-1001")
    void getOrderEvent() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(load("get-order.json"), null);

        assertEquals(200, response.getStatusCode());
        assertEquals("ORD-1001", Json.read(response.getBody(), OrderView.class).getId());
    }

    @Test
    @DisplayName("post-orders.json returns 201 with a Location header")
    void postOrderEvent() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(load("post-orders.json"), null);

        assertEquals(201, response.getStatusCode());
        assertEquals("/dev/orders/ORD-2001", response.getHeaders().get("Location"));
        assertEquals("MacBook Air", Json.read(response.getBody(), OrderView.class).getProduct());
    }

    @Test
    @DisplayName("get-orders-by-customer.json returns 200 with the two seeded orders")
    void findByCustomerEvent() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(load("get-orders-by-customer.json"), null);

        assertEquals(200, response.getStatusCode());
        assertEquals(2, Json.read(response.getBody(), OrderView[].class).length);
    }

    @Test
    @DisplayName("delete-order.json returns 204 with no body")
    void deleteOrderEvent() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(load("delete-order.json"), null);

        assertEquals(204, response.getStatusCode());
        assertNull(response.getBody());
    }

    private static APIGatewayProxyRequestEvent load(String fileName) {
        try (InputStream stream = ProxyEventFixturesTest.class.getResourceAsStream("/events/" + fileName)) {
            assertNotNull(stream, "missing test fixture /events/" + fileName);
            return Json.read(new String(stream.readAllBytes(), StandardCharsets.UTF_8),
                    APIGatewayProxyRequestEvent.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
