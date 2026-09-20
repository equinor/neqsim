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
`target/model-spec/results.tsv` evidence. Production component-data and test-resource
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
ln(gamma); gamma, Z, group R and applicable saturation pressure must be positive.
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

The initial catalog has 31 cases across six system drivers (SRK, PR, Wilson, classic
UNIFAC, PSRK and UMR-PRU), a component saturation adapter and an unsupported phase
adapter. This is **not coverage of every NeqSim model or every property**. Campaign
milestone B owns the complete concrete-model inventory, per-property coverage debt,
and the future inventory-to-catalog gate. The present required-ID check covers only
this explicitly curated initial catalog.

| Cases | Evidence and tolerance rationale |
| --- | --- |
| Acetone at 280, 298.15 and 320 K | NIST WebBook Antoine correlation, Ambrose et al. (1974), 259.16–507.60 K; same fitted correlation, 1e-9 relative implementation tolerance |
| i-Pentane at 290, 298.15 and 301 K | NIST WebBook Willingham et al. (1945), 289.44–301.74 K; independent correlation versus NeqSim DIPPR data, 1% comparison tolerance |
| Binary Wilson | Prescribed Lambda12=2, Lambda21=0.5, with mole fractions 0.2/0.8, 0.5/0.5 and 0.8/0.2; closed-form numerical fixtures, not experimental mixture validation |
| UNIFAC, PSRK, UMR-PRU | Pure methanol gamma=1 reference identity and subgroup-15 R=1.4311 data regression; populated group contents and stored coefficients are read |
| SRK and PR | Low-pressure methane Z approaching unity at 300 K and 0.0001 bar (absolute tolerance 1e-5); zero ideal enthalpy at the 273.15 K reference |
| Missing/unsupported | Hydrogen/nC20 correlation absence, Na+ inapplicability, supercritical methane and bare UNIQUAC rejection |

The low-pressure EOS and pure-component GE cases are intentionally limited controls,
not validation of mixture accuracy or high-pressure behavior. Gamma=1 alone cannot
detect an always-one stub; nonideal Wilson values, group-content checks and the existing
binary UNIFAC regressions provide distinct checks. `ModelSpecStateTest` additionally
tests changed Wilson composition and binary UNIFAC/PSRK component-order and repeated
initialization behavior. UMR-PRU is driven through the actual HV mixing rule and its GE
phase; standalone UNIQUAC is not incorrectly classified as working because a UNIFAC
subclass works.

`ComponentCorrelationSpecTest` gives analytical pressure examples for pow10,
pow10KPa, exp, log, legacy DIPPR and Wagner dispatch. These synthetic coefficients
exercise existing label precedence and pressure units. This does not claim all forms'
derivatives or inverse operations are qualified; preserve the more detailed
`ComponentAntoineVaporPressureTest` and track remaining derivative/inverse coverage
under #3792. In particular, a pressure-only fixture must not imply availability of an
unimplemented derivative.

The pow10KPa derivative currently returns zero despite a nonzero analytical slope;
this was reproduced and isolated in [issue #3798](https://github.com/equinor/neqsim/issues/3798).
That numerical repair is deliberately separate from the test-infrastructure increment.

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

Follow-up milestones cover complete model inventory and sourced mixture properties,
then additive typed availability and enum dispatch. Java 8 enum switches are not
compiler-exhaustive: each new fixture/form needs a coverage test and a fail-closed
default. No public `double` signature is changed by this first increment.

See [component data contracts](../thermo/component_database_guide.md) and
[thermodynamic models](../thermo/thermodynamic_models.md) for production API behavior.
