package com.eci.orders.adapter.lambda.proxy;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Reads the two things the router needs out of an API Gateway proxy event: which resource was
 * addressed and which order id was in the path.
 *
 * <p>With a proxy integration, routing becomes Java code. API Gateway still tells us the resource
 * template in {@code event.resource} ({@code /orders/{id}}), but relying only on that would make
 * the handler untestable without reproducing every gateway detail, and it would break if the path
 * parameter were renamed. So the event is read if present and the raw path is used as a fallback,
 * which also makes the handler work when invoked from a test or from the Lambda console.</p>
 */
final class Routes {

    /** Collection resource: {@code POST /orders} and {@code GET /orders?customerId=...}. */
    static final String ORDERS = "/orders";

    /** Single-order resource: {@code GET /orders/{id}}. */
    static final String ORDER_BY_ID = "/orders/{id}";

    /** Nothing in this API matches the request. */
    static final String UNKNOWN = "";

    private Routes() {
    }

    static String resourceOf(APIGatewayProxyRequestEvent event) {
        String candidate = firstUsable(event.getResource(), event.getPath());
        if (candidate == null) {
            return UNKNOWN;
        }

        List<String> segments = Arrays.stream(candidate.split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();

        int collectionIndex = segments.indexOf("orders");
        if (collectionIndex < 0) {
            return UNKNOWN;
        }

        // Anything after /orders is the identifier; a stage prefix such as /dev is ignored.
        return switch (segments.size() - collectionIndex - 1) {
            case 0 -> ORDERS;
            case 1 -> ORDER_BY_ID;
            default -> UNKNOWN;
        };
    }

    /** The order id taken from the path parameters, or from the raw path as a fallback. */
    static String orderIdOf(APIGatewayProxyRequestEvent event) {
        Map<String, String> pathParameters = event.getPathParameters();
        if (pathParameters != null) {
            String fromGateway = pathParameters.get("id");
            if (fromGateway != null && !fromGateway.isBlank()) {
                return fromGateway;
            }
        }

        String path = event.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        return lastSegment.isBlank() ? null : URLDecoder.decode(lastSegment, StandardCharsets.UTF_8);
    }

    /** A query string parameter, treating an absent query string as an absent parameter. */
    static String queryParameter(APIGatewayProxyRequestEvent event, String name) {
        Map<String, String> parameters = event.getQueryStringParameters();
        return parameters == null ? null : parameters.get(name);
    }

    private static String firstUsable(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
