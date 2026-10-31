# Reproducing the API Gateway configuration

Three ways to get the same result. The OpenAPI documents in this folder are the authoritative
definition; the console walkthrough exists because that is how the workshop is usually demonstrated.

| Option | Command | When to use |
| --- | --- | --- |
| Scripted | `scripts/deploy-api.sh` | fastest, idempotent, reproducible |
| Import | `aws apigateway import-rest-api --body fileb://openapi-proxy.yaml` | after substituting the placeholders |
| Console | the walkthrough below | to see every decision explicitly |

## 0. The Lambda functions

Both architectures run from the same JAR (`orders-api/target/orders-api.jar`); the handler string is
what selects the entry point.

| Function | Handler | Serves |
| --- | --- | --- |
| `orders-get-order` | `com.eci.orders.adapter.lambda.custom.GetOrderHandler::handleRequest` | A: `GET /orders/{id}` |
| `orders-create-order` | `com.eci.orders.adapter.lambda.custom.CreateOrderHandler::handleRequest` | A: `POST /orders` |
| `orders-find-orders-by-customer` | `com.eci.orders.adapter.lambda.custom.FindOrdersByCustomerHandler::handleRequest` | A: `GET /orders?customerId=` |
| `orders-delete-order` | `com.eci.orders.adapter.lambda.custom.DeleteOrderHandler::handleRequest` | A: `DELETE /orders/{id}` |
| `orders-api-proxy` | `com.eci.orders.adapter.lambda.proxy.OrdersApiHandler::handleRequest` | B: the whole API |

Runtime **Java 17**, memory **512 MB** (more memory means more CPU, which shortens a Java cold
start), timeout **15 s**, role **LabRole** in AWS Academy.

---

## Architecture A — custom (non-proxy) integration

Console → API Gateway → **Create API** → **REST API** → Build. Name: `orders-api-custom`.

### A.1 `GET /orders/{id}`

1. **Resources → Create resource**: parent `/`, resource name `orders`.
2. On `/orders` → **Create resource**: resource name `{id}` (the braces make it a path parameter).
3. On `/orders/{id}` → **Create method** → `GET`.
   * Integration type: **Lambda function**
   * **Lambda proxy integration: OFF** ← this is what makes it Architecture A
   * Function: `orders-get-order`
4. Open the method → **Integration request** → **Mapping templates**:
   * Request body passthrough: **When there are no templates defined**
   * Content type `application/json`, template = contents of
     [`mapping-templates/get-order-request.vtl`](mapping-templates/get-order-request.vtl)
5. **Method response** → add `400`, `404` and `500` next to the existing `200`.
6. **Integration response** → add one response per error, each with a *selection pattern* matched
   against the Lambda `errorMessage`:

   | Selection pattern | Status | Response template (`application/json`) |
   | --- | --- | --- |
   | *(empty — this is the default)* | `200` | *(none)* |
   | `.*ORDER_NOT_FOUND.*` | `404` | `$input.path('$.errorMessage')` |
   | `.*(VALIDATION_ERROR\|MISSING_QUERY_PARAMETER\|MALFORMED_REQUEST).*` | `400` | `$input.path('$.errorMessage')` |
   | `.*INTERNAL_ERROR.*` | `500` | contents of [`internal-error-response.vtl`](mapping-templates/internal-error-response.vtl) |

   The template for 400/404 is one line because
   [`ApiError`](../orders-api/src/main/java/com/eci/orders/adapter/lambda/custom/ApiError.java)
   makes the Lambda error message *be* the JSON error body of the API.

### A.2 `GET /orders?customerId=...`

1. On `/orders` → **Create method** → `GET`, Lambda proxy **OFF**, function
   `orders-find-orders-by-customer`.
2. **Integration request → Mapping templates**: `application/json` =
   [`find-orders-request.vtl`](mapping-templates/find-orders-request.vtl).
3. Same four method/integration responses as A.1.

Optionally add a **request validator** that requires `customerId`, so the gateway rejects the
request before paying for an invocation. This project does not: the `400` is produced by the Lambda
so that both architectures fail identically and the behaviour stays unit-testable. That trade-off is
discussed in the README.

### A.3 `POST /orders`

1. On `/orders` → **Create method** → `POST`, Lambda proxy **OFF**, function `orders-create-order`.
2. **Integration request → Mapping templates**: `application/json` =
   [`create-order-request.vtl`](mapping-templates/create-order-request.vtl).
3. **Method response**: delete `200`, add `201`, `400`, `404`, `500`.
4. **Integration response**: the default one → status **201**; the three error responses exactly as
   in A.1.

No `Location` header: an integration response parameter can copy
`integration.response.body.id`, but API Gateway mapping cannot concatenate `/orders/` with it. See
the note at the end of [`openapi-custom.yaml`](openapi-custom.yaml).

### A.4 `DELETE /orders/{id}`

1. On `/orders/{id}` → **Create method** → `DELETE`, Lambda proxy **OFF**, function
   `orders-delete-order`.
2. **Integration request → Mapping templates**: `application/json` =
   [`get-order-request.vtl`](mapping-templates/get-order-request.vtl) — the same template as the
   `GET`, because a delete needs exactly the same input.
3. **Method response**: delete `200`, add `204`, `400`, `404`, `500`.
4. **Integration response**: the default one → status **204** with an **empty** response template
   for `application/json`; the three error responses exactly as in A.1.

The empty template matters: the handler returns `Void`, which the runtime serializes as the literal
`null`, and a `204` must not carry a body.

### A.5 Deploy

**Deploy API** → stage `dev`. The invoke URL is
`https://{apiId}.execute-api.{region}.amazonaws.com/dev`.

---

## Architecture B — proxy integration

Create API → REST API. Name: `orders-api-proxy`.

1. Create the resources `/orders` and `/orders/{id}` exactly as above.
2. Create each method with **Lambda proxy integration: ON** and function `orders-api-proxy`:

   | Resource | Methods |
   | --- | --- |
   | `/orders` | `GET`, `POST` |
   | `/orders/{id}` | `GET`, `DELETE` |

3. That is the whole configuration. No mapping templates, no method responses, no integration
   responses, no selection patterns: with proxy integration API Gateway hands the Lambda the entire
   request and returns whatever the Lambda produced.
4. **Deploy API** → stage `dev`.

### Who answers an undeclared method?

Only the methods declared above reach the Lambda; `PUT /orders` gets API Gateway's own
`403 Missing Authentication Token`. The adapter implements `405` with an `Allow` header (and
`OrdersApiHandlerTest` proves it), but it can only answer if the gateway routes the request to it.
Creating an `ANY` method instead of the explicit ones moves that decision into Java. Both are
defensible; see the README.

---

## Granting API Gateway permission to invoke the functions

The console offers to add the permission automatically. From the CLI it is explicit:

```bash
aws lambda add-permission \
  --function-name orders-api-proxy \
  --statement-id apigw-invoke \
  --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:us-east-1:123456789012:abc123xyz/*/*/*"
```

Without it, every call fails with `500` and a CloudWatch log line about the gateway not being
allowed to invoke the function.

## Verifying

```bash
scripts/smoke-test.sh proxy    # 200, 201, 204, 400, 404
scripts/smoke-test.sh custom
```

Note how much of this page Architecture A needed and how little Architecture B did: adding one
operation cost a new function, a mapping template and eight response configurations in A, versus one
method in B.
