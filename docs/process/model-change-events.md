---
title: "Model Change Events"
description: "Publish deterministic, versioned engineering-model change events with revision identity, evidence references, idempotency, integrity fingerprints, and durable replay."
keywords: "ModelChangeEvent, engineering model revision, change event, idempotency, event journal, impact analysis"
---

# Model change events

NeqSim exposes a versioned, transport-neutral event contract for publishing governed engineering-model revisions.
The contract is deliberately separate from the runtime `ProcessEventBus`: runtime alarms and simulation progress are
ephemeral operating events, while `ModelChangeEvent` identifies a durable change to a model revision.

`ModelChangeEvent.fromEngineeringGraphDiff(...)` converts a canonical `EngineeringGraphDiff` into a deterministic
event. Each event carries asset and model identity, base and target revisions, direct change subjects, downstream
impact hints, evidence references, an idempotency key, and a SHA-256 payload fingerprint.

```java
ModelChangeEvent event = ModelChangeEvent.fromEngineeringGraphDiff(
    oldGraph.compareTo(newGraph),
    "EVENT-2026-001",
    "WISTING-PROCESS:A:B",
    Instant.parse("2026-07-18T08:00:00Z"),
    "NEQSIM",
    "PROCESS-TEAM",
    "WISTING",
    "WISTING-PROCESS",
    "Design pressure basis updated");

publisher.publish(event);
```

Use `InMemoryModelChangeEventBus` for embedded adapters and tests. Use `ModelChangeEventJournal` when local durable
replay is needed. Kafka, MQTT, AMQP, cloud-event, and HTTP adapters implement `ModelChangeEventPublisher`; they do not
change the domain event.

Publication is idempotent. Reusing an idempotency key with the same fingerprint returns `DUPLICATE`; reusing it with a
different payload fails closed. Event delivery and integrity do not grant engineering approval.
