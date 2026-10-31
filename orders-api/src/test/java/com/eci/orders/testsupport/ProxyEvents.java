package com.eci.orders.testsupport;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent.ProxyRequestContext;

import java.util.Map;

/**
 * Builds the API Gateway proxy events used by the Architecture B tests.
 *
 * <p>This is the practical advantage of proxy integration: the contract between API Gateway and
 * the Lambda is a plain Java object, so the whole HTTP surface of the API can be tested on a
 * laptop with no gateway, no deployment and no network.</p>
 */
public final class ProxyEvents {

    private static final String STAGE = "dev";

    private ProxyEvents() {
    }

    public static APIGatewayProxyRequestEvent get(String orderId) {
        return event("GET", "/orders/{id}", "/orders/" + orderId)
                .withPathParameters(Map.of("id", orderId));
    }

    public static APIGatewayProxyRequestEvent delete(String orderId) {
        return event("DELETE", "/orders/{id}", "/orders/" + orderId)
                .withPathParameters(Map.of("id", orderId));
    }

    public static APIGatewayProxyRequestEvent findByCustomer(String customerId) {
        APIGatewayProxyRequestEvent event = event("GET", "/orders", "/orders");
        return customerId == null ? event : event.withQueryStringParameters(Map.of("customerId", customerId));
    }

    public static APIGatewayProxyRequestEvent post(String body) {
        return event("POST", "/orders", "/orders").withBody(body);
    }

    public static APIGatewayProxyRequestEvent event(String method, String resource, String path) {
        ProxyRequestContext requestContext = new ProxyRequestContext();
        requestContext.setStage(STAGE);
        requestContext.setPath("/" + STAGE + path);
        requestContext.setResourcePath(resource);
        requestContext.setHttpMethod(method);
        requestContext.setRequestId("test-request-id");

        return new APIGatewayProxyRequestEvent()
                .withHttpMethod(method)
                .withResource(resource)
                .withPath(path)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withRequestContext(requestContext)
                .withIsBase64Encoded(false);
    }
}
