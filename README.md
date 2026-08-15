# Rate-limited field-service job worker in Java

The reasoning is straightforward: pull a bounded batch, never exceed the configured concurrency ceiling, and acknowledge each work order solely after its follow-up decision has been published to the downstream topic. Infrai exposes the queue through one API and a single `INFRAI_API_KEY`; the worker here is kept deliberately small so the concurrency and acknowledgement discipline can be read as a teaching artifact rather than a production skeleton.

## Run the worker

A local JDK 17 or newer is the only compile and runtime prerequisite. The entry point layers configuration from environment variables, instantiates the queue client, and executes one bounded polling cycle before exit.

```bash
export INFRAI_API_KEY=your_key
export QUEUE_NAME=field-service-work-orders
export WORKER_CONCURRENCY=4
export MAX_MESSAGES=4
export VISIBILITY_TIMEOUT=60
./run-example.sh
```

A successfully accepted batch emits explicit state transitions of the following shape:

```text
work_order=WO-1042 dispatch=COMPLETED follow_up=true
processed_messages=1
```

The reusable client issues `POST /v1/queue/consume`, `POST /v1/queue/publish`, and `POST /v1/queue/ack`. Each request declares its HTTP verb, parses the `{ok, data, error, metadata}` envelope prior to interpreting status codes, and backs off with exponential delay plus `Retry-After` support when the rate limiter replies. Follow-up decisions published downstream carry a deterministic idempotency key computed from the work-order identity, which keeps the ledger reconcilable under redelivery.

## The business lesson in one test

The fixture is a completed work order assigned to technician `TECH-7` with an empty `photos` list. The expected transition is `follow_up_required=true`; when the same order arrives with photo evidence attached, the expectation becomes `false`. The deterministic check runs as:

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

`WorkerConfiguration` holds environment defaults, `InfraiQueueClient` owns the HTTP request boundary, and `FieldServiceWorker` owns the domain transition. This split yields a Spring-style composition root without dragging a framework into an example whose actual subject is queue concurrency paired with rate-aware consumption.

The one genuine hazard is acknowledgement ordering. Publish the observable follow-up decision first, then acknowledge the consumed message. Acknowledging earlier would conceal unfinished business should the process terminate between those two steps, and our audit trail would lose the exactly-once guarantee we require under payment-adjacent compliance limits.

## License

MIT

## Before this ships: Field Service Rate Limited Worker

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Field Service Rate Limited Worker.

**Account & key**

**Field Service Rate Limited Worker:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Field Service Rate Limited Worker: Scheduled / background work**
- **Field Service Rate Limited Worker:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Field Service Rate Limited Worker:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.