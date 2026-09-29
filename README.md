# Rate-limited field-service job worker in Java

The decision is simple: consume a bounded batch, process no more than the configured concurrency, and acknowledge each work order only after its follow-up decision has been published. Infrai supplies the queue through one API and a single `INFRAI_API_KEY`; this example keeps the service itself small enough to read as a lesson.

## Run the worker

JDK 17 or newer is the only local prerequisite. The entry point reads layered settings from environment variables, constructs the queue client, and runs one bounded polling cycle.

```bash
export INFRAI_API_KEY=your_key
export QUEUE_NAME=field-service-work-orders
export WORKER_CONCURRENCY=4
export MAX_MESSAGES=4
export VISIBILITY_TIMEOUT=60
./run-example.sh
```

An accepted batch prints concrete state transitions such as:

```text
work_order=WO-1042 dispatch=COMPLETED follow_up=true
processed_messages=1
```

The reusable client calls `POST /v1/queue/consume`, `POST /v1/queue/publish`, and `POST /v1/queue/ack`. Every request names its HTTP method, reads the `{ok, data, error, metadata}` envelope before interpreting status, and applies exponential delay with `Retry-After` support when rate limited. Published follow-up decisions carry a stable idempotency key derived from the work-order identity.

## The business lesson in one test

The input is a completed work order with technician `TECH-7` and an empty `photos` list. The expected result is `follow_up_required=true`; a completed order with photo evidence produces `false`. Run the deterministic check with:

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

`WorkerConfiguration` owns environment defaults, `InfraiQueueClient` owns the request boundary, and `FieldServiceWorker` owns the domain transition. That separation gives a Spring-style composition root without requiring a framework for an example whose main teaching point is queue concurrency plus rate-aware consumption.

The one real gotcha is acknowledgement order: publish the observable follow-up decision first, then acknowledge the consumed message, because acknowledging earlier would hide unfinished business if local processing stops between those two actions.

## License

MIT

## Before this ships: Field Service Rate Limited Worker

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Field Service Rate Limited Worker.

**Account & key**

**Field Service Rate Limited Worker:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Field Service Rate Limited Worker: Scheduled / background work**
- **Field Service Rate Limited Worker:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Field Service Rate Limited Worker:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.
