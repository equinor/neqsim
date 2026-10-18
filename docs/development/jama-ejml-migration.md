---
title: JAMA to EJML migration and dependency retirement
description: Numerical validation, compatibility boundaries, and release gates for retiring the JAMA dependency.
---

# JAMA to EJML migration and dependency retirement

The numerical backend migration does **not** yet make JAMA removable from a
NeqSim distribution. Keep the dependency in all supported Maven build files
while production calculations, public compatibility methods, or supported
serialized models still require it. No removal release is committed.

## Reviewed migration series

The October 2026 audit covers the following merged changes and the follow-up in
[#4366](https://github.com/equinor/neqsim/pull/4366). A merged PR is not, by itself,
evidence of backward compatibility or a performance improvement.

| PR | Scope | Review boundary |
| --- | --- | --- |
| [#4109](https://github.com/equinor/neqsim/pull/4109) | Validated linear algebra interface and JAMA/EJML backends | Shared contracts cover dimensions, ownership, finite results and least-squares rank. |
| [#4164](https://github.com/equinor/neqsim/pull/4164) | Chemistry, flash, statistics and surface-tension integration | Array storage and protected extension points require a separate compatibility assessment. |
| [#4171](https://github.com/equinor/neqsim/pull/4171) | Pedersen characterization, TP gradient and flash arrays | Backend substitutions must retain convergence and physical-output tests. |
| [#4226](https://github.com/equinor/neqsim/pull/4226) | PH/PS Newton solver | Factorization and finite-step checks must remain in place. |
| [#4239](https://github.com/equinor/neqsim/pull/4239) | General Newton solver | EJML factorization acceptance alone does not prove a finite solution. |
| [#4294](https://github.com/equinor/neqsim/pull/4294) | Legacy TP solver deprecation | Deprecation does not authorize immediate removal of the public class. |
| [#4335](https://github.com/equinor/neqsim/pull/4335) | Characterization, film/pipe flow, columns and solid/wax solvers | Changed protected fields and serialized working state are compatibility boundaries. |
| [#4360](https://github.com/equinor/neqsim/pull/4360) | Chemical equilibrium and additional array APIs | Singular/non-finite LU results must reach the existing fallback or failure path. |
| [#4378](https://github.com/equinor/neqsim/pull/4378) | Film coefficient arrays and legacy live views | Preserve orientation, correction order, live-view ownership and serialization handling. |
| [#4366](https://github.com/equinor/neqsim/pull/4366) | Further EJML backend selection | Preserve the interface default bridge and defer the failing phase-envelope migration. |

## Phase-envelope migration is deferred

The array/EJML conversion in PR #4416 is not qualified for replacement of the
established solver. At exact head
`2d18635559c1766669ef9445dedd72eb640acd37`, both Ubuntu and Windows Java 21
fast-test jobs in [CI run 39130434441](https://github.com/equinor/neqsim/actions/runs/39130434441)
fail the unchanged `PTPhaseEnvelopeRobustnessTest.testPureEthane`:
SRK with classic mixing, pure ethane at 200 K and 10 bara calculates a
cricondenbar of **59.0958176754016 bara**, outside **42–56 bara**. Each job
reports 17,160 tests with one failure. The earlier review of
`09da30ef7bec8f6f6f74674394418056ad0532d8` reports the same failure. A passing
result from a different historical snapshot or runtime does not qualify these
committed trees; the cause of the differing macOS result remains unresolved.

The correction restores `SysNewtonRhapsonPhaseEnvelope` and its
Matrix-dependent `UMRPRUPhaseEnvelopeRegressionTest` from base
`ab0c50e5c47387666d65932978e3931999932183`. The solver retains its JAMA Matrix
working state and serialization UID **1000**, preserving the established
serialized layout. The proposed UID-1001 array layout is deferred with the
numerical conversion. No physical acceptance bounds are changed.

With the restored solver and the same remaining source and dependencies,
OpenJDK 17.0.20 on Linux calculates **50.939222570821975 bara** for the unchanged
ethane input in all nine calculations across three independent JVMs. The
unrestored head also reproduces the 59.0958176754016 bara failure locally.
These results support deferring the conversion; they do not identify the first
diverging continuation step or establish an EJML library defect.

A future migration needs exact-head continuation/residual diagnostics, pure
and multicomponent regressions, nearby operating points, cross-runtime
qualification, and an explicit compatibility decision for old serialized
solver instances under the [API lifecycle policy](api-lifecycle.md) and
[governance](../../GOVERNANCE.md). Do not widen the ethane pressure bound or
substitute the critical pressure for a failed calculated result.

## Remaining production dependencies

These classes still contain executable or signature references to JAMA after
the review fixes. Comments mentioning JAMA are not removal blockers.

| Class | Remaining dependency | Retirement requirement |
| --- | --- | --- |
| `SysNewtonRhapsonPhaseEnvelope` | Numerical matrices and serialized solver state | Qualify the replacement and preserve or explicitly migrate old state. |
| `JamaLinearAlgebra` | Deprecated public reference backend | Retire after the deprecation period, or retain it in a separate optional compatibility artifact. |
| `ChemicalReactionList` | Deprecated `calcReacRates(...)` result | Migrate callers to `calcReationRates(...)`; preserve the column-vector orientation during the support period. |
| `FluidBoundaryInterface` | Deprecated Matrix API and default array bridge | Preserve implementations that only implement the legacy Matrix accessor. |
| `FluidBoundary` | Deprecated live Matrix coefficient views | Preserve live mutation and replacement semantics until the API is retired. |
| `StatisticsBaseClass` | Legacy Matrix bounds and display methods | Preserve subclass bounds-hook dispatch; use arrays in new implementations. |
| `LevenbergMarquardtResult` | Deprecated Matrix constructor and covariance/correlation accessors | Use `fromArrays(...)` and the array accessors; retire signatures only after the support period. |

Use primitive arrays at new public numerical boundaries. Built-in film models
compute from arrays and materialize legacy Matrix views only when requested.
The interface default array method remains a compatibility bridge for existing
third-party implementations; making it abstract would break source compilation
and can cause `AbstractMethodError` for previously compiled implementations.

`JamaLinearAlgebra` is deprecated as a public reference backend; new backend
users should select `EjmlLinearAlgebra`. This does not deprecate or replace the
established phase-envelope solver. JAMA remains necessary for that production
calculation as well as the compatibility surfaces above. Keep the dependency
in the current release build files until both numerical qualification and
API-retirement requirements are satisfied.

Some earlier migrations also changed protected matrix fields and serialized
working-state layouts in characterization, film/pipe flow and saturation
solvers. Same-version serialization round trips do not prove that older streams
can be read. Downstream subclasses and persisted models need explicit migration
testing; keeping the JAMA JAR alone does not repair those layout changes. See
the affected class Javadocs and the [parameter-fitting migration notes](../statistics/parameter_fitting.md).

### Parameter-fitting release boundary

The published `v3.23.0` result constructor accepts JAMA matrices; the published
`v3.25.0` constructor accepts arrays. The review repair retains the public array
constructor and restores the deprecated Matrix constructor, so both binary
descriptors exist. Prefer the named `fromArrays(...)` factory in new source.
With both constructors present, an untyped `null, null` matrix pair is ambiguous
when recompiling; use the factory or explicitly typed null arguments. This
source migration caveat cannot be hidden by claiming complete compatibility.

Legacy `checkBounds(Matrix)` overrides again receive calls from the array-based
optimizer, and `displayMatrix(Matrix, String, int)` is available as a deprecated
bridge. Protected covariance/correlation fields retain their already-published
array types: changing them back would break newer subclasses. Subclasses tied
to the older Matrix fields must be rebuilt and migrated. Release notes must
separate these remaining field/layout changes from the restored method APIs.

## Numerical and performance acceptance

- Preserve equation assembly, dimensions, row/column orientation and input
  ownership exactly.
- Check both factorization acceptance and finite solve results. An EJML LU
  factorization can accept a singular matrix and subsequently produce NaN or
  infinity. Preserve existing SVD fallback and explicit failure semantics.
- Do not require bit-identical JAMA/EJML QR output. The Pedersen regression
  permits 32 ulps per step component and independently checks the scaled
  normal-equation residual; equation assembly remains exact and the existing
  final coefficient checks remain unchanged.
- Keep physical acceptance gates, conservation checks, repeated calculations,
  and relevant clone/serialization tests. A small linear residual does not
  establish thermodynamic correctness.
- Benchmark representative processes on the same runtime and inputs. A backend
  name or green unit suite is not evidence of a speedup. Investigate slow-suite
  timeouts without raising the timeout merely to make the migration pass.

## Release gates before dependency removal

1. Complete and publish the replacement APIs and migration notes, including
   Python/JVM callers, downstream subclasses and persisted models.
2. Satisfy the [API lifecycle policy](api-lifecycle.md): at least one subsequent
   published minor or major release after announced deprecation. A patch release
   or an unreleased POM version does not satisfy this period. Honor any applicable
   LTS support commitments.
3. Approve the required NRC, new major contract version, compatibility tests and
   release notes for removed public or serialized contracts. Obtain the required
   maintainer/domain review under [governance](../../GOVERNANCE.md).
4. Eliminate production bytecode and public-signature references, then build and
   package every supported Maven variant without JAMA. Test thin and shaded JAR
   consumers and Python/JVM loading on their supported Java runtimes. JAMA may
   remain a test-only reference dependency if intentional.
5. Run numerical, integration, documentation and representative performance
   checks against the release candidate and verify the dependency tree/artifacts.

Until these gates are met, retain JAMA as a production dependency. This is a
staged migration, not a commitment to remove it automatically after the next
release.
