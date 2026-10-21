---
title: JAMA to EJML migration and dependency retirement
description: EJML-only calculations, numerical qualification, and migration of former JAMA APIs.
---

# JAMA to EJML migration and dependency retirement

PR [#4416](https://github.com/equinor/neqsim/pull/4416) completes the removal of
JAMA from production and test calculations. Both `pom.xml` and `pomJava8.xml`
remove `gov.nist.math:jama`. No JAMA implementation is vendored, shaded, or
used as a fallback. The retired `JamaLinearAlgebra` backend is removed; select
`EjmlLinearAlgebra` through the existing `LinearAlgebraOperations` array API.

## Phase-envelope numerical correction

The phase-envelope continuation solver stores primitive arrays and solves its
linear systems with EJML. Its fugacity equations, material balances, damping,
retry limits and convergence tolerances remain unchanged.

The earlier conversion produced a reproducible pure-ethane cricondenbar of
59.0958176754016 bara on Linux and Windows. Restoring JAMA hid the problem;
it did not complete the migration. Continuation diagnostics isolate a false
accepted state at 277.24989559451365 K and 59.0958176754016 bara: the gas volume
implies an EOS pressure of 32.56026344346368 bara, while the liquid volume
satisfies the specified pressure. The gas volume iteration has stalled near
a spinodal. Distinct positive phase volumes and a small fugacity residual are
therefore insufficient to establish pure-component saturation.

Pure-component continuation now also requires both EOS volumes to reproduce
the specified pressure within a relative tolerance of `1e-6`. Non-finite or
inconsistent roots enter the existing step-reduction/retry path. This checks
the calculated state; it neither substitutes a critical pressure nor clamps
the envelope to a reference value. The original ethane acceptance bounds
(42–56 bara and 295–315 K) are retained. Regression coverage checks accepted
continuation points directly against the EOS pressure equation. Repeated local
calculations with EJML return 48.71999806842542 bara and 305.31999797987163 K.

Film Newton regression fixtures can have condition numbers above `1e20`.
Their migration checks equation assembly, ownership and damping, plus the
normwise linear backward error against a `1e-12` tolerance, rather than
requiring backend-specific correction vectors to agree in nearly singular
systems. This does not certify those intermediate iterations as converged
physical states. The EJML spectral reference is scaled before decomposition to
handle very large finite fluxes; the existing `1e-10` correction comparison
tolerance is unchanged.

## Public API migration

The complete removal intentionally changes the remaining matrix-specific
contracts. This is a source, binary and persisted-solver compatibility
boundary, requiring release review under [governance](../../GOVERNANCE.md) and
the [API lifecycle policy](api-lifecycle.md). It must not be advertised as a
binary-compatible patch. Existing array APIs remain the preferred interfaces.

| Previous JAMA surface | EJML-only replacement |
| --- | --- |
| `JamaLinearAlgebra` | `EjmlLinearAlgebra`; the former backend is removed. |
| `ChemicalReactionList.calcReacRates(...)` | The deprecated method returns an EJML `SimpleMatrix` column; prefer `calcReationRates(...)`, which returns `double[]`. |
| `FluidBoundaryInterface.getMassTransferCoefficientMatrix()` | Returns `SimpleMatrix[]`; prefer `getMassTransferCoefficientArray()`. |
| `FluidBoundary.massTransferCoefficientMatrix` | Transient EJML `SimpleMatrix[]` views, synchronized with canonical coefficient arrays. |
| `StatisticsBaseClass.checkBounds(...)` and `displayMatrix(...)` matrix overloads | Accept EJML `SimpleMatrix`; array calls dispatch to those EJML hooks. Rebuild and migrate subclass overrides. |
| `LevenbergMarquardtResult` matrix constructor and covariance/correlation getters | Use EJML `SimpleMatrix` with defensive copies; prefer `fromArrays(...)` and array getters. |
| `SysNewtonRhapsonPhaseEnvelope` persisted working state | Array layout with serialization UID 1001; recreate UID-1000 working solvers from thermodynamic inputs. |

For Java and Python/JVM callers, replace JAMA imports and matrix construction
with the corresponding EJML type, or migrate to primitive arrays. The EJML
accessors do not preserve old JAMA method descriptors. A typed pair of null
matrix arguments or the named `fromArrays(...)` factory avoids constructor
overload ambiguity. Previously serialized model graphs containing a UID-1000
phase-envelope solver cannot be restored by adding the old JAR.

Film coefficient access retains lazy allocation, live matrix mutation and
replacement, shallow-clone sharing, and synchronization before serialization.
EJML stores flat arrays, so synchronization copies values between its matrix
views and the canonical row-major coefficient arrays. Array-only callers do
not allocate matrix views. Result covariance/correlation accessors return
independent copies. Conversion code supports EJML 0.41 for Java 8 as well as
the current EJML version.

## Validation requirements

- Build and run with JAMA absent from the complete Maven test classpath.
- Retain physical regressions, analytical linear-algebra contracts, ownership,
  subclass dispatch, coefficient correction order, and serialization tests.
- Reject non-finite EJML results; factorization acceptance alone is insufficient.
- Check the EOS pressure residual as well as fugacity and material balance for
  pure-component continuation.
- Check both supported POM dependency trees and thin/shaded distributions.
- Run cross-platform CI; a successful local run does not establish identical
  continuation paths on every runtime or a performance improvement.

The dependency absence regression fails if `Jama.Matrix` becomes loadable on
the Maven test classpath. Independent numerical references use analytical
relations or Apache Commons Math; tests never call JAMA.
