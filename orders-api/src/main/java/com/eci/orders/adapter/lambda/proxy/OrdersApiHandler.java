package com.eci.orders.adapter.lambda.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.eci.orders.adapter.dto.ErrorCode;
import com.eci.orders.adapter.dto.OrderView;
import com.eci.orders.adapter.json.Json;
import com.eci.orders.adapter.json.MalformedRequestException;
import com.eci.orders.application.CreateOrderRequest;
import com.eci.orders.application.OrderService;
import com.eci.orders.domain.InvalidOrderException;
import com.eci.orders.domain.Order;
import com.eci.orders.domain.OrderNotFoundException;
import com.eci.orders.infrastructure.ApplicationFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Architecture B: the whole Orders API behind a single Lambda using proxy integration.
 *
 * <p>This class is the HTTP adapter. It is the only place in the project that is allowed to know
 * about methods, paths, query parameters, headers, status codes and JSON text. It translates an
 * {@link APIGatewayProxyRequestEvent} into calls on {@link OrderService} and translates the
 * outcome, including domain exceptions, back into an HTTP response.</p>
 *
 * <p>The application layer below it never learns that HTTP exists, which is why the exact same
 * {@code OrderService} serves Architecture A, Architecture B and the unit tests.</p>
 *
 * <p>Handler string:
 * {@code com.eci.orders.adapter.lambda.proxy.OrdersApiHandler::handleRequest}</p>
 */
public class OrdersApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final OrderService orderService;

    /** Used by AWS. */
    public OrdersApiHandler() {
        this(ApplicationFactory.orderService());
    }

    /** Used by tests, which inject an isolated application instance. */
    public OrdersApiHandler(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            return route(event);
        } catch (OrderNotFoundException e) {
            return HttpResponses.error(404, ErrorCode.ORDER_NOT_FOUND, e.getMessage());
        } catch (InvalidOrderException e) {
            return HttpResponses.error(400, ErrorCode.VALIDATION_ERROR, e.getMessage());
        } catch (MalformedRequestException e) {
            return HttpResponses.error(400, ErrorCode.MALFORMED_REQUEST, e.getMessage());
        } catch (RuntimeException e) {
            // Last line of defence: a bug must not leak a stack trace or a Java class name to a
            // client, but it must be visible in CloudWatch.
            log(context, "Unhandled failure: " + e);
            return HttpResponses.error(500, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
        }
    }

    private APIGatewayProxyResponseEvent route(APIGatewayProxyRequestEvent event) {
        if (event == null) {
            throw new MalformedRequestException("An API Gateway proxy event is required");
        }

        String method = method(event);
        String resource = Routes.resourceOf(event);

        return switch (resource) {
            case Routes.ORDERS -> switch (method) {
                case "POST" -> createOrder(event);
                case "GET" -> findOrdersByCustomer(event);
                default -> HttpResponses.methodNotAllowed("GET, POST", ErrorCode.METHOD_NOT_ALLOWED,
                        method + " is not supported on /orders");
            };
            case Routes.ORDER_BY_ID -> switch (method) {
                case "GET" -> getOrder(event);
                default -> HttpResponses.methodNotAllowed("GET", ErrorCode.METHOD_NOT_ALLOWED,
                        method + " is not supported on /orders/{id}");
            };
            default -> HttpResponses.error(404, ErrorCode.ROUTE_NOT_FOUND,
                    "No resource matches " + method + " " + nullSafe(event.getPath()));
        };
    }

    // ---------------------------------------------------------------- operations

    private APIGatewayProxyResponseEvent createOrder(APIGatewayProxyRequestEvent event) {
        CreateOrderRequest request = Json.read(body(event), CreateOrderRequest.class);
        Order created = orderService.createOrder(request);
        return HttpResponses.created(OrderView.from(created), location(event, created.id()));
    }

    private APIGatewayProxyResponseEvent getOrder(APIGatewayProxyRequestEvent event) {
        Order order = orderService.getOrder(Routes.orderIdOf(event));
        return HttpResponses.json(200, OrderView.from(order));
    }

    private APIGatewayProxyResponseEvent findOrdersByCustomer(APIGatewayProxyRequestEvent event) {
        String customerId = Routes.queryParameter(event, "customerId");
        if (customerId == null) {
            // Absent parameter is an HTTP-level problem, so the adapter answers it directly: the
            // application has no opinion about query strings.
            return HttpResponses.error(400, ErrorCode.MISSING_QUERY_PARAMETER,
                    "Query parameter customerId is required");
        }
        return HttpResponses.json(200, OrderView.fromAll(orderService.findOrdersByCustomer(customerId)));
    }

    // ---------------------------------------------------------------- event reading

    private static String method(APIGatewayProxyRequestEvent event) {
        String method = event.getHttpMethod();
        return method == null ? "" : method.toUpperCase();
    }

    /** API Gateway may deliver the body base64-encoded, for instance when a binary media type is configured. */
    private static String body(APIGatewayProxyRequestEvent event) {
        String body = event.getBody();
        if (body == null) {
            return null;
        }
        if (Boolean.TRUE.equals(event.getIsBase64Encoded())) {
            return new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
        }
        return body;
    }

    /** Absolute-path URL of the created order, including the stage when the event reports one. */
    private static String location(APIGatewayProxyRequestEvent event, String orderId) {
        String basePath = Routes.ORDERS;
        if (event.getRequestContext() != null && event.getRequestContext().getPath() != null
                && !event.getRequestContext().getPath().isBlank()) {
            basePath = event.getRequestContext().getPath();
        } else if (event.getPath() != null && !event.getPath().isBlank()) {
            basePath = event.getPath();
        }
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        return basePath + "/" + orderId;
    }

    private static void log(Context context, String message) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log(message);
        } else {
            System.err.println(message);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
