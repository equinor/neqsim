---
title: Laboratory gas-density consistency before EOS handoff
description: "Source-identified interval checks of gas pressure, temperature, Z, molar mass and density without fitting the EOS."
---

# Laboratory gas-density consistency before EOS handoff

`PvtGasDensityConsistency` checks whether reported gas-phase quantities can satisfy
the physical identity

$$\rho_g=\frac{pM_g}{Z_gRT}.$$

Here $p$ is **absolute pressure in Pa**, $T$ temperature in K, $M_g$ gas molar mass
in kg/mol, $Z_g$ dimensionless gas compressibility and $\rho_g$ gas mass density in
kg/m³. $R=8.31446261815324$ J/(mol K). All five measurements must refer to the
**same gas phase at the same state and on the same molar-volume basis**. Do not
combine bulk two-phase density with gas Z, use liquid molar mass, or substitute a
separator-gas composition for reservoir vapor. A volume-translated density also
requires a Z defined from that same volume.

This opt-in arithmetic check runs without a NeqSim flash or a fit. It helps identify
unit, transcription, phase-reference and source inconsistencies before calibration.
It cannot establish which reported quantity is wrong. A pass means internal
compatibility under the supplied bounds; it does not establish measured accuracy,
independent experiment provenance, a qualified EOS or a field-history match.

## Bounds and acceptance

Each immutable `Quantity(value, absoluteBound)` declares a closed interval. Inputs
and endpoints must be positive and finite; bounds must be finite and nonnegative.
Zero bounds specify point values. The caller must justify and freeze the bounds
before comparison. Printed-resolution half widths are different from instrument
uncertainty and from the measurement sigma used by `compareHoldout`.

The implied density interval is

$$\rho_{\min}=\frac{p_{\min}M_{\min}}{Z_{\max}RT_{\max}},\qquad
\rho_{\max}=\frac{p_{\max}M_{\max}}{Z_{\min}RT_{\min}}.$$

Arithmetic is rounded outward after each operation. A measured-density interval
passes if it overlaps this implied interval, including a shared endpoint. These
are conservative box bounds; joint correlations are not modeled. Overflow or
underflow that prevents a positive finite result is rejected explicitly. There is
no hidden relative-error tolerance, automatic widening, extrapolation, EOS tuning
or replacement of a reported value.

The evidence retains every supplied row, its whole-experiment identity, its state
reference, source/page/table/row, nominal values, bounds, implied central density,
implied bounds, observed-minus-implied density and compatibility. `requireCompatible()`
throws with the incompatible row identities. Do not silently remove failing rows
or relabel rows from a single depletion experiment as independent holdouts.

## Executable handoff example

The following analytic tuple has an implied density of 20 kg/m³. It illustrates
the API, independently of the methane EOS below. The tuple is synthetic and is
not a measured methane calibration target. The corresponding
[JUnit regression](../../src/test/java/neqsim/pvtsimulation/util/PvtGasDensityConsistencyTest.java)
executes these API calls and verifies both failure and wrong-sample rejection.

```java
import neqsim.pvtsimulation.util.CalibratedPvtModel;
import neqsim.pvtsimulation.util.PvtGasDensityConsistency;
import neqsim.pvtsimulation.util.PvtGasDensityConsistency.Observation;
import neqsim.pvtsimulation.util.PvtGasDensityConsistency.Quantity;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

Observation row = new Observation("row-1", "CCE-1", "same equilibrium gas phase",
    "synthetic analytic fixture", new Quantity(831446.261815324, 0),
    new Quantity(100, 0), new Quantity(1, 0), new Quantity(0.02, 0),
    new Quantity(20, 0));
PvtGasDensityConsistency evidence = new PvtGasDensityConsistency("synthetic sample",
    "explicit absolute bounds, not sigmas", "public synthetic data",
    new Observation[] {row});
evidence.requireCompatible();
String diagnostics = evidence.toJson();

SystemInterface original = new SystemSrkEos(300, 10);
original.addComponent("methane", 1);
original.setMixingRule("classic");
original.init(0);
CalibratedPvtModel model = new CalibratedPvtModel(original, original.clone(),
    "synthetic sample", "original v1", "tuned v1", "synthetic protocol",
    new String[] {"CCE-1"}, evidence);
String checkpointProvenance = model.getProvenance();
```

The overload checks the sample identity and requires **all supplied rows** to pass
before freezing the complete JSON under `gasDensityConsistency` in checkpoint
provenance. Serialization and held-out comparison provenance retain it. The existing
constructor, or a null evidence argument, retains the previous behavior and makes
no gas-density consistency claim. This gate is not automatically applied to existing
models or ordinary simulation calls. Both independently calibrated EOS checkpoints
remain unchanged; reservoir-volume misfit must never be used to retune them.

Continue with complete CCE/CVD/DLE/separator calibration and held-out experiments,
then the [pressure-path handoff](reservoir_material_balance.md) and
[produced-phase validation](reservoir_phase_production.md). Gas-density evidence
alone does not qualify those stages. Keep laboratory holdouts separate from
reservoir pressure, GOR and composition holdouts.

## Public primary report check

The [fixture and provenance](../../src/test/resources/neqsim/pvtsimulation/gas-density/README.md)
contain a small factual extract from Schlumberger DBR report **2012-10037190**,
Duvernay well 13-05-043-06W5, printed page 37/table 18, dated May 14, 2012. Raw
psia, g/mol and g/cc values are retained before explicit SI conversion. Printed
pages 3, 34 and 37 were checked visually against extracted text.

The frozen printing-resolution bounds are ±0.5 psia, ±0.5 K, ±0.0005 in Z,
±0.005 g/mol and ±0.0005 g/cc. They are **not** validated measurement uncertainty.
An independent standard-library Python calculation uses 50-digit decimal arithmetic
on the raw source units. Java results must agree within 1e-9 kg/m³; interval
overlap decisions must agree exactly. No EOS parameter is used by either calculation.

| Table-18 row | Implied gas density (g/cc) | Printed gas density (g/cc) | Result |
| --- | ---: | ---: | --- |
| Saturation, 4661 psia | 0.333688435 | 0.339 | Incompatible under printing bounds |
| Step 8, **1300 psia** | 0.075278163 | 0.075 | Compatible |
| All nine rows | Eight compatible, one incompatible | Raw values retained | Entire supplied set fails the gate |

The source's executive summary additionally reports saturation Z=0.992 while its
detailed CCE table reports 0.922. These conflicting source statements must be
resolved before claiming an accepted laboratory benchmark. Do not replace either
with a preferred value merely to improve a fit. A prior issue ledger transcribed
the final CVD pressure as 1250 psia; the verified source is **1300 psia**. The
fixture and tests explicitly protect that correction.

The public report is a data lead and consistency case, not an output-complete
laboratory-to-field validation chain. Its CVD protocol preserves gas removal and
cell-volume restoration; do not replace it with unrelated constant-composition
flashes. Its separator and standard bases also need explicit reconciliation before
use with reservoir outputs. Passing eight rows does not authorize discarding the
ninth, combining calibration/holdout rows, or inferring reservoir volumes/reserves.

## Reproduce and interpret

From the repository root:

```bash
python3 src/test/resources/neqsim/pvtsimulation/gas-density/audit_reference.py
./mvnw -Dtest=PvtGasDensityConsistencyTest test
```

The audit prints every nominal density, interval and decision. The test compares
all nine rows with the checked-in independent reference, covers analytic point and
closed-interval limits, all 16 endpoint corners, nonphysical/missing/duplicate inputs,
unrepresentable arithmetic, immutable serialization and checkpoint integration.
Its runtime probe logs three samples after 5000 warm-up tuples for tuple
construction/checking and one-row JSON export. Enable INFO for
`neqsim.pvtsimulation.util.PvtGasDensityConsistencyTest` to view these samples;
repository test logging is off by default. Wall-clock timing is evidence, not a
CI threshold or an EOS speedup claim.

The 2025 Tran rescaled-CVD/density method is a separate research acceptance item.
This identity check does not implement its equations, flow model or benchmark.
