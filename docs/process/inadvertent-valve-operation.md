---
title: "Inadvertent Valve Operation (IVO)"
description: "Screen credible inadvertent valve operation scenarios per API 521 §4.4.13 and NORSOK P-002 §5.5. Classifies overpressure, blocked outlet, loss of relief path, reverse flow, and failure to isolate on demand for block, control, bypass, check, PSV isolation, ESD and blowdown valves."
---

# Inadvertent Valve Operation (IVO)

`InadvertentValveOperationAnalyzer` is a screening tool that diagnoses the
credible consequence of a valve being operated outside its intended state —
either spuriously opened or closed, or stuck on demand. It implements the
scenario taxonomy of **API 521 §4.4.13** (inadvertent opening / closing) and
**NORSOK P-002 §5.5**, and ranks severity against the API 521 §5.4.2
accumulation limits.

The analyser is deliberately conservative: it produces frequency-tagged,
severity-classified IVO scenarios and remediation recommendations
(HIPPS / SIL, double block-and-bleed, CSO / CSC, key-locked, dual check
valves, partial-stroke testing). It does **not** size relief loads — feed
the resulting scenario into `runRelief` or LOPA for that.

## Theory

The valve role and the failure mode together determine the credible
consequence:

| Role × Mode | Consequence | Severity driver |
|-------------|-------------|-----------------|
| BLOCK / CONTROL spurious close | Blocked outlet | Overpressure factor $p_1 / p_d$ |
| BYPASS spurious open into LP segment | Overpressure of LP side | $p_1 / p_{d,2}$ |
| PSV_ISOLATION closed | Loss of relief path | SAFETY_CRITICAL |
| ESD / BLOWDOWN stuck open | Failure to isolate / depressure on demand | SAFETY_CRITICAL |
| CHECK stuck open | Reverse flow | MAJOR |

Severity is classified against the API 521 §5.4.2 accumulation limits:

$$
\text{severity} =
\begin{cases}
\text{SAFETY\_CRITICAL} & p_1 / p_d > 1.21 \\
\text{MAJOR}            & p_1 / p_d > 1.10 \\
\text{MINOR}            & p_1 / p_d > 1.00 \\
\text{NONE}             & \text{otherwise}
\end{cases}
$$

where 1.10 is the maximum accumulation for a single-jeopardy non-fire
scenario and 1.21 is the fire / multiple-jeopardy limit.

Default initiating-event frequencies (OREDA / IOGP Report 434-9 screening
values):

- **Spurious open / close** of a power-actuated valve: $1.0 \times 10^{-1}$ /yr
- **Stuck on demand**: $1.0 \times 10^{-2}$ /demand

Both defaults can be overridden via `setFrequencyPerYear()`.

## Quick example

```java
SystemInterface fluid = new SystemSrkEos(298.15, 150.0);
fluid.addComponent("methane", 1.0);
fluid.setMixingRule("classic");

Stream feed = new Stream("feed", fluid);
feed.setFlowRate(1000.0, "kg/hr");
feed.setPressure(150.0, "bara");
feed.setTemperature(25.0, "C");
feed.run();

ThrottlingValve xv = new ThrottlingValve("XV-100", feed);
xv.setOutletPressure(50.0);
xv.run();

// Convenience method on ThrottlingValve
InadvertentValveOperationResult result = xv.analyseInadvertentOperation(
    InadvertentValveOperationResult.ValveRole.BLOCK,
    InadvertentValveOperationResult.IvoMode.SPURIOUS_CLOSE,
    100.0);   // downstream design pressure [bara]

System.out.println(result.toJson());
```

Typical output:

```json
{
  "valveName": "XV-100",
  "role": "BLOCK",
  "mode": "SPURIOUS_CLOSE",
  "severity": "SAFETY_CRITICAL",
  "frequencyPerYear": 0.1,
  "upstreamPressureBara": 150.0,
  "downstreamPressureBara": 50.0,
  "designPressureBara": 100.0,
  "overpressureFactor": 1.5,
  "blockedOutlet": true,
  "reverseFlowRisk": false,
  "lossOfReliefPath": false,
  "failureToIsolateOnDemand": false,
  "description": "Spurious closure of a block / control valve isolates ...",
  "recommendations": [
    "Verify PSV / HIPPS sized for blocked-outlet scenario per API 521 §4.4.13",
    "..."
  ]
}
```

## API surface

| Method | Purpose |
|--------|---------|
| `ThrottlingValve.analyseInadvertentOperation(role, mode, designPressureBara)` | Convenience wrapper |
| `new InadvertentValveOperationAnalyzer(valve).analyze()` | Run full analysis |
| `setRole(ValveRole)` | BLOCK / CONTROL / BYPASS / CHECK / PSV_ISOLATION / ESD / BLOWDOWN |
| `setMode(IvoMode)` | SPURIOUS_OPEN / SPURIOUS_CLOSE / STUCK_OPEN / STUCK_CLOSED / PARTIAL_STROKE |
| `setDesignPressure(p, "bara")` | Upstream segment design pressure (severity reference) |
| `setDownstreamDesignPressure(p, "bara")` | LP-side design pressure (for spurious-open / bypass) |
| `setUpstreamPressure(p, "bara")` | Override the upstream pressure (what-if) |
| `setFrequencyPerYear(double)` | Override the default initiating-event frequency |

## Result fields

`InadvertentValveOperationResult` exposes:

- `role`, `mode`, `severity` (NONE / MINOR / MAJOR / SAFETY_CRITICAL)
- `overpressureFactor` — $p_{\text{source}} / p_d$
- `frequencyPerYear` — initiating-event frequency (defaults applied)
- Boolean IVO flags: `blockedOutlet`, `reverseFlowRisk`,
  `lossOfReliefPath`, `failureToIsolateOnDemand`
- `description` — plain-language summary of the scenario
- `recommendations` — list of remediation actions (PSV / HIPPS, CSO / LO +
  Castell, dual check, partial-stroke testing, CSC, etc.)
- `toJson()` — Gson serialisation with `serializeSpecialFloatingPointValues`
  enabled so `NaN` defaults round-trip safely

## Scenario gallery

```java
// 1. PSV isolation valve closed → loss of relief path
v.analyseInadvertentOperation(ValveRole.PSV_ISOLATION, IvoMode.SPURIOUS_CLOSE, 100.0);
// severity = SAFETY_CRITICAL, lossOfReliefPath = true

// 2. ESD stuck open → failure to isolate on demand
v.analyseInadvertentOperation(ValveRole.ESD, IvoMode.STUCK_OPEN, 100.0);
// severity = SAFETY_CRITICAL, failureToIsolateOnDemand = true

// 3. Check valve stuck open → reverse flow risk
v.analyseInadvertentOperation(ValveRole.CHECK, IvoMode.STUCK_OPEN, 100.0);
// severity = MAJOR, reverseFlowRisk = true

// 4. Bypass spurious open into LP segment
new InadvertentValveOperationAnalyzer(v)
    .setRole(ValveRole.BYPASS)
    .setMode(IvoMode.SPURIOUS_OPEN)
    .setDesignPressure(150.0, "bara")
    .setDownstreamDesignPressure(50.0, "bara")
    .analyze();
// severity = SAFETY_CRITICAL, overpressureFactor = 3.0
```

## Python (Jupyter) usage

```python
IvoAnalyzer = ns.JClass(
    "neqsim.process.equipment.valve.InadvertentValveOperationAnalyzer")
IvoResult = ns.JClass(
    "neqsim.process.equipment.valve.InadvertentValveOperationResult")

result = valve.analyseInadvertentOperation(
    IvoResult.ValveRole.BLOCK,
    IvoResult.IvoMode.SPURIOUS_CLOSE,
    100.0)
print(result.toJson())
```

## When to use it

- During **HAZOP / LOPA** preparation, to enumerate credible IVO scenarios
  and assign frequencies before quantifying SIL.
- During **PSV philosophy reviews**, to flag block valves whose spurious
  closure exceeds the §5.4.2 accumulation limit and therefore require a
  relief or HIPPS layer.
- During **operability** reviews, to identify valves where a stuck or
  spurious mode would breach the design envelope.
- During **isolation philosophy** reviews, to confirm CSO / LO / Castell
  and double block-and-bleed are in place where the analyser flags them.

## Related

- [Choke Collapse Analysis](choke-collapse.md)
- [Valves](equipment/valves.md)
- [Valve Mechanical Design](ValveMechanicalDesign.md)
- API 521 §4.4.13, §5.4.2 — Inadvertent valve operation, accumulation limits
- NORSOK P-002 §5.5 — Process safety
- IEC 61511, IEC 61508 — Safety instrumented systems
- IOGP Report 434-9, OREDA — Initiating event frequencies
