package com.eci.orders.adapter.lambda.proxy;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.eci.orders.adapter.dto.ErrorBody;
import com.eci.orders.adapter.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the HTTP responses of Architecture B.
 *
 * <p>Every status code, header and error body of the proxy implementation is produced here, which
 * means the whole HTTP contract of the API can be reviewed by reading one short file instead of
 * clicking through a console.</p>
 */
final class HttpResponses {

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String JSON_UTF8 = "application/json; charset=utf-8";

    private HttpResponses() {
    }

    static APIGatewayProxyResponseEvent json(int statusCode, Object body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers(Map.of()))
                .withBody(Json.write(body));
    }

    /**
     * 201 with a {@code Location} header: a client that creates a resource should be told where it
     * now lives instead of having to assemble the URL from the body.
     */
    static APIGatewayProxyResponseEvent created(Object body, String location) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(201)
                .withHeaders(headers(Map.of("Location", location)))
                .withBody(Json.write(body));
    }

    /** 204 carries no body, so it must not advertise a content type either. */
    static APIGatewayProxyResponseEvent noContent() {
        return new APIGatewayProxyResponseEvent().withStatusCode(204);
    }

    static APIGatewayProxyResponseEvent error(int statusCode, String code, String message) {
        return json(statusCode, new ErrorBody(code, message));
    }

    /** 405 must say which methods the resource does support. */
    static APIGatewayProxyResponseEvent methodNotAllowed(String allowedMethods, String code, String message) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(405)
                .withHeaders(headers(Map.of("Allow", allowedMethods)))
                .withBody(Json.write(new ErrorBody(code, message)));
    }

    private static Map<String, String> headers(Map<String, String> extra) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(CONTENT_TYPE, JSON_UTF8);
        headers.putAll(extra);
        return headers;
    }
}
