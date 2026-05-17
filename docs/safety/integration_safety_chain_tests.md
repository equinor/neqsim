---
title: Integrated HIPPS/ESD Safety Chain Tests
description: This repository now includes an integration test that links alarms, HIPPS isolation, and ESD
---

# Integrated HIPPS/ESD Safety Chain Tests

This repository now includes an integration test that links alarms, HIPPS isolation, and ESD
depressurization logic against dynamic equipment models during a transient upset. The goal is to
verify that layered safety functions respond coherently when feed pressure surges beyond
high-high limits.

## What the test covers

- **Alarm validation:** A separator pressure transmitter with HI/HIHI limits moves into alarm as the
  feed surge pushes pressure upward.
- **HIPPS isolation:** Three redundant pressure detectors (2oo3 voting) trip HIPPS logic that drives
  the `HIPPS Isolation Valve` closed in under two seconds.
- **ESD escalation:** If pressure remains high three seconds after HIPPS closure, HIPPS escalates to
  `ESD Level 1`, closing inlet valves, opening the blowdown valve, and routing gas to the flare.
- **Dynamic response:** The scenario runs as a transient simulation, confirming separator pressure
  falls while flare flow rises once ESD starts depressurizing.

## Running the integration test

Execute the JUnit test directly:

```bash
mvn -q -Dtest=IntegratedSafetyChainTransientTest test
```

The test lives at `src/test/java/neqsim/process/util/scenario/IntegratedSafetyChainTransientTest.java`
and uses `ProcessScenarioRunner` to coordinate logic execution with the process model.
