# Distillation column algorithm

This document outlines how the `DistillationColumn` class in NeqSim solves a multistage equilibrium column.

## Algorithm overview

1. **Initialization**
   - Feed streams are attached to specific trays using `addFeedStream`.
   - The `init()` method estimates tray temperatures by running the feed tray and distributing temperatures linearly towards the condenser and reboiler.
   - Each tray is linked to its neighbouring trays so gas and liquid streams circulate.

2. **Pressure profile**
   - In `run(UUID id)` a linear pressure drop is imposed from the bottom tray to the top tray.

3. **Sequential substitution iterations**
   - Starting from the feed tray, trays are solved iteratively in two sweeps:
     - Upwards sweep: liquid from the tray above is sent down and trays below the feed are solved.
     - Downwards sweep: vapour from the tray below is sent up and trays above the feed are solved.
   - After each iteration the sum of absolute temperature changes on all trays is used as the convergence measure.
   - Iterations stop when the error falls below `1e-4` or the maximum number of iterations is reached.

   The class also supports a damped variant of the sequential substitution algorithm.
   The default `SolverType` is `DIRECT_SUBSTITUTION`, but you may activate the damped
   solver by calling `setSolverType(SolverType.DAMPED_SUBSTITUTION)` and optionally
   tune the relaxation factor via `setRelaxationFactor(double)`.

4. **Results**
   - Once converged the gas stream from the top tray and the liquid stream from the bottom tray are reported as product streams.

## Suggestions for improvement

- Use a more robust solver (e.g. Newton or Broyden method) for faster convergence on difficult systems.
- Allow non-linear pressure profiles or user specified pressure drops per tray.
- Expose options for enthalpy or mass balance tolerances.
