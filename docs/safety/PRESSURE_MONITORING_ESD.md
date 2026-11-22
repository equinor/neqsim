---
title: Pressure Monitoring in ESD Blowdown Systems
description: Source-backed workflow for monitoring separator pressure, blowdown-valve response, and flare load during an ESD transient simulation.
---

## Purpose and limits

Use this workflow to verify the simulated sequence from emergency shutdown (ESD) activation to separator depressurization and flare loading. It is intended for model diagnostics and regression testing. It is not a substitute for relief-system sizing, an API 521 study, a HAZOP/LOPA, an SRS, or accountable engineering approval.

The maintained implementation evidence is:

- [ESDBlowdownSystemExample.java](../../src/test/java/neqsim/process/util/example/ESDBlowdownSystemExample.java), an executable end-to-end example;
- [BlowdownValveESDSystemTest.java](../../src/test/java/neqsim/process/equipment/valve/BlowdownValveESDSystemTest.java), including the focused `testPressureReliefViaBlowdown` regression.

The reported pressures, flows, valve positions, and flare loads depend on the fluid, inventory, equipment parameters, event timing, and time step. Run the current model to obtain results; do not treat example output or generic percentages as design criteria.

## Monitoring sequence

1. Run a steady state to establish the initial separator condition.
2. Switch the separator to dynamic calculation before advancing the transient.
3. At the ESD event, activate the blowdown valve, redirect the splitter to the blowdown path, and isolate or reduce the inlet as required by the scenario.
4. Advance state-owning equipment with `runTransient(...)`; run the remaining connected equipment in process order.
5. After each accepted time step, record separator pressure, blowdown-valve opening, blowdown flow, flare heat duty, and cumulative gas routed to the flare.
6. Assert the event and response independently: the pressure rises in the blocked-outlet phase, then falls after ESD; the valve activates; and gas reaches the flare.

The full example contains the initialization and declarations required by these excerpts. Its event transition uses:

```java
esdButton.push();
gasSplitter.setSplitFactors(new double[] {0.0, 1.0});
separator.setCalculateSteadyState(false);
```

Inside the transient loop, advance the separator dynamically and update cumulative flare quantities after the connected equipment has run:

```java
separator.runTransient(timeStep, java.util.UUID.randomUUID());
flare.run();
flare.updateCumulative(timeStep);

double currentPressure = separator.getGasOutStream().getPressure("bara");
minPressure = Math.min(minPressure, currentPressure);
maxPressure = Math.max(maxPressure, currentPressure);
pressureAtEnd = currentPressure;
```

Use parameterized Log4j2 messages for diagnostic output in Java examples and tests; do not write directly to the JVM standard-output streams.

## Quantities to trend

| Quantity | Current API | Interpretation |
| --- | --- | --- |
| Separator pressure | `separator.getGasOutStream().getPressure("bara")` | Pressure response of the protected inventory model |
| Blowdown-valve state | `bdValve.isActivated()` | Whether the ESD action activated the valve |
| Blowdown-valve opening | `bdValve.getPercentValveOpening()` | Simulated stroke progression, from 0 to 100 percent |
| Blowdown flow | `blowdownStream.getFlowRate("kg/hr")` | Flow selected into the blowdown path |
| Flare heat duty | `flare.getHeatDuty("MW")` | Instantaneous heat release calculated by the flare model |
| Cumulative gas | `flare.getCumulativeGasBurned("kg")` | Integrated gas quantity after `updateCumulative(...)` calls |

Keep units explicit at every API boundary. For pressure-relief assessment, compare the final pressure with the pressure captured at ESD activation, not only with the initial steady-state pressure.

## Verification contract

The focused JUnit scenario exercises a blocked outlet followed by ESD activation. It verifies all of the following:

- maximum pressure exceeds the initial pressure during the blockage phase;
- final pressure is lower than pressure at ESD activation;
- the blowdown valve is activated;
- cumulative gas sent to the flare is positive.

These are regression checks for this configured NeqSim scenario. A project study should add acceptance limits for response time, peak pressure, minimum temperature, inventory release, flare backpressure, and mass/energy closure, with assumptions and engineering ownership recorded.

## Run the maintained evidence

Run the end-to-end example from the repository root:

```bash
./mvnw -Dexec.classpathScope=test -Dexec.mainClass=neqsim.process.util.example.ESDBlowdownSystemExample exec:java
```

Run the focused pressure-relief regression:

```bash
./mvnw test -Dtest=BlowdownValveESDSystemTest#testPressureReliefViaBlowdown
```

To run every scenario in that test class:

```bash
./mvnw test -Dtest=BlowdownValveESDSystemTest
```

## Troubleshooting

- **Pressure does not fall after ESD:** confirm the separator is in dynamic mode, the inlet condition represents isolation, the splitter routes flow to blowdown, and the downstream pressure permits flow.
- **Valve remains closed:** confirm the push button targets the intended `BlowdownValve` and that the valve is advanced with `runTransient(...)`.
- **Cumulative flare gas remains zero:** run the complete valve/orifice/header/flare chain and call `flare.updateCumulative(timeStep)` after `flare.run()`.
- **Results change with time step:** perform a time-step sensitivity study before using the transient for an engineering decision.
- **Unexpected pressure source:** distinguish separator pressure from the pressure on a downstream stream or orifice outlet.

## Related documentation

- [Safety systems overview](README.md)
- [ESD testing workflow](esd_testing_workflow.md)
- [Integrated safety systems](INTEGRATED_SAFETY_SYSTEMS.md)
