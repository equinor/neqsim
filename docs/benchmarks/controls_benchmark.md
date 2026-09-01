---
title: "Canonical Controls Benchmark"
description: "Source-linked deterministic qualification of six NeqSim controller, protection, and actuator-coordination cases with transparent dynamic surrogate models."
---

The canonical controls benchmark gives NeqSim one reproducible CI suite for six
dynamic control capabilities: level, pressure, cascade temperature, split-range
capacity, anti-surge protection, and compressor speed/recycle coordination. It
uses the production controller and control-structure classes while keeping each
plant model deliberately small and inspectable.

This page reports a deterministic regression qualification, not an independent
plant or vendor benchmark. The Java implementation and executable test are the
authoritative sources for the equations, schedules, acceptance gates, and
published reference values.

Run the complete suite through the public API:

```java
import neqsim.process.controllerdevice.ControlsBenchmarkSuite;

ControlsBenchmarkSuite.Report report = ControlsBenchmarkSuite.runCanonicalSuite();
if (!report.isPassed()) {
  throw new IllegalStateException("Canonical controls benchmark failed");
}
ControlsBenchmarkSuite.CaseResult level = report.getCase("control_level_setpoint");
double levelIae = level.getMetrics().getIntegralAbsoluteError();
```

`Report.getCases()` returns the uniformly sampled time, process-value, set-point,
and controller-output vectors for every case. Each result also exposes
`ControllerPerformanceMetrics`, the final relative error, process-value range,
the case-specific acceptance detail, and the corresponding
`AgentBenchmarkSuite` verdict.

## Model and numerical contract

The suite uses a fixed time step of 1 s. Self-regulating cases apply the
transparent first-order balance

$$\tau\frac{dy}{dt}=y_{\mathrm{target}}(u,d)-y$$

and the level case applies the inventory balance

$$C\frac{dh}{dt}=q_{\mathrm{in}}-q_{\mathrm{out}}$$

Here, $t$ is time [s], $y$ is a case process value, $u$ is controller output [%],
$d$ is the declared disturbance, $y_{\mathrm{target}}$ is the resulting target,
and $\tau$ is the case time constant [s]. For the normalized level case, $h$ is
level [%], $C=25\ \mathrm{s}$, and $q_{\mathrm{in}}$ and $q_{\mathrm{out}}$ are
percent-scale surrogate flow signals. Process values and set points use
case-specific surrogate units; they are not field transmitter units.

The anti-surge case uses `AntiSurgeDynamicBenchmark`, including a
controller-disabled reference. All generic loops use proportional-only tuning so
their steady offsets, output limits, and challenge response remain easy to
audit.

| Stable ID | Case | Duration | Process-value/set-point view | Controller and challenge |
|-----------|------|----------|------------------------------|--------------------------|
| `control_level_setpoint` | Integrating level | 240 s | Normalized level [%] | Direct-acting Kp = 10; SP 50 -> 55% at 30 s; inflow 50 -> 55 at 130 s |
| `control_pressure_disturbance` | Pressure rejection | 220 s | Normalized pressure [%] | Reverse-acting Kp = 8; load +10 from 50-140 s and -5 thereafter |
| `control_cascade_temperature` | Cascade temperature | 260 s | Normalized temperature [%] | Primary Kp = 8, secondary Kp = 2; SP 50 -> 52% at 30 s; load -2 at 150 s |
| `control_split_range` | Sequential split range | 240 s | Normalized capacity response [%] | Reverse-acting Kp = 20; load -20 from 40-150 s and -10 thereafter |
| `control_anti_surge` | Anti-surge recycle | 120 s | Surge-margin fraction | Initial margin 0.30; erosion 0.020/s; full-recycle authority 0.060/s |
| `control_speed_recycle_coordination` | Speed/recycle coordination | 360 s | Normalized pressure [%] | Reverse-acting Kp = 6; SP 75%; load -12 then +18; protection demand 35% at 210 s |

## CI reference results

The following values are generated and locked by
`ControlsBenchmarkSuiteTest.publishedReferenceValuesRemainCurrent()`. IAE is
the trapezoidal integral of absolute error in each case's surrogate process-value
unit multiplied by seconds. It is useful for deterministic regression comparison
within one case, not comparison across unlike process variables.

| Case | Final relative error | IAE [case unit s] | PV range [case units] | Output [%] | Acceptance gate |
|------|----------------------|-------------------|-----------------------|------------|-----------------|
| Integrating level | 0.909% | 62.000 | 50.000-55.500 | 0.000-55.000 | Error <= 1%; non-negative level; bounded output |
| Pressure rejection | 1.111% | 143.093 | 49.444-51.111 | 41.111-54.444 | Error <= 1.5%; bounded output after both load steps |
| Cascade temperature | 1.215% | 114.233 | 50.000-51.684 | 50.000-82.000 | Error <= 2%; bounded inner-loop valve |
| Sequential split range | 0.952% | 147.880 | 49.000-50.000 | 50.000-70.000 | Error <= 2%; second final element exercised |
| Anti-surge recycle | 0.00488% | 2.333 | 0.0423-0.300 (fraction) | 0.000-37.481 | Positive closed-loop margin; disabled reference crosses surge |
| Speed/recycle coordination | 0.503% | 476.566 | 79.068-84.523 | 55.364-88.091 | Error <= 2%; speed, recycle, and protection ranges selected |

Every case must pass its physical checks and the dedicated `CONTROL` problems in
`AgentBenchmarkSuite.createControlsSuite()`. A numerical value alone cannot hide
a non-converged or physically failed case.

## Evidence and reproduction

The authoritative implementation is
[`ControlsBenchmarkSuite.java`](../../src/main/java/neqsim/process/controllerdevice/ControlsBenchmarkSuite.java).
The executable reference-value and acceptance contracts are in
[`ControlsBenchmarkSuiteTest.java`](../../src/test/java/neqsim/process/controllerdevice/ControlsBenchmarkSuiteTest.java)
and `AgentBenchmarkSuiteTest`.

Run them with:

```bash
./mvnw -q -DskipITs \
  -Dtest=ControlsBenchmarkSuiteTest,AgentBenchmarkSuiteTest test
```

## Qualification boundary

This benchmark qualifies deterministic NeqSim controller execution, control
structure selection, trace collection, KPI reporting, and source-linked
regression values. The plant equations are transparent qualification surrogates.
They are not field tuning, a vendor compressor map, severe-slugging validation,
a safety-instrumented function, operator training, or a commissioning study.
Engineering applications must replace the surrogate with qualified process
dynamics and validate tuning, valve authority, equipment limits, and protection
layers against project data.

See the [Benchmark Gallery](index.md) for independently referenced property
benchmarks and other evidence classes.
