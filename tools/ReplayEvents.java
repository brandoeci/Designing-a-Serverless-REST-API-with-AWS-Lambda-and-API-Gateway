import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent.ProxyRequestContext;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.eci.orders.adapter.lambda.proxy.OrdersApiHandler;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Prints the HTTP outcome of every case the API can produce, by running the real proxy adapter
 * against the saved API Gateway events and a few hand-built requests.
 *
 * <p>It exists so that the examples in the documentation can be regenerated and checked by anyone,
 * without an AWS account and without a deployment:</p>
 *
 * <pre>{@code
 * cd orders-api && mvn -q -DskipTests package && cd ..
 * java -cp orders-api/target/orders-api.jar tools/ReplayEvents.java
 * }</pre>
 *
 * <p>This is a development tool, not part of the deployed artifact: it lives outside
 * {@code src/main}, so it is never packaged into the JAR that goes to Lambda. The same behaviour is
 * asserted automatically by {@code OrdersApiHandlerTest} and {@code ProxyEventFixturesTest}; this
 * only makes it readable at a glance.</p>
 */
public class ReplayEvents {

    private static final Path EVENTS = Path.of("orders-api/src/test/resources/events");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public static void main(String[] args) throws Exception {
        OrdersApiHandler api = new OrdersApiHandler();

        System.out.println("Saved API Gateway proxy events (" + EVENTS + "):");
        System.out.println();
        for (String fixture : new String[]{"post-orders.json", "get-order.json",
                                           "get-orders-by-customer.json", "delete-order.json"}) {
            Path file = EVENTS.resolve(fixture);
            APIGatewayProxyRequestEvent event =
                    MAPPER.readValue(Files.readString(file), APIGatewayProxyRequestEvent.class);
            report(fixture + "  (" + event.getHttpMethod() + " " + event.getPath() + ")",
                    api.handleRequest(event, null));
        }

        System.out.println("Error cases:");
        System.out.println();
        report("GET /orders/ORD-999            (order does not exist)",
                api.handleRequest(request("GET", "/orders/ORD-999", null, null), null));
        report("DELETE /orders/ORD-999         (order does not exist)",
                api.handleRequest(request("DELETE", "/orders/ORD-999", null, null), null));
        report("POST /orders {quantity:0}      (two rules broken at once)",
                api.handleRequest(request("POST", "/orders", null,
                        "{\"customerId\":\"CUST-42\",\"quantity\":0}"), null));
        report("POST /orders 'not json'        (body is not JSON)",
                api.handleRequest(request("POST", "/orders", null, "not json"), null));
        report("GET /orders                    (required parameter absent)",
                api.handleRequest(request("GET", "/orders", null, null), null));
        report("PUT /orders/ORD-1001           (resource exists, method does not)",
                api.handleRequest(request("PUT", "/orders/ORD-1001", null, null), null));
        report("GET /invoices                  (no such resource)",
                api.handleRequest(request("GET", "/invoices", null, null), null));
    }

    private static void report(String label, APIGatewayProxyResponseEvent response) {
        System.out.println("  " + label);
        StringBuilder line = new StringBuilder("    -> HTTP ").append(response.getStatusCode());
        Map<String, String> headers = response.getHeaders();
        if (headers != null && headers.containsKey("Location")) {
            line.append("   Location: ").append(headers.get("Location"));
        }
        if (headers != null && headers.containsKey("Allow")) {
            line.append("   Allow: ").append(headers.get("Allow"));
        }
        System.out.println(line);
        if (response.getBody() != null) {
            System.out.println("       " + abbreviate(response.getBody()));
        }
        System.out.println();
    }

    private static String abbreviate(String body) {
        return body.length() <= 120 ? body : body.substring(0, 117) + "...";
    }

    /** Builds the same event shape API Gateway would send, so no case needs a deployment. */
    private static APIGatewayProxyRequestEvent request(String method, String path,
                                                       String customerId, String body) {
        boolean singleOrder = path.startsWith("/orders/");

        ProxyRequestContext context = new ProxyRequestContext();
        context.setStage("dev");
        context.setPath("/dev" + path);

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod(method)
                .withResource(singleOrder ? "/orders/{id}" : path)
                .withPath(path)
                .withRequestContext(context)
                .withBody(body);

        if (singleOrder) {
            event.setPathParameters(Map.of("id", path.substring("/orders/".length())));
        }
        if (customerId != null) {
            event.setQueryStringParameters(Map.of("customerId", customerId));
        }
        return event;
    }
}
