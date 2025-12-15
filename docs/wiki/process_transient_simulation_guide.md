# Transient process simulation patterns from tests

Dynamic process behavior in NeqSim is validated through `ProcessSystemRunTransientTest`, which assembles streams, valves, separators, transmitters, and controllers before stepping a transient solver. Reusing the tested scaffolding ensures that custom flowsheets converge and maintain synchronized calculation identifiers across unit operations.

## Minimal transient loop with flow control

The first scenario creates a single feed stream, lets it down through a valve into a separator, and attaches a flow controller to the inlet valve based on a volume-flow transmitter.【F:src/test/java/neqsim/process/processmodel/ProcessSystemRunTransientTest.java†L58-L120】 Key steps reproduced below:

1. Build a thermodynamic system with SRK EOS and set a mixing rule.
2. Define a `Stream`, set mass flow and pressure, and connect it to a `ThrottlingValve` with a target outlet pressure.
3. Route the valve outlet to a `Separator`, configure geometry (diameter, length) and initial liquid level.
4. Add downstream valves for gas and liquid outlets to define back-pressure targets.
5. Attach a `VolumeFlowTransmitter` to the inlet stream, wire it to a `ControllerDeviceBaseClass`, and assign the controller to the inlet valve.
6. Run a steady-state initialization (`p.run()`), choose a transient timestep, and iterate `runTransient()` to observe controller action converging toward the setpoint (73.5 kg/hr in the test).

The assertions in the test check that every unit operation shares the same calculation identifier during the loop and that the transmitter stabilizes near the requested flow, confirming correct coupling of controller logic and transport equations.【F:src/test/java/neqsim/process/processmodel/ProcessSystemRunTransientTest.java†L106-L120】 Use this template when debugging control loops or valve responses in your own cases.

## Level- and pressure-controlled separator case

A second scenario adds a purge stream to the separator, introduces level and pressure transmitters, and binds each to a dedicated controller that manipulates the liquid and gas outlet valves respectively.【F:src/test/java/neqsim/process/processmodel/ProcessSystemRunTransientTest.java†L122-L232】 After a steady-state start, the process is marched forward with a 10-second timestep and the level transmitter reading is checked against the 0.45 m setpoint, demonstrating how controller gains drive the separator toward balanced holdup.

When replicating this pattern:

- Set transmitter bounds (`setMaximumValue`, `setMinimumValue`) to guard against unrealistic signals.
- Apply `setCalculateSteadyState(false)` on dynamic equipment to ensure the transient integrator, not a steady solver, advances the state.
- Verify calculation identifiers after each `runTransient()` to confirm that all equipment is synchronized before trusting control trajectories.

## Why calculation identifiers matter

Throughout the transient loops the test asserts that each unit operation's `getCalculationIdentifier()` matches the process system's identifier.【F:src/test/java/neqsim/process/processmodel/ProcessSystemRunTransientTest.java†L114-L118】【F:src/test/java/neqsim/process/processmodel/ProcessSystemRunTransientTest.java†L221-L229】 This guards against stale states or partial updates when complex equipment is added. If you see divergence, reinitialize the process system or inspect units for disabled steady-state flags.
