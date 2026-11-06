# Independent coupled SRK tank–aquifer qualification

This optional synthetic benchmark qualifies existing `CompositionalReservoirHistory`
with `FetkovichAquifer`. It adds no runtime API or fast-suite workload. Run `run.py`
with the selected Python interpreter from an existing source checkout and JDK17+.
It builds the current APIs and POM-derived dependency classpath, compiles the driver
with `--release 8`, retains every endpoint in `target/coupled-aquifer-qualification`,
and runs `audit.py`. Bootstrap Maven as required by the development environment.

The independently coded explicit SRK pressure equation and RK4 ODE integrate
continuous constant-molar-rate production and segregated water support. Native replay
instead couples TV flashes with the existing exact prescribed-linear-pressure
interval response. Agreement therefore requires timestep refinement. No EOS parameter
is tuned to pressure history or reservoir volume. Original/tuned synthetic checkpoints
are identical and their serialized bytes must remain unchanged on every replay.
Production composition is prescribed pure methane; no produced-phase/GOR accuracy
or laboratory calibration/holdout claim is made.

`SCOPE.md` freezes inputs, units, assumptions, matrix and acceptance before comparing
outputs. Sixteen cases cover finite storage ratios .25 and 1, infinite storage,
productivity ratios .1 and 1, zero storage/productivity controls and production
fractions .2 and .5. Each uses 8/16/32/64 intervals: 64 replays and 1,920 endpoints.
Reference RK4 meshes 16,384/32,768 must agree within 1e-8 bara/m3. Finest native
pressure/water limits are .01 bara/.05 m3; supported-case 64/32 error ratios must
be <=.4 outside the 1e-7 absolute floor. Component and storage closure limits are
1e-6 mol and 1e-9 bara. Final pore-volume closure is also retained and checked at
1e-4 m3, matching the existing 1e-7 relative tank-volume criterion.

`reference.py` regenerates `reference.json`; `audit.py` checks the full native matrix.
`native.json`, `summary.json` and `validation.json` preserve outputs, runtime and
provenance. These are original public synthetic inputs under the repository license.
There are no standard/separator-volume conversions. Water remains segregated;
constant-property, isothermal, fixed-pore-volume assumptions apply.

An initial reference-volume bisection evaluated tiny molar volume by subtracting
from 1000 m3, leaving a 6.82e-8 bara initial pressure residual. It passed the frozen
bounds but was corrected to evaluate molar pressure directly. Physical inputs and
acceptance bounds were unchanged. The initial trial is retained separately; the
published primary reference enforces initial pressure closure below 1e-10 bara.

This is independent finite-EOS numerical qualification, not measured field accuracy,
parameter identification, Bayesian sensitivity, radial-transient/Carter–Tracy/VEH
comparison, water mixing, broader phase behavior or complete issue #4740 acceptance.
