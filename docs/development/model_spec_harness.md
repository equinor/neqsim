---
title: Numerical Model Specification Harness
description: Executable numerical, unavailable-data and unsupported-model contracts for NeqSim CI.
---

# Numerical model specifications

The model-spec harness addresses [issue #3792](https://github.com/equinor/neqsim/issues/3792).
It drives real calculation paths and checks their published values. Merely constructing a
model or completing a calculation without throwing is not a passing numerical contract.
This is test infrastructure; it does not change thermodynamic equations, database values,
public signatures, or the existing absence conventions.

## Run the gate

From the repository root:

```bash
./mvnw test "-Dtest=ModelSpec*Test,ComponentCorrelationSpecTest" -Djacoco.skip=true
```

The Java 8 dependency profile can be selected with `-f pomJava8.xml`.
These tests also belong to the ordinary fast suite. The dedicated CI job runs Java 8
and Java 21, fails if tests are not discovered, and retains Surefire and
`target/model-spec/results.tsv` and `coverage.tsv` evidence. The job summary reports
partial coverage, unsupported contracts and temporary debt separately. Production component-data and test-resource
changes select the build/test workflow, including changes containing only CSV or TSV data.
`ModelSpecHarnessTest` protects those workflow entries and the focused test selection.
Branch-protection configuration remains an administrator policy; adding a CI job does
not itself make that job a required merge status.

## Catalog and fixtures

The UTF-8 tab-separated catalog is
`src/test/resources/neqsim/thermo/spec/cases.tsv`. Its first line is the schema version,
followed by an exact header. Blank rows, extra/missing columns and unknown schema versions
are errors. There is no reflection-based class or method execution.

Each row declares:

| Fields | Contract |
| --- | --- |
| ID, fixture, property | Stable case identity and a curated, typed adapter |
| Components | Ordered `name=amount` pairs separated by semicolons; positive amounts sum to one mole |
| Temperature, pressure | K and absolute bar, both finite and positive |
| Phase, component index | Explicit selection; no assumption that every model has the same phase layout |
| Mixing rule, operation | Exact driver configuration; unsupported combinations fail loading |
| Unit, basis | Property-specific output unit and molar basis |
| Outcome | `VALUE`, `UNAVAILABLE`, or `UNSUPPORTED`, chosen before calculation |
| Expected, absTol, relTol | Finite numerical reference and finite, nonnegative tolerances; absence uses `-` |
| minK, maxK | Declared test/reference temperature domain, not an inferred universal model validity range |
| Source, provenance, reason | Reference locator, evidence type and reuse provenance; exact absence reason |

Comparison is `abs(actual - expected) <= absTol + relTol * abs(expected)`.
Zero and negative values are valid for signed properties such as ideal enthalpy or
ln(gamma); gamma, fugacity coefficient, Z, group R and applicable saturation pressure must be positive.
NaN and infinity always fail a `VALUE` case.

An unavailable saturation case asserts its declared cause (missing correlation, ion,
or temperature above Tc), the availability query and the legacy NaN result. A NaN
from an otherwise supported case is **not** converted into success. The bare UNIQUAC
case requires `UnsupportedOperationException` with a UNIQUAC diagnostic. An unrelated
exception or newly returned placeholder does not satisfy this contract.

To add coverage, add or extend the typed adapter in `ModelSpecFixtures`, add sourced
rows, and update the reviewed required-ID set in `ModelSpecHarnessTest`. Every fixture
enum member must have executed catalog coverage. Deleting a required row fails the
gate rather than silently reducing the test count. Keep the corresponding detailed
regression tests; the catalog supplements them.

## Initial evidence and boundaries

The catalog has 104 cases across seven system drivers (SRK, PR, Wilson, NRTL,
classic UNIFAC, PSRK and UMR-PRU), direct SRK/PR/Wilson/NRTL phase adapters, a component saturation
adapter and an unsupported phase adapter. This is **not coverage of every NeqSim model or every property**. Campaign
milestone B owns sourced family qualification and remaining per-property coverage debt.
The inventory gate below now reconciles every concrete System and Phase type against
an explicit classification; discovery does not qualify their numerical behavior.

| Cases | Evidence and tolerance rationale |
| --- | --- |
| Acetone at 280, 298.15 and 320 K | NIST WebBook Antoine correlation, Ambrose et al. (1974), 259.16–507.60 K; same fitted correlation, 1e-9 relative implementation tolerance |
| i-Pentane at 290, 298.15 and 301 K | NIST WebBook Willingham et al. (1945), 289.44–301.74 K; independent correlation versus NeqSim DIPPR data, 1% comparison tolerance |
| Binary Wilson | Prescribed Lambda12=2, Lambda21=0.5, with mole fractions 0.2/0.8, 0.5/0.5 and 0.8/0.2; closed-form numerical fixtures, not experimental mixture validation |
| Binary NRTL | Published local-composition equation with prescribed alpha12=alpha21=0.3, D12=200 K and D21=-100 K; gamma and molar excess Gibbs energy at three compositions and 298.15/323.15 K for SystemNRTL and exact PhaseGENRTL entry points |
| UNIFAC, PSRK, UMR-PRU | Pure methanol gamma=1 reference identity and subgroup-15 R=1.4311 data regression; populated group contents and stored coefficients are read |
| SRK and PR | Low-pressure methane Z approaching unity and zero ideal enthalpy controls; independent pure-methane cubic-root and fugacity calculations at 280 K/10 bar, 300 K/30 bar and 320 K/50 bar for both System and exact phase entry points |
| Missing/unsupported | Hydrogen/nC20 correlation absence, Na+ inapplicability, supercritical methane and bare UNIQUAC rejection |

The cubic cases use the original published SRK/PR equations with the declared methane
Tc, Pc and acentric factor. They validate analytical implementation and state publication,
not experimental model accuracy, mixture behavior, liquid roots or near-critical behavior.
The low-pressure EOS and pure-component GE cases are intentionally limited controls.
Gamma=1 alone cannot
detect an always-one stub; nonideal Wilson values, group-content checks and the existing
binary UNIFAC regressions provide distinct checks. `ModelSpecStateTest` additionally
tests changed Wilson composition and binary UNIFAC/PSRK component-order and repeated
initialization behavior. UMR-PRU is driven through the actual HV mixing rule and its GE
phase; standalone UNIQUAC is not incorrectly classified as working because a UNIFAC
subclass works.

`ModelSpecStateTest` also reuses each cubic System while moving 280 K/10 bar ->
320 K/50 bar -> 300 K/30 bar -> the initial state, checks both Z and the stored
fugacity coefficient after every initialization, and verifies `H = U + PV` and
`G = H - TS` on one consistent extensive/molar basis. These identities accompany
independent numerical anchors; they are not accepted as accuracy evidence by themselves.

The NRTL fixtures independently reconstruct both activity coefficients from the
Renon-Prausnitz local-composition equation and verify `G^E = RT sum(x_i ln(gamma_i))`.
The production phase publishes gamma and fugacity, and repeated evaluation traverses
composition and temperature before returning to the initial state. Stored NRTL
ln(gamma) remains explicitly unqualified: [issue #3899](https://github.com/equinor/neqsim/issues/3899)
records that it stays at a stale zero after a nonideal gamma calculation. These
prescribed parameters test analytical implementation and state refresh; they are not
fitted methanol/water data and do not qualify NeqSim's database parameters. Wilson's
existing closed-form cases now also exercise the exact `PhaseGEWilson` entry point.

`ComponentCorrelationSpecTest` gives analytical pressure examples for pow10,
pow10KPa, exp, log, legacy DIPPR and Wagner dispatch. These synthetic coefficients
exercise existing label precedence and pressure units. This does not claim all forms'
derivatives or inverse operations are qualified; preserve the more detailed
`ComponentAntoineVaporPressureTest` and track remaining derivative/inverse coverage
under #3792. In particular, a pressure-only fixture must not imply availability of an
unimplemented derivative.

The pow10KPa derivative defect in [issue #3798](https://github.com/equinor/neqsim/issues/3798)
was repaired by [PR #3803](https://github.com/equinor/neqsim/pull/3803). Six catalog cases
now qualify its derivative and inverse at 260, 300 and 350 K using the existing
prescribed-coefficient fixture in `ComponentPow10KPaVaporPressureTest`. The analytic
reference is P = 2 * 10^(1 - 300/T) bar and dP/dT = P * ln(10) * 300/T^2 bar/K.
Inverse cases supply independently calculated pressure, not the production forward
answer. Nonzero E tests explicit-label precedence. These are implementation contracts,
not physical fits for i-pentane. Other correlation derivatives remain separate debt.

## Inventory-to-catalog gate

`inventory.tsv` classifies 131 concrete types at the initial snapshot: 68 implementations
of `SystemInterface`, 62 implementations of `PhaseInterface`, and the explicitly selected
`ComponentSrk` saturation-correlation entry point. All concrete subclasses in the two
system/phase packages are discovered from compiled project classes, including nested
classes. Abstract bases, interfaces, enums and helpers not implementing those interfaces
are excluded. Discovery uses `Class.forName(..., false, ...)`: it never runs a constructor,
static initializer or arbitrary calculation. Model driving remains the explicit switch
in `ModelSpecFixtures`; aliases/subclasses do not inherit catalog qualification.

The gate runs in the named CI job and the ordinary suite. The catalog runner itself
also checks reconciliation, so selecting only `ModelSpecTest` cannot bypass the inventory.
Run from the repository with compiled production classes; jar-only execution deliberately
fails instead of reporting an empty inventory.

| Classification | Meaning |
| --- | --- |
| `PARTIAL` (12 types) | The named adapter, properties and exact catalog cases/domains have evidence; every other property/domain remains unqualified |
| `UNSUPPORTED` (1 type) | Bare UNIQUAC's declared constructor-rejection contract is tested; this does not label subclasses unsupported |
| `DEBT` (118 types) | No numerical claim from this catalog; linked campaign issue and review condition are mandatory |

Every fixture is bound exactly once to its concrete type. Property sets must agree with
the referenced cases; unknown/stale types, changed kinds, missing cases and duplicate
bindings fail. `coverage.tsv` contains every type, classification, property set, case IDs,
scope, debt issue and review condition. `coverage.md` is a compact CI summary; it is not
a percentage of validated physics. Existing focused tests outside this catalog still
matter, but do not automatically establish a curated capability contract.

`initial-debt.txt` is the explicit debt snapshot from master
`c7cde46a78f18922534c18ec1241d61abac0a08c`. Do not expand or regenerate it to make CI pass.
New concrete types must receive a real typed fixture and sourced cases; adding a new
`DEBT` row is rejected. Qualifying an existing debt type changes its inventory row and
adds the complete family evidence. Keep the initial snapshot fixed so qualification
can be tracked without allowing new types to consume old debt slots. Changing a currently
covered type to debt also fails because it was not in the initial debt snapshot.

The standalone correlation scope is intentionally explicit: inherited saturation pressure,
the prescribed pow10KPa derivative and inverse, and their declared absence contracts.
It is not an inventory of all component, physical-property or transport APIs. Phase types
are inventoried independently of System drivers: the exact `PhaseSrkEos` and `PhasePrEos`
classes now have explicit Z/phi cases, while indirect use still does not qualify any other
phase entry point. Family batches must define applicable properties, physical
domains, sourced anchors and nearby-state/invariant checks before reducing this debt.

## Reference provenance

- [NIST acetone phase-change data](https://webbook.nist.gov/cgi/cbook.cgi?ID=C67641&Mask=4):
  log10(P/bar) = 4.42448 - 1312.253/(T/K - 32.445).
- [NIST i-pentane phase-change data](https://webbook.nist.gov/cgi/cbook.cgi?ID=C78784&Mask=4):
  log10(P/bar) = 3.91457 - 1020.012/(T/K - 40.053), using the 1945 range above.
- For the prescribed Wilson binary with x=x1 and x2=1-x:
  ln(gamma1) = 1 - ln(x+2(1-x)) - x/(x+2(1-x)) - 0.5(1-x)/(0.5x+1-x).
  The second coefficient follows the corresponding swapped component indices.
- Methanol group R is the existing repository regression/data contract in
  `UnifacGroupSynchronizationTest`, not an independently measured property.
- [Soave's 1972 SRK equation](https://doi.org/10.1016/0009-2509(72)80096-4) and
  [Peng and Robinson's 1976 equation](https://doi.org/10.1021/i160057a011) define
  the pure-fluid cubic and fugacity-coefficient references. The stored anchors use
  methane Tc=190.56 K, Pc=45.99 bar and acentric factor 0.0115. An independent
  calculation selected the largest real gas root and evaluated the published pure-fluid
  fugacity expression at the three declared states. A dependency-free harness control
  independently substitutes every stored Z into the published cubic and recomputes every
  phi reference before production evaluation. Agreement tolerance is 1e-12 in Z or phi
  because this is formula/dispatch evidence, not a physical-accuracy tolerance.
- [Renon and Prausnitz (1968)](https://doi.org/10.1002/aic.690140124) is the
  source for the NRTL local-composition form. Catalog anchors use authored prescribed
  parameters and an independent dependency-free evaluation of both activity coefficients
  and molar excess Gibbs energy. The 1e-12 gamma and 1e-9 J/mol excess-energy
  tolerances are analytical implementation tolerances, not experimental accuracy claims.

NIST sources were inspected on 2026-09-18. Only a few numerical values derived from
the identified correlations are included, not a redistributed NIST database or
compilation. Source compilation rights remain with the source; the authored fixtures
and analytical controls follow the repository's Apache-2.0 license. References are
stored offline so CI does not depend on live scientific websites.

Never replace a failed reference with the current implementation's answer. Investigate
units, validity, phase selection, data and algorithms, then document any justified
reference or tolerance change. Model-to-model agreement with shared formulas/data is
cross-implementation evidence, not independent experimental validation.

## Defect-detection and expansion policy

Harness self-tests reject malformed catalogs, missing required cases, nonfinite
numbers, zero positive-only properties, giant pressures and a plausible but wrong
constant. They also prove legitimate signed/zero properties and declared absence pass.
Production-path mutation evidence is recorded in the campaign PR/ledger separately;
helper self-tests alone are not proof that a production regression is detected.

Follow-up milestones reduce inventory debt with sourced mixture properties. The next
group-contribution phase batch requires a separate parameter-table provenance and
validation matrix; modified-HV/WS variants, fitted NRTL mixtures and VLE accuracy are
not implied by the prescribed binary equation checks. Subsequent work will
then add typed availability and enum dispatch. Java 8 enum switches are not
compiler-exhaustive: each new fixture/form needs a coverage test and a fail-closed
default. No public `double` signature is changed by this first increment.

See [component data contracts](../thermo/component_database_guide.md) and
[thermodynamic models](../thermo/thermodynamic_models.md) for production API behavior.


## PC-SAFT differential and root contracts

The JUnit contracts `SaftDerivativeConsistencyTest` and `PcsaftVolumeDomainTest`
complement the portable fixtures. At fixed composition and volume they compare
first and second temperature derivatives, the mixed derivative, volume curvature,
and the third hard-chain volume derivative with independent perturbations of
Helmholtz energy. They exercise both PC-SAFT implementations with pure fluids and
mixtures. `PhasePCSAFTRahmatTest` also checks a cold, dense methane/hexane root.

The packing fraction must satisfy `0 < eta < 1`; a returned molar volume must close
the specified pressure. Gas and liquid root selection is deterministic across
fresh and reused systems. Invalid input, a missing bracket, and a nonfinite or
unconverged pressure residual produce an exception. The finite bracketing mesh
is not a proof that every near-critical root can be resolved.

Fixing the diameter and product-rule derivatives changes caloric properties.
The methane/hexane regression at 250 K and 10 bara changes from about 172.366 to
219.083 J/K. `SystemPCSAFTTest` checks this against `dH/dT` at constant pressure
and fixed phase compositions. Reflashing at the perturbed temperatures would
include phase redistribution and would not test the same heat capacity.
The stored primitive regression values now refer to the physical pressure root;
these are numerical consistency checks, not experimental validation of parameters.
