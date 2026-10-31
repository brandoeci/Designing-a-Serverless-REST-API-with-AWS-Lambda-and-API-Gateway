# Prediction — `DELETE /orders/{id}`

Written **before** implementing the final challenge and committed before the implementation, as
section 17 of the workshop requires. Check with `git log --oneline -- docs/PREDICTION-DELETE.md`:
this file's commit precedes the commit that adds the feature.

Expected behaviour:

```
DELETE /orders/ORD-1003   ->  204 No Content   (no body)
DELETE /orders/ORD-999    ->  404 Not Found    {"error":"ORDER_NOT_FOUND","message":"..."}
DELETE /orders/%20        ->  400 Bad Request  {"error":"VALIDATION_ERROR","message":"..."}
```

## Which Java classes should change?

| Class | Change | Why |
| --- | --- | --- |
| `OrderRepository` | add `boolean deleteById(String id)` | the port has to express removal; returning a boolean lets the caller know whether anything was there |
| `InMemoryOrderRepository` | implement it with `ConcurrentHashMap.remove` | — |
| `OrderService` | add `void deleteOrder(String id)` throwing `OrderNotFoundException` | one more use case, same contract style as `getOrder` |
| `OrdersApiHandler` | one `case "DELETE"` under `/orders/{id}`, and the `Allow` header for that resource becomes `GET, DELETE` | routing is this adapter's job |
| **new** `DeleteOrderHandler` | Architecture A needs one function per operation | — |

What should **not** change: `Order`, `CreateOrderRequest`, `CreateOrderValidator`,
`OrderIdGenerator`, `Json`, `ErrorBody`, `ApiError`, `CustomErrorMapper`, `HttpResponses` (it already
has `noContent()`), `OrderView`, and `ErrorCode` — `ORDER_NOT_FOUND` and `VALIDATION_ERROR` already
cover every outcome. `OrderIdInput` should be reusable as-is, since a delete needs exactly the same
input as a get.

Predicted total: 4 files modified, 1 file added, plus tests.

## Do you need a new Lambda function?

Different answer per architecture, which is itself the interesting part:

* **Architecture A: yes.** One function per operation is the whole premise, so a fifth function
  `orders-delete-order` with handler `...custom.DeleteOrderHandler::handleRequest`. Same JAR,
  new handler string.
* **Architecture B: no.** `orders-api-proxy` already receives every request; only its router grows
  by one branch. Nothing is redeployed except the code.

## Does the API Gateway configuration need to change?

* **Architecture A: substantially.** A new `DELETE` method on `/orders/{id}`, pointing at the new
  function, plus a request mapping template (the existing `get-order-request.vtl` can be reused
  verbatim), plus method responses `204`, `400`, `404`, `500`, plus the four integration responses
  with their selection patterns. The default integration response must be `204` **with an empty
  response template**, otherwise the function's `null` return would be written as the literal
  `null` in the body of a response that is supposed to have none.
* **Architecture B: one method.** `DELETE` on `/orders/{id}` with proxy integration on. No
  templates, no response configuration.

Both need a redeployment of the stage, and Architecture A also needs an `add-permission` for the new
function.

## Where should the deletion logic reside?

Split, along the same line as every other operation:

* **`InMemoryOrderRepository`** — *how* an order is removed from storage. It is the only class that
  should know a `ConcurrentHashMap` is involved.
* **`OrderService.deleteOrder`** — *whether* the deletion is legitimate: reject a blank id, and
  report that an unknown order cannot be deleted. This is also where a future rule such as
  "a shipped order cannot be deleted" would go.
* **Neither the handler nor API Gateway** — a mapping template must not decide what deletion means.

## Where should the decision to return 204 or 404 reside?

Two decisions that are easy to confuse:

1. **"Did the order exist?"** belongs to the application. `OrderService.deleteOrder` throws
   `OrderNotFoundException` when it did not. It must not return a boolean that the adapter
   interprets, because then two adapters could interpret it differently.
2. **"Which number does that outcome get?"** belongs to the adapter, and only to it:
   `HttpResponses.noContent()` for success, the existing `catch (OrderNotFoundException)` for the
   404 — which means Architecture B needs *no new error-mapping code at all*. In Architecture A the
   number lives in the gateway: `204` as the default integration response, `404` from the already
   written `.*ORDER_NOT_FOUND.*` pattern.

I expect this to be the clearest evidence for the comparison: the same feature needs one line of
Java and one gateway method in B, versus a new function and roughly thirty lines of configuration
in A.

## Which tests should be added?

| Test | Asserts |
| --- | --- |
| `InMemoryOrderRepositoryTest.deletes` | `deleteById` returns `true` and the order is gone |
| `InMemoryOrderRepositoryTest.deleteUnknown` | returns `false` and nothing else is removed |
| `OrderServiceTest.deletesExistingOrder` | after `deleteOrder`, `getOrder` throws `OrderNotFoundException` |
| `OrderServiceTest.failsDeletingUnknownOrder` | `OrderNotFoundException` with the right id |
| `OrderServiceTest.failsDeletingTwice` | the second delete also fails — deliberately not idempotent |
| `OrderServiceTest.rejectsBlankIdOnDelete` | `InvalidOrderException` |
| `OrderServiceTest.deleteDoesNotAffectOtherOrders` | the customer's other orders survive |
| `OrdersApiHandlerTest` 204 | status 204, **null body**, no `Content-Type` |
| `OrdersApiHandlerTest` 404 | status 404 with the `ORDER_NOT_FOUND` document |
| `OrdersApiHandlerTest` delete-then-get | 204 then 404, proving the order really went away |
| `OrdersApiHandlerTest` 405 | `Allow` on `/orders/{id}` is now `GET, DELETE`; `DELETE /orders` is still 405 |
| `CustomIntegrationHandlersTest` | `DeleteOrderHandler` returns nothing on success and throws `ApiError` with `ORDER_NOT_FOUND` otherwise |
| `ProxyEventFixturesTest` | a new saved event `delete-order.json` returns 204 |

Two existing tests should need updating, not just new ones: the `Allow`-header case for
`/orders/{id}` (now `GET, DELETE`) and the parameterised 405 case. If nothing existing breaks, I
have probably forgotten to wire the route.

## What I expect to get wrong

* The `Void` return type of `DeleteOrderHandler`. `RequestHandler<OrderIdInput, Void>` returning
  `null` is the shape I expect to need, but the Lambda runtime may serialize that as the string
  `null` rather than an empty payload — which only matters for Architecture A, where the empty
  response template has to compensate.
* Whether `405` for `/orders/{id}` keeps working once `DELETE` exists: the `Allow` string is built by
  hand, so it is exactly the kind of detail that silently goes stale.
