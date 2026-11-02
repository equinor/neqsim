# Conservative total energy in the existing optional Rusanov phase flux

Base: `5d0edbdf6f9e75a89f8f7337b4769d8cb24b2694`. The exact candidate source blob,
commands, local runtimes and qualification limits are retained in `summary.json` and
`validation.json`. This repairs the existing public local flux query; the production pipeline
continues to use its existing AUSM path. No Riemann solver, topology or transaction owner is added.

## Equation and root cause

For phase fraction alpha, density rho, velocity u, pressure p and total specific enthalpy H,
the conserved variables per pipe volume are

$$U=(\alpha\rho,\ \alpha\rho u,\ \alpha(\rho H-p)),$$

and the physical axial flux per pipe area is

$$F=(\alpha\rho u,\ \alpha\rho u^2+\alpha p,\ \alpha\rho uH).$$

H includes phase kinetic energy. The optional local Rusanov flux is

$$F^*=A\left[\frac{F_L+F_R}{2}-\frac{s}{2}(U_R-U_L)\right],\qquad s=\max(|u_L|+c_L,|u_R|+c_R).$$

A is pipe area [m2], c phase sound speed [m/s]. The returned mass, momentum and energy flows
are kg/s, N and W. Original source blob `00afdfdc8a3e32ca722c8fc9fc0b62db5569c9f7`
incorrectly used alpha*rho*H in the dissipative energy inventory. The physical advected enthalpy
flux was already correct. The fix subtracts alpha*p only from the conserved energy jump.

The Euler energy/enthalpy relation is independently described by
[Clawpack's Euler derivation](https://www.clawpack.org/riemann_book/html/Euler_approximate.html).
The local Lax/Rusanov conserved-state jump is given in
[Hakim's numerical-flux note](https://ammar-hakim.org/hancock-muscl.html).
These support the local conservation-law formula, not a new two-fluid pooling qualification.

## Executed pressure-jump regression

Gamma1.4 ideal gas; rho10kg/m3, velocity0, alpha0.5 on both sides, A1m2;
pL1MPa, pR2MPa, c computed from sqrt(gamma*p/rho), H=3.5*p/rho.
All inputs are declared ideal-gas states; this is a numerical-flux identity, not an equilibrium state.

| Energy flux | W |
| --- | ---: |
| Independent conserved-E reference | -330718913.88307387 |
| Original enthalpy-inventory result | -463006479.4363034 |
| Original error | -132287565.55322951 |
| Corrected result | -330718913.88307387 |

The original magnitude error is40%. This instantaneous numerical flux is not an actual heat
duty or a simulated pressure-jump duration. `PressureJump.java` and before/after CSVs retain
the standalone reproduction. The original source also fails the complete independent gate at row0.

## Frozen matrix and independent reference

`qualify.py` constructs384 fixed ideal-gas pairs over rho1/800kg/m3, p0.1/15MPa,
both velocity signs, two pipe areas and fractions1e-30/0.25/1.0. A further24 controls retain
positive traces in both states down to1e-30. Total: **408 complete ordered controls**.
The80-digit Decimal reference builds conserved energy directly as
alpha*(p/(gamma-1)+rho*u*u/2), independently of the implemented rho*H-p conversion.

Frozen acceptance for each flux component is3e-14 times the sum of magnitudes of its physical
fluxes and wave-speed-scaled conserved states. Holdup and pressure metadata use their corresponding
homogeneous scales; there is no absolute full-pipe allowance that could hide a trace result.
The native schema, all408 identities, complete row/column counts and every finite field are checked.
Exact-zero scale requires exact agreement. No Python assertion owns an acceptance gate.

Maximum observed normalized error: **2.63970e-16**. Full input/native/reference matrices are retained.
Normal and optimized Python reproduce identical numerical files. The default-AUSM408-row
output is byte-identical before/after the repair. No runtime solver-selection call was changed.

JUnit additionally checks exact absence with unused NaN extensions, positive traces, identical-state
Euler consistency, signed flow, total-enthalpy kinetic energy, negative reference-energy shifts,
finite guards/overflow, and the explicit Courant condition for an analytical mass-positivity control.
The original false unconditional-stability claim has been removed.

Run from the repository root after the work-with-neqsim Maven bootstrap:

```bash
python3 src/test/resources/neqsim/process/equipment/pipeline/rusanov-total-energy/qualify.py
python3 -O src/test/resources/neqsim/process/equipment/pipeline/rusanov-total-energy/qualify.py --output-dir target/rusanov-optimized
./mvnw -q -Dtest=RusanovConservativeFluxTest test
```

`--source` accepts an explicitly selected Java source for a baseline replay. Preserve its reported
blob; an arbitrary local source must not be labeled as master. The baseline native/control files
are retained separately. `plot.py` reproduces the following figure from the stored results.

![Independent energy-flux controls before and after repair](energy-flux.png)

## Executed SRK gas/oil follow-up

The same public six-component SRK/classic composition is flashed at18 fixed states:
260/277.15/290K and10/40/80/115/125/150bara. Adjacent pressures, including150→10bara,
are paired independently at each temperature. Each gas/oil query uses velocities(0,0),(-5,3),
(7,-2)m/s and phase-fraction multipliers1/1e-12/1e-30. These query fractions do not seed a
pipeline. EOS phase-volume fractions define the local query inputs. The full18-state grid
and all324 local-flux rows are retained in `srk-native.csv`,
including both densities, h/e, sound speed, all six phase mole fractions and native/reference fluxes.
Exactly absent oil has a zero evidence record and unused NaN query extensions.

The324 controls include54 one-sided oil appearance/disappearance rows and261 rows with
negative reference internal energy. Negative energy under an EOS reference is not by itself
an inadmissible state. `RusanovRichGasFluxTest` checks every query in CI and also exposes an
asserted CSV replay. `qualify_srk.py` independently evaluates every complete, ordered, finite
row with80-digit arithmetic and constructs energy from internal energy plus kinetic energy.
This is a numerical/API consistency reference, not independent experimental EOS validation.

### Explicit density and energy convention

The first test incorrectly combined `phase.getInternalEnergy("J/kg")` with
`phase.getDensity("kg/m3")`. At260K/10bara gas, h-e is117427.8565287262J/kg while
p/rhoPhysical is117373.8856055669J/kg. That control **failed**; its tolerance was not relaxed.
`Phase.getInternalEnergy` uses the untranslated EOS molar volume, whereas the density method
with a unit reads physical properties with the default Peneloux correction. The no-argument
`phase.getDensity()` uses that EOS molar volume. This is an API convention distinction,
not evidence that the local flux or EOS itself failed.

The reference now explicitly converts the internal-energy API value to the existing pipeline
mechanical-energy convention (also used in `ConservativeThermodynamicRecovery.evaluate`):

$$e_m=e_{EOS}+p\left(\frac{1}{\rho_{EOS}}-\frac{1}{\rho_{physical}}\right).$$

It then builds Uenergy=alpha*rhoPhysical*(em+u*u/2), without using the flux implementation's
rho*H-p conversion. Both h-eEOS=p/rhoEOS and the volume-work conversion are checked separately.
No volume correction, EOS guard, production density or energy definition is changed.

| Fixed gate/result | Value |
| --- | ---: |
| Normalized flux gate, set before execution |3e-14|
| Maximum normalized flux error |1.76198e-16|
| Normalized EOS/unit identity gate, set before execution |3e-13|
| Maximum normalized EOS/unit error |9.68095e-17|
| Maximum explicit volume-work correction |1516.5165J/kg|
| Measured asserted CSV replay wall time, shared runtime |1.67053s|

Normal/-O reference and summary files are byte-identical. Both Python modes also pass two
receipt tests that reject12 corrupted records per mode and remove stale success receipts.
The existing devtools workflow runs these controls on Python3.11/3.13; hosted results are pending.
Full explicit-source provenance,
failed-reference diagnosis, Java8 compilation/replay, negative controls and command exits
are recorded in `srk-validation.json`. Source is parentc2b9eac5fe85120c89703fa90f9044d06d6d588e
plus this test/evidence follow-up; the flux implementation remains blob61c647164d40187e07be29d5cdfe96112944f6fd.

![SRK sample volume convention and equilibrium heavy-component fractions](srk-inputs.png)

The figure shows input equilibrium states only, not spatial pools or time histories. Sampled oil
n-heptane mole fractions span0.20795–0.76619, versus the bulk feed0.004. These are equilibrium
phase compositions of the fixed bulk feed; they do not demonstrate the enrichment or depletion
history of retained pools. No evaporation coefficient or reference inventory was fitted.

Replay after Maven bootstrap and the focused Maven test compilation:

```bash
./mvnw -q -Dtest=RusanovRichGasFluxTest test
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/srk-classpath.txt
java -cp "target/test-classes:target/classes:$(cat target/srk-classpath.txt)" neqsim.process.equipment.pipeline.twophasepipe.numerics.RusanovRichGasFluxTest target/srk-native.csv
python3 src/test/resources/neqsim/process/equipment/pipeline/rusanov-total-energy/qualify_srk.py --native target/srk-native.csv --output-dir target/srk-check
```

The classpath separator above is for Linux/macOS. The Java main and Python audit are the executed
paths; no Maven/Java8 runtime execution is inferred from API compilation alone. Plot regeneration:
`python3 src/test/resources/neqsim/process/equipment/pipeline/rusanov-total-energy/plot_srk.py`.
The unchanged production AUSM path gains no work from this test-only increment. The18 flashes
are test/replay overhead, not measured pipeline calculation cost. Full-cycle limitations below remain.

## Public output and applicability

`interfaceHoldup` is the arithmetic mean fraction. `interfacePressure` is phase-area weighted,
so their product reproduces the centered pressure traction. `holdupFlux` is the corresponding
local Lax phase-fraction flux. Both absent phases give exact zero without reading unused fields.
Positive occupied density must be finite/positive; velocity, pressure, total enthalpy and sound
speed must be finite, with nonnegative sound speed. Area must be finite/positive. Unrepresentable
interface holdup and nonfinite computed outputs reject; tested traces extend to1e-30.
No state is clipped, seeded or thermodynamically recovered by this query. No serialized field or UID changes.

## Performance and remaining engineering gates

`FluxTiming.java` retains seven200000-query observations after100000 warmups, separately for
baseline/candidate AUSM and Rusanov in independent JVMs. Raw timing/checksum CSVs and observed
min/median/max are retained. Shared-runtime observations are not a whole-simulation speedup claim.
The normal AUSM implementation has no added arithmetic or branches, and its control data are unchanged.
The repaired optional query includes validation and output metadata; do not equate its cost with
ordinary pipeline calculation cost.

Rusanov's two-sided numerical diffusion does not satisfy the existing one-donor component ledger
merely by substituting a net mass flux. No automatic fallback or solver switch is enabled here.
Explicit time integration needs an appropriate Courant bound, admissible EOS states and independent
coupled positivity/energy/rollback evidence. A two-phase volume constraint, normal-gravity pressure
reconstruction and gravitational/internal/kinetic energy ownership still need a coherent integration.

The documented dry/wet unfunded pressure impulse therefore remains unresolved. No elapsed terrain
retention, pool level/inventory, gas-over-pool motion, same-feed inlet refill or physical evaporation
time is demonstrated by this repair. No thermodynamic-reference value was used to tune it.
#4674/#4675/#3298 remain open. This prerequisite stops at the existing local-flux API boundary.

## Final local validation

79 tests across14 classes pass, including7 new Rusanov tests; zero failures/errors/skips.
The original78-test result and unchanged pipeline control are retained in `validation.json`;
`srk-validation.json` records the combined selection and new SRK follow-up.
The unchanged public12/24-cell default-AUSM120s ramp completes with its original per-interval
mass/component/energy assertions (65.946266521s wall on the shared runtime). This is not an
enabled pressure-moment or retained-pool result.

Observed baseline/candidate median local-query times: AUSM31.42/44.02ns; Rusanov25.38/40.95ns.
Sample ranges overlap for AUSM; these shared-runtime measurements do not resolve a speed change.
The optional Rusanov query performs additional validation/metadata work. No Rusanov call was
added to ordinary calculations, and the default408-row numerical output/checksums are unchanged.

Maven helper, stable direct formatting, full Javadoc, documentation search828Markdown+1HTML,
source-generated inventory and all-file pre-commit/pre-push pass. LocalJava17.0.20/Python3.12.14;
Java8 API compilation passes, but no localJava8 JVM or full-repository suite is claimed.
`validation.json` retains command exits, provenance, complete timing samples and limits.
