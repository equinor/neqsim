# Transient slug separator control example

This example wires a terrain-affected transient flowline to an inlet separator that is controlled by independent liquid-level and gas-pressure loops. The flowline uses a sinusoidal elevation profile to shed slugs into the separator while a choke on the inlet protects separator pressure and a pair of throttling valves, each driven by a transmitter/PID loop, work to hold the separator liquid level and gas outlet pressure near their set points. The sample program reports slug statistics plus the final liquid level and gas outlet pressure once the transient run finishes.

## Key setup

- The inlet stream represents a rich gas with water, flowing at 10 kg/s and 80 bara. It feeds a 1.5 km, 0.2 m diameter transient pipe split into 20 sections and a sinusoidal elevation dip that forces intermittent liquid holdup and slug formation before the pipe outlet. 【F:src/main/java/neqsim/process/controllerdevice/TransientSlugSeparatorControlExample.java†L55-L97】
- A choke valve sits between the flowline and separator and is driven by a pressure controller to hold the separator near 70 bara while absorbing slug-induced surges. Downstream of the separator, a gas outlet valve uses its own pressure controller on the export stream, and the liquid valve is driven by a level transmitter/PID pair targeting a 45% level. 【F:src/main/java/neqsim/process/controllerdevice/TransientSlugSeparatorControlExample.java†L99-L135】
- After steady-state initialization, the process advances 10 transient steps at 0.5 s per step to give the pipe time to generate slugs and let the controllers react. The example returns the slug tracker summary string along with the separator level and gas outlet pressure observed at the end of these steps. 【F:src/main/java/neqsim/process/controllerdevice/TransientSlugSeparatorControlExample.java†L120-L138】

## What happens to level and pressure

- The flowline’s sinusoidal dip causes alternating liquid accumulation and blowdown, so slug pockets intermittently hit the separator. As those pockets arrive, the level controller opens the liquid valve to drain the separator and then trims back toward the 45% target once the surge passes, keeping the level bounded around the set point rather than drifting. 【F:src/main/java/neqsim/process/controllerdevice/TransientSlugSeparatorControlExample.java†L78-L138】
- Gas-side disturbances from slug arrival are handled first by the inlet choke, whose pressure controller clips separator pressure excursions near 70 bara, and then by the export valve, which throttles based on the downstream stream pressure target. The reported gas outlet pressure at the end of the run shows how the combined control brings the system back toward the set points after slug-induced swings. 【F:src/main/java/neqsim/process/controllerdevice/TransientSlugSeparatorControlExample.java†L99-L138】

## Plotting the default run

The example records the separator liquid level and gas outlet pressure on every 0.5 s time step, and the `--series` flag prints those values as CSV for plotting. Over the ten-step transient, the separator level jumps toward 0.9 when the first slug reaches the vessel and stays elevated while the controller holds the outlet valve open, while the gas pressure peaks above 120 bara before decaying toward the 70 bara set point as the gas valve throttles. 【F:src/main/java/neqsim/process/controllerdevice/TransientSlugSeparatorControlExample.java†L22-L138】

## How to run it

Execute `TransientSlugSeparatorControlExample.main` to print the slug statistics, separator liquid level, and gas outlet pressure for the default 5 s transient run (10 steps × 0.5 s). Use the `--series` flag to emit comma-separated time, level, and gas pressure for plotting (as used to build the graph above). The accompanying JUnit test `TransientSlugSeparatorControlExampleTest` simply calls `runSimulation()` to verify the example produces populated slug statistics and non-zero separator conditions.
