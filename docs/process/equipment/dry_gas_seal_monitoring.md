---
title: Dry Gas Seal Support-System Monitoring
description: Quality-aware timed advisory monitoring of seal supply, primary vent, buffer and bearing-side separation gas.
---

# Dry gas seal support-system monitoring

`DryGasSealMonitor` evaluates caller-normalized measurements for one tandem seal.
Use separate instances for each end of the compressor. It produces fault evidence
and simulated trip recommendations; it does not send commands or certify a protection function.

## Measurements, limits and interpretation

| Signal | Reference/basis | Observable concern |
|---|---|---|
| Supply minus process pressure | Both absolute bara; difference in bar | Insufficient pressure barrier, possible process ingress |
| Primary vent flow | Ideal NL/min at 273.15 K and 1.01325 bara | Excessive leakage or another vent contribution |
| Primary vent pressure | Absolute bara | Backpressure or restricted disposal path |
| Intermediate buffer gas flow | Same normal-volume basis | Insufficient buffer supply |
| Separation differential pressure | Separation pressure minus bearing cavity pressure, bar | Inadequate oil/process migration barrier |
| Gas-quality assessment | Explicit externally evaluated pass flag | Wet/contaminated gas or unresolved quality |
| Instrument quality and oldest sample age | Boolean quality, seconds | Missing, stale or physically invalid measurements |

Do not substitute supply-to-primary-vent differential pressure for supply-to-process
pressure, or confuse intermediate buffer gas with bearing-side separation gas. A negative
differential pressure is a physical barrier fault; negative absolute pressure or normal
flow is invalid measurement evidence. Root causes are hypotheses: a high vent-flow signal
alone does not uniquely diagnose a failed primary seal.

All `Limits` values are supplied by the caller/OEM. No universal leakage, pressure or
trip-delay thresholds are assumed. The primary vent maximum pressure must be positive;
all other limits must be finite and nonnegative. A zero confirmation delay requests an
immediate recommendation for an active physical fault.

## Timers and replay

`evaluate(sample, intervalSeconds)` represents one non-overlapping scan interval. Use a
finite positive interval in seconds. Physical faults must persist continuously for the
configured confirmation delay. Healthy scans reset the corresponding timer. Invalid
scans break continuous physical-fault evidence and immediately latch `INVALID_DATA`.
The caller therefore receives unresolved-data evidence rather than an invented healthy state.

Confirmed recommendations remain latched until `reset()`. Reset clears all timers and
recommendations; an ongoing fault reappears on the next scan. Reset is an explicit simulator
operation, not a plant reset permissive. Results are immutable detached snapshots, and
serialization preserves the monitor timers/latches for replay. Each collection getter
returns an unmodifiable defensive copy in fault declaration order. Retained collections
and serialized results remain unchanged by later scans or monitor resets.

This initial monitor has one common confirmation delay and a single running/pressurized
measurement basis. Mode-specific source/booster switching, alarm/trip threshold separation,
hysteresis, startup/standstill/coastdown logic, sensor voting and complete controller/SIS
integration remain acceptance items in [issue #4145](https://github.com/equinor/neqsim/issues/4145).

## Example (synthetic limits)

```java
DryGasSealMonitor.Limits limits =
    new DryGasSealMonitor.Limits(2, 100, 3, 10, 0.1, 2, 3);
DryGasSealMonitor monitor = new DryGasSealMonitor(limits);
DryGasSealMonitor.Sample sample =
    new DryGasSealMonitor.Sample(82, 80, 101, 2, 10, 0.1, 0, true, true);
DryGasSealMonitor.Result first = monitor.evaluate(sample, 2);
DryGasSealMonitor.Result confirmed = monitor.evaluate(sample, 1);
```

The example's high vent flow lasts three seconds and confirms a recommendation on the
second scan. The numbers illustrate behavior; use project-specific limits. This API
sequence is exercised by `DryGasSealMonitorTest.delayRecoveryLatchAndReset`.
The tests also inject simultaneous faults, bad/stale measurements, interrupted evidence,
recovery/reset and serialization/replay.

See the [program roadmap #4149](https://github.com/equinor/neqsim/issues/4149),
[EagleBurgmann seal management systems](https://www.eagleburgmann.com/en/products/seal-supply-systems/gas-supply-systems/seal-management-system-sms/)
and the [bearing-side separation barrier description](https://cobaseal.eagleburgmann.com/en/).
