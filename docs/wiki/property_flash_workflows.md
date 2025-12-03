# Property flash workflows proven by tests

`ThermodynamicOperationsTest` exercises NeqSim's property flash API across PT/TP orderings, unit handling, and online composition updates. This guide distills the tested patterns so you can set up property flashes with confidence.

## PT vs TP flash symmetry

`testFlash` creates a binary methane/ethane SRK system and runs both `flash(FlashType.PT, P, T, unitP, unitT)` and `flash(FlashType.TP, T, P, unitT, unitP)` on the same state, then compares all returned properties for equality.【F:src/test/java/neqsim/thermodynamicoperations/ThermodynamicOperationsTest.java†L25-L48】 The test verifies that property flashes are order-invariant when pressure and temperature units are supplied explicitly.

**Takeaway:** You can interchange PT and TP flash modes without changing results, as long as you reinitialize (`init(2)` and `initPhysicalProperties()`) before reading properties.

## Validating request inputs

`testFluidDefined` builds several SRK air mixtures and calls `propertyFlash` with streaming pressure/temperature vectors and optional online compositions to mimic live analyzers.【F:src/test/java/neqsim/thermodynamicoperations/ThermodynamicOperationsTest.java†L50-L119】 The test first omits `init(0)` to prove the API returns a descriptive error (`"Sum of fractions must be approximately to 1 or 100..."`), then reinitializes and confirms all calculation errors are null.

**Setup checklist from the test:**

1. Add components and set a mixing rule plus any volume corrections.
2. Call `init(0)` before requesting properties to normalize molar fractions.
3. Pass `FlashMode` 1, 2, or 3; any other mode yields the explicit error asserted in `testNeqSimPython`.【F:src/test/java/neqsim/thermodynamicoperations/ThermodynamicOperationsTest.java†L120-L152】
4. When streaming online composition updates, make sure each inner list matches the length of the pressure/temperature vectors.

## Integration pattern for external clients

`testNeqSimPython` and `testNeqSimPython2` illustrate how property flashes can be called from foreign interfaces (e.g., Python bindings) while maintaining result integrity.【F:src/test/java/neqsim/thermodynamicoperations/ThermodynamicOperationsTest.java†L120-L209】 The tests check that:

- Returned `CalculationResult` objects are stable under equality and `hashCode()` comparisons.
- Invalid `FlashMode` inputs return clear error strings without throwing exceptions.
- Property arrays cover the full set of `SystemProperties.getPropertyNames()` even when only single-point requests are made.

When wrapping the API externally, mirror these assertions to guard against transport or serialization errors.
