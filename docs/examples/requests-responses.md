# Example requests and responses

Every exchange below is the real behaviour of the handler code: the bodies and headers were
produced by running the packaged JAR locally (`mvn package`, then the handlers invoked with the
saved API Gateway events) and the same assertions run on every `mvn test`. After deploying, the
same cases are replayed against the live API by `scripts/smoke-test.sh`.

Set the base URL once:

```bash
BASE=https://abc123xyz.execute-api.us-east-1.amazonaws.com/dev
```

---

## 200 OK — retrieve an order

```bash
curl -i "$BASE/orders/ORD-1001"
```

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8

{"id":"ORD-1001","customerId":"CUST-42","product":"MacBook Air","quantity":1}
```

## 201 Created — create an order

```bash
curl -i -X POST "$BASE/orders" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"CUST-42","product":"MacBook Air","quantity":1}'
```

```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=utf-8
Location: /dev/orders/ORD-B2D04774

{"id":"ORD-B2D04774","customerId":"CUST-42","product":"MacBook Air","quantity":1}
```

The id is random, so yours will differ. Architecture A returns the same body and status but no
`Location` header — API Gateway mapping cannot build it.

## 200 OK — orders of a customer

```bash
curl -i "$BASE/orders?customerId=CUST-42"
```

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8

[{"id":"ORD-1001","customerId":"CUST-42","product":"MacBook Air","quantity":1},
 {"id":"ORD-1002","customerId":"CUST-42","product":"Magic Keyboard","quantity":2}]
```

## 200 OK — a customer with no orders

```bash
curl -i "$BASE/orders?customerId=CUST-100"
```

```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8

[]
```

Not a `404`: the collection exists, the filter simply matched nothing.

## 404 Not Found — the order does not exist

```bash
curl -i "$BASE/orders/ORD-999"
```

```http
HTTP/1.1 404 Not Found
Content-Type: application/json; charset=utf-8

{"error":"ORDER_NOT_FOUND","message":"Order ORD-999 does not exist"}
```

## 400 Bad Request — a business rule was broken

```bash
curl -i -X POST "$BASE/orders" \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"CUST-42","quantity":0}'
```

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json; charset=utf-8

{"error":"VALIDATION_ERROR","message":"product is required and must not be blank; quantity must be greater than zero"}
```

Both problems are reported at once.

## 400 Bad Request — the body is not JSON

```bash
curl -i -X POST "$BASE/orders" -H 'Content-Type: application/json' -d 'not json'
```

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json; charset=utf-8

{"error":"MALFORMED_REQUEST","message":"Request body is not valid JSON"}
```

## 400 Bad Request — the required query parameter is missing

```bash
curl -i "$BASE/orders"
```

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json; charset=utf-8

{"error":"MISSING_QUERY_PARAMETER","message":"Query parameter customerId is required"}
```

## 404 Not Found — no such resource

```bash
curl -i "$BASE/invoices"
```

In Architecture B, a path routed to the Lambda answers:

```http
HTTP/1.1 404 Not Found
Content-Type: application/json; charset=utf-8

{"error":"ROUTE_NOT_FOUND","message":"No resource matches GET /invoices"}
```

A path API Gateway does not know at all is rejected by the gateway itself with
`403 Missing Authentication Token` — the API never sees it.

## 500 Internal Server Error — something unexpected

Not reachable by sending a particular request; it is proved by a unit test that makes the store
fail (`OrdersApiHandlerTest.internalFailure`). The client gets:

```http
HTTP/1.1 500 Internal Server Error
Content-Type: application/json; charset=utf-8

{"error":"INTERNAL_ERROR","message":"An unexpected error occurred"}
```

The exception, including the message of the failing dependency, goes to CloudWatch Logs and nowhere
else.
