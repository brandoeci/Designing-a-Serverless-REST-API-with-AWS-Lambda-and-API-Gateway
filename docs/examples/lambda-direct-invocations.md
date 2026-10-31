# Part II — invoking the Lambda directly, with no HTTP

How to reproduce, once the functions are deployed:

```bash
scripts/deploy-lambdas.sh
scripts/invoke-lambdas.sh          # runs every case below and saves each response under .deploy/
```

Or one case at a time:

```bash
aws lambda invoke \
  --function-name orders-get-order \
  --payload '{"id":"ORD-1001"}' \
  --cli-binary-format raw-in-base64-out \
  response.json && cat response.json
```

The outputs below were produced by running the packaged artifact
(`java -cp orders-api/target/orders-api.jar ...`) through the same handler classes and the same
Jackson mapping the AWS runtime performs, so they are real responses rather than illustrations. A
deployed function answers identically, except that generated ids differ: they are random by design
(see `UuidOrderIdGenerator`).

## GET — one order

| Input | Output |
| --- | --- |
| `{"id":"ORD-1001"}` | `{"id":"ORD-1001","customerId":"CUST-42","product":"MacBook Air","quantity":1}` |

A second, different input — the same function, an order that does not exist:

| Input | Lambda result |
| --- | --- |
| `{"id":"ORD-999"}` | **function error**: `{"errorMessage":"{\"error\":\"ORDER_NOT_FOUND\",\"message\":\"Order ORD-999 does not exist\"}","errorType":"com.eci.orders.adapter.lambda.custom.ApiError"}` |

This is what a `404` looks like *before* API Gateway is involved: Lambda has no notion of a status
code, so a mapped error is a failed invocation whose `errorMessage` carries the API's error
document. API Gateway turns it into `404` by matching `.*ORDER_NOT_FOUND.*`.

## POST — create an order

| Input | Output |
| --- | --- |
| `{"customerId":"CUST-42","product":"MacBook Air","quantity":1}` | `{"id":"ORD-106C66C0","customerId":"CUST-42","product":"MacBook Air","quantity":1}` |
| `{"customerId":"CUST-7","product":"Studio Display","quantity":3}` | `{"id":"ORD-73D84685","customerId":"CUST-7","product":"Studio Display","quantity":3}` |
| `{"customerId":"CUST-42","quantity":0}` | **function error**: `errorMessage = {"error":"VALIDATION_ERROR","message":"product is required and must not be blank; quantity must be greater than zero"}` |

Both violations are reported in one response, so a client fixes the whole payload in one round trip.

## GET — orders of a customer

| Input | Output |
| --- | --- |
| `{"customerId":"CUST-42"}` | `[{"id":"ORD-1001","customerId":"CUST-42","product":"MacBook Air","quantity":1},{"id":"ORD-1002","customerId":"CUST-42","product":"Magic Keyboard","quantity":2},{"id":"ORD-106C66C0","customerId":"CUST-42","product":"MacBook Air","quantity":1}]` |
| `{"customerId":"CUST-100"}` | `[]` |
| `{"customerId":null}` | **function error**: `errorMessage = {"error":"MISSING_QUERY_PARAMETER","message":"Query parameter customerId is required"}` |

`[]` is a success: the customer exists as a concept and simply has no orders.

The first row has **three** orders, not the two that were seeded: `ORD-106C66C0` is the order the
`POST` above created, and the same warm container served both calls. On a cold container, or when
another container answers, that third order is simply not there. That is ephemeral in-memory storage
behaving exactly as documented — and the reason `scripts/smoke-test.sh` treats "read back the order
I just created" as a warning rather than a failure.

## DELETE — remove an order

| Input | Output |
| --- | --- |
| `{"id":"ORD-1003"}` | `null` — the handler returns `Void`, and the runtime writes the literal `null` |
| `{"id":"ORD-999"}` | **function error**: `errorMessage = {"error":"ORDER_NOT_FOUND","message":"Order ORD-999 does not exist"}` |

That `null` is why the `204` integration response in Architecture A needs an **empty** response
template: a `204` must not carry a body, and without the empty template the client would receive the
four characters `null`. Architecture B has no such problem —
`HttpResponses.noContent()` simply sets no body at all.

## The proxy function, invoked without a gateway

```bash
aws lambda invoke \
  --function-name orders-api-proxy \
  --payload fileb://orders-api/src/test/resources/events/get-order.json \
  response.json && cat response.json
```

```json
{
  "statusCode": 200,
  "headers": { "Content-Type": "application/json; charset=utf-8" },
  "body": "{\"id\":\"ORD-1001\",\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}"
}
```

Same application, same data, but the payload in and out is now a whole HTTP exchange. Replaying
`delete-order.json` through the same function returns the shortest response in the project, and the
one that carries the most meaning:

```json
{ "statusCode": 204 }
```

No `headers`, no `body` — the adapter does not invent either.

---

## How does the Lambda Java runtime turn JSON into Java objects?

The investigation the workshop asks for, and what the behaviour above confirms.

**The code never parses JSON in Architecture A.** `GetOrderHandler` implements
`RequestHandler<OrderIdInput, OrderView>`. The managed Java runtime reads those two type arguments
by reflection and, around the handler method, does this:

1. The runtime receives the invocation payload as a byte stream.
2. It deserializes those bytes into the handler's **input type** with a bundled Jackson-based
   serializer (`aws-lambda-java-serialization`, used through
   `com.amazonaws.services.lambda.runtime.serialization.PojoSerializer`). The target type is
   discovered from the `RequestHandler<I, O>` type parameters, which is why a raw
   `RequestHandler` or a wildcard does not work.
3. It calls `handleRequest(input, context)`.
4. It serializes the **returned object** back to JSON and writes it to the response stream.
5. If the method throws, the runtime instead writes
   `{"errorMessage": ..., "errorType": ..., "stackTrace": [...]}` and marks the invocation as failed
   with the `X-Amz-Function-Error` header, which is exactly what API Gateway inspects.

Consequences that are visible in the tables above, and that drove design decisions in this project:

* **Bean conventions matter.** The input types (`OrderIdInput`, `CreateOrderInput`) and the output
  type (`OrderView`) are mutable classes with a public no-argument constructor and getters/setters,
  not records. The runtime's serializer is a dependency of the platform, not of this project, so its
  version is not under my control; beans are what every version of it understands. Inside the
  application, where *this* project controls Jackson 2.17, records are used freely (`Order`,
  `CreateOrderRequest`).
* **Unknown fields are ignored**, so a client sending an extra field does not break the function.
* **Missing fields become `null`**, which is why `quantity` is `Integer` and not `int`: with `int`,
  an absent quantity would silently arrive as `0` and the API would report a confusing
  "must be greater than zero" instead of "quantity is required".
* **`Context` is injected by the runtime**, not deserialized from the payload.
* In Architecture B the same machinery runs, but the input type is
  `APIGatewayProxyRequestEvent` from `aws-lambda-java-events`, a library of plain beans describing
  the gateway's event shape. The HTTP **body** is a `String` inside that event, so the adapter
  parses it with its own `ObjectMapper` — that second, explicit deserialization is the one this
  project owns and tests.
