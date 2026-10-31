# Architecture in detail

The final diagram is in [`../README.md`](../README.md). This document holds the per-architecture
detail, the responsibility matrix and the dependency rules that keep the application independent
from AWS.

## Package dependency rule

```mermaid
graph TD
    subgraph aws["Knows about AWS / HTTP"]
        CUSTOM["adapter.lambda.custom<br/>4 handlers, ApiError,<br/>CustomErrorMapper, input DTOs"]
        PROXY["adapter.lambda.proxy<br/>OrdersApiHandler, Routes, HttpResponses"]
        DTO["adapter.dto / adapter.json<br/>OrderView, ErrorBody, ErrorCode, Json"]
    end

    subgraph plain["Ordinary Java"]
        APP["application<br/>OrderService, CreateOrderRequest,<br/>OrderRepository, CreateOrderValidator"]
        DOMAIN["domain<br/>Order, OrderNotFoundException,<br/>InvalidOrderException"]
        INFRA["infrastructure<br/>InMemoryOrderRepository,<br/>UuidOrderIdGenerator, ApplicationFactory"]
    end

    CUSTOM --> DTO
    PROXY --> DTO
    CUSTOM --> APP
    PROXY --> APP
    CUSTOM --> INFRA
    PROXY --> INFRA
    DTO --> DOMAIN
    APP --> DOMAIN
    INFRA --> APP
    INFRA --> DOMAIN

    classDef awsStyle fill:#fff4e5,stroke:#d97706
    classDef plainStyle fill:#eef7ee,stroke:#2f7a33
    class CUSTOM,PROXY,DTO awsStyle
    class APP,DOMAIN,INFRA plainStyle
```

Every arrow points inwards. `application` and `domain` import nothing from `com.amazonaws`,
`com.fasterxml` or `adapter`, which is checked the hard way: removing both adapter packages leaves a
project that still compiles and whose tests still pass.

| Package | Imports AWS types? | Imports Jackson? | Tested without AWS? |
| --- | --- | --- | --- |
| `domain` | no | no | yes |
| `application` | no | no | yes |
| `infrastructure` | no | no | yes |
| `adapter.dto` | no | no | yes |
| `adapter.json` | no | yes | yes |
| `adapter.lambda.custom` | yes (`RequestHandler`, `Context`) | indirectly | yes |
| `adapter.lambda.proxy` | yes (+ proxy event types) | yes | yes |

## Architecture A — API Gateway transformation

```mermaid
sequenceDiagram
    actor Client
    participant GW as API Gateway<br/>(custom integration)
    participant L as Lambda<br/>GetOrderHandler
    participant App as OrderService<br/>(ordinary Java)

    Client->>GW: GET /orders/ORD-999
    Note over GW: routing<br/>extract {id}<br/>apply request mapping template
    GW->>L: {"id":"ORD-999"}
    Note over L: runtime deserializes to OrderIdInput
    L->>App: getOrder("ORD-999")
    App--xL: OrderNotFoundException
    Note over L: CustomErrorMapper -> ApiError<br/>message = the API error document
    L--xGW: errorMessage = {"error":"ORDER_NOT_FOUND",...}
    Note over GW: match .*ORDER_NOT_FOUND.*<br/>-> status 404<br/>apply response template
    GW->>Client: 404 {"error":"ORDER_NOT_FOUND","message":"..."}
```

Four Lambda functions for four operations, three request mapping templates (the `GET` one is reused
by the `DELETE`), two response templates, and four method responses plus four integration responses
*per method* — 35 pieces of gateway configuration in total.

## Architecture B — proxy integration

```mermaid
sequenceDiagram
    actor Client
    participant GW as API Gateway<br/>(proxy integration)
    participant H as OrdersApiHandler<br/>(HTTP adapter)
    participant App as OrderService<br/>(ordinary Java)

    Client->>GW: GET /orders/ORD-999
    Note over GW: routing only
    GW->>H: APIGatewayProxyRequestEvent<br/>(method, path, params, headers, body)
    Note over H: Routes: resource + id<br/>no transformation configured anywhere
    H->>App: getOrder("ORD-999")
    App--xH: OrderNotFoundException
    Note over H: HttpResponses.error(404, ORDER_NOT_FOUND)
    H->>GW: {"statusCode":404,"headers":{...},"body":"..."}
    Note over GW: passthrough
    GW->>Client: 404 {"error":"ORDER_NOT_FOUND","message":"..."}
```

One Lambda function, four methods declared in the gateway, and every HTTP decision in two small
Java files (`Routes`, `HttpResponses`) plus the router inside `OrdersApiHandler`.

## Responsibility matrix

| Responsibility | Architecture A | Architecture B |
| --- | --- | --- |
| Routing (URL + method → code) | API Gateway | API Gateway (resource) + `OrdersApiHandler.route()` (method) |
| Extract path parameter | API Gateway mapping template | `Routes.orderIdOf()` |
| Extract query parameter | API Gateway mapping template | `Routes.queryParameter()` |
| Read the request body | API Gateway mapping template | `Json.read()` in the adapter |
| JSON → Java | AWS runtime (into a DTO) | AWS runtime (into the event) + adapter (the body) |
| Java → JSON | AWS runtime | adapter (`Json.write`) |
| Status code on success | API Gateway method/integration response | `HttpResponses` |
| Status code on error | API Gateway selection patterns | `OrdersApiHandler` catch clauses |
| Error body | Lambda (`ApiError`) + response template | `HttpResponses.error()` |
| `Location` header | not possible without leaking URLs into the Lambda | `HttpResponses.created()` |
| `Allow` header on 405 | not implemented (gateway answers 403) | `HttpResponses.methodNotAllowed()` |
| Dropping the body of a 204 | API Gateway (empty response template) | `HttpResponses.noContent()` |
| Business validation | `OrderService` / `CreateOrderValidator` | same |
| Business rules and data | `OrderService`, `Order`, repository | same |

The last two rows are the point of the whole exercise: they are identical. Changing the integration
style changed the adapter and the gateway configuration, and nothing else.

## What the JAR contains

```
orders-api.jar (3.3 MB, shaded)
├── com/eci/orders/...                 29 application + adapter classes
├── com/amazonaws/services/lambda/...  aws-lambda-java-core, aws-lambda-java-events
├── com/fasterxml/jackson/...          jackson-core, jackson-databind, jackson-annotations
└── org/joda/time/...                  joda-time, pulled in transitively by the events library
```

Lambda does not resolve Maven dependencies, so everything the function needs at runtime has to be
inside the archive. That is the whole reason `maven-shade-plugin` is in the build. No web server, no
servlet container, no framework: the `main` entry point is provided by the AWS runtime, which calls
`handleRequest`.
