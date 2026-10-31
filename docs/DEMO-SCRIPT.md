# Demonstration video — script

Section 21 of the workshop asks for a short video. Target length: **6–8 minutes**. Everything below
is a command to run or a screen to show, in order. Record with OBS, the Xbox Game Bar (`Win+G`) or
Zoom, and keep the terminal font large.

Before recording: refresh the AWS Academy credentials (`AWS Details → AWS CLI` into
`~/.aws/credentials`) and run `scripts/build.sh` once, so Maven's downloads do not appear in the
video.

While recording, take the six screenshots the README is still waiting for — they are the same
moments, so it costs nothing extra. Each one is marked **[shot NN]** below; save it over
`docs/img/NN-*.png` keeping the file name, and the README picks it up with no edit.

---

## 1. Running the tests locally — 60 s

```bash
cd orders-api
mvn test
```

Say while it runs: *63 tests, no AWS account, no network, no web server — the application layer does
not know that HTTP exists.*

Then show one test file so the claim is visible, for instance
`OrderServiceTest` (business rules) and the nested classes of `OrdersApiHandlerTest` (every status
code).

## 2. Packaging — 30 s

```bash
mvn package
ls -lh target/orders-api.jar
unzip -l target/orders-api.jar | head -20
```

Say: *a shaded JAR — our classes plus the Lambda and Jackson libraries, because Lambda does not
resolve Maven dependencies. 3.3 MB, no `main`, no server.*

## 3. Invoking a Lambda directly — 60 s

Show the Lambda console with the five functions and point out that they share one artifact and
differ only in the handler string. **[shot 10: `docs/img/10-lambda-console.png`]** Open
`orders-get-order` → Test, run it with `{"id":"ORD-1001"}` **[shot 11:
`docs/img/11-lambda-invoke.png`]**. Then, from the terminal:

```bash
aws lambda invoke --function-name orders-get-order \
  --payload '{"id":"ORD-1001"}' --cli-binary-format raw-in-base64-out out.json && cat out.json

aws lambda invoke --function-name orders-get-order \
  --payload '{"id":"ORD-999"}' --cli-binary-format raw-in-base64-out out.json && cat out.json
```

Say: *no HTTP anywhere. The second one is what a 404 looks like before API Gateway exists: a failed
invocation whose `errorMessage` carries our error document.*

Optionally run `scripts/invoke-lambdas.sh` to show all seven cases at once.

## 4. The deployed API — 3 min

Set the base URL once:

```bash
BASE=$(grep PROXY_BASE_URL .deploy/outputs.env | cut -d= -f2)
```

### A successful GET — 200

```bash
curl -i "$BASE/orders/ORD-1001"
```

### A successful POST — 201

```bash
curl -i -X POST "$BASE/orders" -H 'Content-Type: application/json' \
  -d '{"customerId":"CUST-42","product":"MacBook Air","quantity":1}'
```

Point at the `Location` header and say: *the client learns the id the server minted, and where the
new resource lives.*

### A search, including the empty result — 200

```bash
curl -i "$BASE/orders?customerId=CUST-42"
curl -i "$BASE/orders?customerId=CUST-100"
```

Say: *an unknown customer is `200 []`, not `404` — the collection exists, the filter matched
nothing.*

### A 404

```bash
curl -i "$BASE/orders/ORD-999"
```

### A 400

```bash
curl -i -X POST "$BASE/orders" -H 'Content-Type: application/json' \
  -d '{"customerId":"CUST-42","quantity":0}'
```

Say: *every violation is reported at once, so the client fixes the payload in one round trip.*

### The DELETE extension — 204 then 404

```bash
curl -i -X DELETE "$BASE/orders/ORD-1003"      # 204, no body
curl -i -X DELETE "$BASE/orders/ORD-999"       # 404
```

### Everything at once

```bash
scripts/smoke-test.sh proxy
```

**[shot 15: `docs/img/15-smoke-test.png`]** — the PASS lines for 200, 201, 204, 400 and 404.

## 5. Both architectures side by side — 90 s

Show the same two calls against the custom-integration API:

```bash
CUSTOM=$(grep CUSTOM_BASE_URL .deploy/outputs.env | cut -d= -f2)
curl -i "$CUSTOM/orders/ORD-1001"
curl -i "$CUSTOM/orders/ORD-999"
```

Then, in the API Gateway console for `orders-api-custom`, show:

* `GET /orders/{id}` → **Integration request** → the mapping template that turns the path into
  `{"id":"ORD-1001"}` **[shot 12: `docs/img/12-apigw-mapping.png`]**;
* **Integration response** → the `.*ORDER_NOT_FOUND.*` pattern that produces the `404`
  **[shot 13: `docs/img/13-apigw-selection-pattern.png`]**.

And in `orders-api-proxy`, show that the same method has **nothing** configured beyond "Lambda proxy
integration: ON" **[shot 14: `docs/img/14-apigw-proxy.png`]**.

Say: *identical responses. In one API the status code lives in a regular expression in the console;
in the other it lives in `HttpResponses.java`, where a unit test can reach it.*

## 6. The architecture diagram — 45 s

Open the README diagram and trace one request with the cursor: client → API Gateway → adapter →
`OrderService` → repository, and back. Point out the orange/green split — what depends on AWS and
what is ordinary Java — and say the sentence the whole workshop is about:

*The business logic is byte-for-byte identical in both architectures. Only the adapter and the
gateway configuration changed.*

---

## Checklist

- [ ] `mvn test` passing on camera
- [ ] `mvn package` and the JAR contents
- [ ] a direct Lambda invocation (success and mapped error)
- [ ] `200` GET on the deployed API
- [ ] `201` POST with the `Location` header
- [ ] `404`
- [ ] `400`
- [ ] `204` and `404` for `DELETE`
- [ ] both architectures answering identically
- [ ] the architecture diagram, explained
- [ ] the six screenshots saved over `docs/img/10-*.png` … `docs/img/15-*.png`
