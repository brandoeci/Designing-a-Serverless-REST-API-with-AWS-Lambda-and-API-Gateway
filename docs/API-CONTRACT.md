# API contract

Written before any Java code, as the workshop requires. The machine-readable version of this
document is [`infra/openapi-proxy.yaml`](../infra/openapi-proxy.yaml); both architectures implement
exactly this contract.

## Resources

| Resource | URL | What it is |
| --- | --- | --- |
| Order collection | `/orders` | The set of all orders. Accepts new members and can be filtered. |
| Order | `/orders/{id}` | One order, identified by the id the server assigned to it. |

There are only two. `customer` and `product` appear *inside* an order but are not resources of this
API: nothing here lets you create, read or delete a customer.

## Operations

### 1. Create an order

| | |
| --- | --- |
| Method / URL | `POST /orders` |
| Path parameters | — |
| Query parameters | — |
| Request body | `{ "customerId": "CUST-42", "product": "MacBook Air", "quantity": 1 }` |
| Success | `201 Created`, body = the created order, `Location: /dev/orders/ORD-B2D04774` |
| Errors | `400` (missing/blank field, `quantity <= 0`, body not JSON), `500` |

`POST` on the collection, not `PUT` on `/orders/{id}`, because the client does not know the
identifier: the server mints it. That also makes the operation non-idempotent, which is honest —
two identical POSTs create two orders.

### 2. Retrieve an order

| | |
| --- | --- |
| Method / URL | `GET /orders/{id}` |
| Path parameters | `id` — required |
| Request body | — |
| Success | `200 OK`, body = the order |
| Errors | `400` (blank id), `404` (no such order), `500` |

### 3. Find the orders of a customer

| | |
| --- | --- |
| Method / URL | `GET /orders?customerId={customerId}` |
| Query parameters | `customerId` — required |
| Request body | — |
| Success | `200 OK`, body = JSON array, possibly `[]` |
| Errors | `400` (parameter absent or blank), `500` |

An unknown customer returns `200 []`, never `404`. The resource being addressed is the collection,
and it exists; "this customer has no orders" is a valid answer, not a missing resource.

### 4. Delete an order

| | |
| --- | --- |
| Method / URL | `DELETE /orders/{id}` |
| Path parameters | `id` — required |
| Request body | — |
| Success | `204 No Content`, no body |
| Errors | `400` (blank id), `404` (no such order), `500` |

Added by the final challenge. Deliberately **not** idempotent: deleting an order that does not exist
returns `404` rather than silently succeeding, so a client learns it was wrong about the state of the
system. (Always answering `204` is the other defensible choice — quieter retries, hidden client
bugs.) The prediction written before implementing it is in
[`PREDICTION-DELETE.md`](PREDICTION-DELETE.md).

## Representations

An order on the wire:

```json
{
  "id": "ORD-1001",
  "customerId": "CUST-42",
  "product": "MacBook Air",
  "quantity": 1
}
```

Every error, from either architecture:

```json
{
  "error": "ORDER_NOT_FOUND",
  "message": "Order ORD-999 does not exist"
}
```

`error` is a stable code a client may branch on; `message` is for a human reading a log and may
change at any time. No stack traces, no Java class names, no SQL, no internal ids.

| `error` | Status | Meaning |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | A field or parameter broke a business rule. The message lists every violation at once. |
| `MISSING_QUERY_PARAMETER` | 400 | A required query parameter was absent. |
| `MALFORMED_REQUEST` | 400 | The body was absent or was not valid JSON. |
| `ORDER_NOT_FOUND` | 404 | No order has that identifier. |
| `ROUTE_NOT_FOUND` | 404 | No resource matches the path. |
| `METHOD_NOT_ALLOWED` | 405 | The resource exists but not with that method. Response carries `Allow`. |
| `INTERNAL_ERROR` | 500 | Anything unexpected. Details are logged, never returned. |

## Status codes used, and why

| Code | Used for | Why not something else |
| --- | --- | --- |
| `200 OK` | successful `GET` | — |
| `201 Created` | successful `POST` | `200` would not tell the client a new resource now exists, nor where. |
| `204 No Content` | successful `DELETE` | The deletion succeeded and nothing is left to return. `200 {}` would invent a body. |
| `400 Bad Request` | the client sent something the contract forbids | `422` is defensible but is not in the set the workshop asks for, and `400` is what every HTTP client already understands. |
| `404 Not Found` | the addressed order does not exist | `204` would wrongly suggest "it exists and is empty". |
| `405 Method Not Allowed` | known resource, wrong method | `404` would lie about the resource not existing. |
| `500 Internal Server Error` | a bug or an unavailable dependency | — |

## Design questions

The questions section 4 of the workshop asks to document (why `/orders/{id}`, why `customerId` as a
query parameter, what `POST` returns, what a missing order returns, what an error must contain) are
answered in [`../README.md`](../README.md#part-0--designing-the-api-before-writing-code), next to the
rest of the architectural reasoning.
