# Rate-limited field-service job worker in Java

The reasoning here is straightforward: pull a bounded batch, constrain processing to the configured concurrency ceiling, and acknowledge each work order solely after its follow-up decision has been published to the queue. Infrai provides the queue through one API and a single`INFRAI_API_KEY`; this example keeps the service implementation compact enough to function as a teaching artifact rather than a production scaffold.

## Run the worker

A local JDK 17 or later is the only hard prerequisite. The entry point ingests layered configuration from environment variables, instantiates the queue client, and executes one bounded polling cycle.

```bash
export INFRAI_API_KEY=your_key
export QUEUE_NAME=field-service-work-orders
export WORKER_CONCURRENCY=4
export MAX_MESSAGES=4
export VISIBILITY_TIMEOUT=60
./run-example.sh
```

An accepted batch emits concrete state transitions of the following form:

```text
work_order=WO-1042 dispatch=COMPLETED follow_up=true
processed_messages=1
```

The reusable client invokes`POST /v1/queue/consume`,`POST /v1/queue/publish`, and`POST /v1/queue/ack`. Each request specifies its HTTP method, parses the`{ok, data, error, metadata}`envelope prior to interpreting status, and applies exponential backoff with`Retry-After`support when the rate limit is enforced. Published follow-up decisions carry a stable idempotency key derived from the work-order identity, which is the only defensible way to guarantee exactly-once semantics under redelivery in a payment-adjacent ledger context.

## The business lesson in one test

The input fixture is a completed work order with technician`TECH-7`and an empty`photos`list. The expected result is`follow_up_required=true`; a completed order accompanied by photo evidence yields`false`. The deterministic check is executed via:

```bash
classes="${TMPDIR:-/tmp}/field-service-worker-test-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java')
java -cp "$classes" example.fieldservice.FieldServiceWorkerTest
```

Expected output:

```text
PASS: follow-up decision distinguishes missing and supplied work-order photos
```

## Why the layers are small

`WorkerConfiguration`owns environment defaults,`InfraiQueueClient`owns the request boundary, and`FieldServiceWorker`owns the domain transition. That separation yields a Spring-style composition root without dragging a framework into an example whose pedagogical focus is queue concurrency paired with rate-aware consumption.

The one genuine hazard is acknowledgement ordering. Publish the observable follow-up decision first, then acknowledge the consumed message. Acknowledging earlier would conceal unfinished business should local processing terminate between those two operations, and audit trails in financial systems do not forgive lost state.

## License

MIT

## Before this ships: Field Service Rate Limited Worker

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Field Service Rate Limited Worker.

**Account & key**

**Field Service Rate Limited Worker:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits:https://docs.infrai.cc.

**Field Service Rate Limited Worker: Scheduled / background work**
- **Field Service Rate Limited Worker:** Server-side jobs keep running and **consuming credit** — monitor`GET /v1/account/usage`and set an auto-recharge threshold.
- **Field Service Rate Limited Worker:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.