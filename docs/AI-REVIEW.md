# Criticising the AI assistant

Section 15 of the workshop allows an AI assistant and section 16 asks for a critical review of its
decisions. This project was built with Claude (Claude Code) used as a collaborator: it was asked to
propose an architecture and justify it *before* generating code, and several of its proposals were
rejected or modified.

The point of this document is not whether the generated code compiled. It is whether the
engineering decisions were right, and who is accountable for them. I am.

---

## Decision 1 — "One Lambda per operation" as the default

**What the assistant proposed.** Four functions: `orders-get-order`, `orders-create-order`,
`orders-find-orders-by-customer` and later `orders-delete-order`, each with a dedicated handler
class.

**Why it probably proposed it.** It is the shape the workshop implicitly suggests for Part IV ("take
one of your Java operations and deploy it"), it matches the single-responsibility reflex, and it is
the configuration that makes a non-proxy integration natural: one mapping template per function,
one function per method.

**Do I agree?** Partly. One function per operation is genuinely good for independent scaling,
least-privilege IAM per operation and small cold starts. But it was proposed as *the* answer when it
is really the answer **for Architecture A**. It also quietly multiplies deployment units: with 30
operations, 30 functions, 30 sets of mapping templates and 30 sets of integration responses.

**Alternative.** One function for the whole API (the "fat Lambda"), which is what proxy integration
invites, or an intermediate grouping (one function per resource: `/orders` and `/orders/{id}`).

**What I implemented and why.** Both, deliberately, because the workshop asks to compare them: four
single-purpose functions for Architecture A and one `OrdersApiHandler` for Architecture B — out of
the same JAR, differing only in the configured handler string. That made the comparison in the
README a measurement instead of an opinion. For a system that kept growing I would choose the
proxy-style single function per bounded context, and split only an operation that proves to need
different memory, timeout or concurrency.

---

## Decision 2 — Letting the service return the HTTP response

**What the assistant proposed.** An early sketch had `OrderService.getOrder` returning an
`APIGatewayProxyResponseEvent` (and, in another variant, a `Result` object carrying a status code),
on the grounds that it removes a layer of translation.

**Why it probably proposed it.** It is shorter. One class fewer, no exception-to-status mapping, and
the status code sits right next to the logic that knows what happened — which *feels* cohesive.

**Do I agree?** No, and this is the decision I pushed back on hardest. It inverts the dependency the
workshop is about: the application would import `com.amazonaws`, and every unit test would assert on
status codes instead of on behaviour. It also makes the application unusable from anywhere that is
not HTTP, and it would have made Architecture A impossible without a second, parallel service.

**Alternative.** Keep status codes out of the application and let the adapter map outcomes. The
mapping needs a vocabulary: domain exceptions (`OrderNotFoundException`, `InvalidOrderException`),
or a `sealed interface` result type, or `Optional`.

**What I implemented and why.** Domain exceptions, mapped in exactly one place per architecture
(`HttpResponses` / `CustomErrorMapper`). Exceptions over `Optional` because "this order does not
exist" needs to carry *which* id for the error message, and over a sealed result type because the
extra ceremony buys nothing here: both adapters want the same three outcomes. The proof that this
was the right call is that `OrderService` is used unchanged by four handlers, one proxy adapter and
63 tests.

---

## Decision 3 — Java records at the Lambda boundary

**What the assistant proposed.** Records everywhere, including the types the AWS runtime
deserializes into: `record OrderIdInput(String id)`, `record OrderView(...)`.

**Why it probably proposed it.** Java 17 is the target, records are the modern way to write a data
carrier, and Jackson 2.12+ supports them natively — so it is correct *for code whose Jackson version
you control*.

**Do I agree?** Not at the boundary. In Architecture A the serializer is **not** this project's
Jackson: it is the one bundled in the managed runtime, through
`com.amazonaws.services.lambda.runtime.serialization`. Its version is a platform detail outside my
control, and record support has not always been there. Betting the API's contract on it is an
avoidable risk for no gain.

**Alternative.** Records at the boundary plus a `jackson-module-parameter-names`-style guarantee, or
custom `RequestStreamHandler`s that parse the stream manually (full control, far more code).

**What I implemented and why.** A split by audience: mutable beans with a no-argument constructor
for the types the *runtime* maps (`OrderIdInput`, `CustomerQueryInput`, `CreateOrderInput`,
`OrderView`, `ErrorBody`), records for the types *this project* maps (`Order`,
`CreateOrderRequest`). It looks inconsistent until you notice that the line is exactly where control
over the serializer changes hands, which is documented in
[`lambda-direct-invocations.md`](examples/lambda-direct-invocations.md).

---

## Decision 4 — Where validation lives

**What the assistant proposed.** JSON Schema models plus an API Gateway request validator, so
invalid payloads are rejected before a Lambda invocation is paid for.

**Why it probably proposed it.** It is a real AWS best practice: cheaper, faster, and it keeps junk
out of the function. For `required` fields and types, it genuinely works.

**Do I agree?** As an optimisation, yes. As *the* home of validation, no — for three reasons: the
rule "quantity must be positive" is a business rule and must hold for every caller, not only for
HTTP ones; gateway validation cannot be unit-tested, so the behaviour would become invisible to
`mvn test`; and the two architectures would then produce different error documents for the same bad
input, which would wreck the comparison.

**Alternative.** Validate in both places, with the gateway as a cheap first filter and the
application as the authority — the standard "validate at the edge, enforce in the core" pattern.

**What I implemented and why.** `CreateOrderValidator` in the application layer, reporting every
violation at once, plus invariants in the `Order` constructor as a last line of defence. The gateway
validator is documented in [`../infra/API-GATEWAY-SETUP.md`](../infra/API-GATEWAY-SETUP.md) as an
optional addition rather than being enabled, so that both architectures fail identically and every
rule stays covered by a test. On a high-traffic API I would enable it too, accepting the duplication.

---

## Decision 5 — How Architecture A reports errors

**What the assistant proposed.** Always succeed, and return a document such as
`{"ok": false, "error": "ORDER_NOT_FOUND"}` with HTTP 200, because a non-proxy Lambda "cannot set a
status code".

**Why it probably proposed it.** It is the path of least resistance: no selection patterns, no
integration responses, nothing to configure. A lot of real-world APIs do exactly this.

**Do I agree?** No. It is the single worst suggestion I received, because it looks like it works.
`200 OK` for a missing resource breaks caches, monitoring, retries and every client library that
branches on the status code — and it makes the API lie.

**Alternative.** Throw, and let API Gateway map the failure (what I did); or keep the status code in
the response body and translate it with `integration.response.body.statusCode` plus a mapping on
every method (more configuration, same result).

**What I implemented and why.** `ApiError` makes the Lambda's `errorMessage` *be* the API's JSON
error document, and each API Gateway integration response selects a status code with a regex over
the error code. The handler stays free of status codes, the client gets a correct one, and both
architectures emit byte-identical error bodies. The cost is honest and documented: the error
contract is now split between a Java class and a regular expression in the gateway, and getting that
regex wrong is a bug that no unit test can catch. Along the way the assistant missed a related trap
that I had to add: an unmapped Lambda failure falls through to the *default* integration response,
which is the success one, so without a catch-all the client would receive `200 OK` with a stack
trace. `CustomErrorMapper` plus the `.*INTERNAL_ERROR.*` pattern closes that hole.

---

## Decision 6 — Sequential order ids

**What the assistant proposed.** `ORD-001`, `ORD-002`, ... from an `AtomicInteger`, matching the
example in the workshop statement.

**Why it probably proposed it.** The statement shows `ORD-001`, and a counter is the obvious
implementation of "next id".

**Do I agree?** No. It is wrong in exactly the way serverless punishes: Lambda runs an unknown
number of execution environments, each with its own memory, so two containers hand out the same
"next" number and two different orders end up sharing an id.

**Alternative.** A real sequence in a database, a DynamoDB atomic counter, or a random identifier.

**What I implemented and why.** `UuidOrderIdGenerator` (`ORD-` + 8 hex characters): collision-free
with no shared state, which is the only property a stateless function can rely on. Determinism is
still available where it matters — the generator is an injected port, and tests use a sequential
double so assertions can name `ORD-2001`. This is also why the seeded demo ids are `ORD-1001`+:
they cannot collide with generated ones.

---

## Pattern in the mistakes

Looking at the six together, the assistant was reliable on *mechanics* (the shape of a mapping
template, the signature of a handler, which Maven plugin builds a fat JAR) and unreliable on
*boundaries*. Every proposal I rejected made the same kind of error: it moved a decision to wherever
the code was shortest, not to wherever the decision belongs. Returning a gateway event from the
service, validating only at the edge, answering 200 for a missing order and counting ids in memory
are all locally reasonable and globally wrong.

That is a usable rule for working with a coding assistant: let it write the code, and reserve for
yourself the question the workshop keeps asking — *where should this responsibility live?* It is
also why I asked it to explain an architecture before producing any file, and why every non-obvious
decision in this repository is accompanied by the reason it was made.
