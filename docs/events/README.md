# The API Gateway proxy event

Part V asks where each piece of the HTTP request appears in the event that API Gateway sends to a
Lambda with proxy integration.

The saved events live in
[`orders-api/src/test/resources/events/`](../../orders-api/src/test/resources/events/) rather than in
this folder, and that is on purpose: `ProxyEventFixturesTest` replays them through the real handler
on every `mvn test`. If the format in the repository ever stopped matching what the code reads, the
build would break instead of the documentation quietly going stale.

| File | Request it captures |
| --- | --- |
| `get-order.json` | `GET /orders/ORD-1001` |
| `post-orders.json` | `POST /orders` with a JSON body |
| `get-orders-by-customer.json` | `GET /orders?customerId=CUST-42` |

## Where everything is

| HTTP element | Field in the event | Example | Read by |
| --- | --- | --- | --- |
| Method | `httpMethod` | `"GET"` | `OrdersApiHandler.method()` |
| Path | `path` | `"/orders/ORD-1001"` | `Routes.resourceOf()` (fallback) |
| Resource template | `resource` | `"/orders/{id}"` | `Routes.resourceOf()` |
| Path parameters | `pathParameters` | `{ "id": "ORD-1001" }` | `Routes.orderIdOf()` |
| Query parameters | `queryStringParameters` | `{ "customerId": "CUST-42" }` | `Routes.queryParameter()` |
| Repeated query parameters | `multiValueQueryStringParameters` | `{ "customerId": ["CUST-42"] }` | not used |
| Headers | `headers`, `multiValueHeaders` | `{ "Content-Type": "application/json" }` | not used |
| Body | `body` (a **string**, not an object) | `"{\"customerId\":\"CUST-42\",...}"` | `OrdersApiHandler.body()` |
| Body encoding | `isBase64Encoded` | `false` | `OrdersApiHandler.body()` |
| Stage | `requestContext.stage` | `"dev"` | — |
| Full path with stage | `requestContext.path` | `"/dev/orders"` | `Location` header |
| Caller | `requestContext.identity.sourceIp` | `"181.49.23.17"` | — |
| Correlation id | `requestContext.requestId` | `"6f1d9a3e-..."` | useful in logs |

Three details are easy to get wrong:

1. **`body` is a string.** It is not parsed JSON. The adapter deserializes it itself, which is why
   a malformed body is a `400` the adapter produces rather than a gateway error.
2. **`queryStringParameters` is `null`, not empty, when there is no query string.** Treating that
   `null` as "no parameter" instead of dereferencing it is the difference between `400
   MISSING_QUERY_PARAMETER` and a `500`.
3. **`path` excludes the stage, `requestContext.path` includes it.** The `Location` header must use
   the one that is actually reachable, so the adapter prefers `requestContext.path`.

## The response side

With proxy integration the Lambda must return the whole response, and API Gateway passes it through
unchanged:

```json
{
  "statusCode": 201,
  "headers": {
    "Content-Type": "application/json; charset=utf-8",
    "Location": "/dev/orders/ORD-B2D04774"
  },
  "body": "{\"id\":\"ORD-B2D04774\",\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}",
  "isBase64Encoded": false
}
```

`body` is a string here too. If the handler returned a malformed document — or an exception escaped
it — API Gateway would answer `502 Bad Gateway`, which is why
[`OrdersApiHandler`](../../orders-api/src/main/java/com/eci/orders/adapter/lambda/proxy/OrdersApiHandler.java)
catches `RuntimeException` and turns it into a `500` of its own.
