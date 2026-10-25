# Strict single-phase release-inventory refinement

Baseline: PR #4577 head `f11f41c1928b4c7123ce99c59e4df5835def29ac`.
OpenJDK 17.0.20; production and new regression sources also compile against Java 8 APIs.

The existing `ReleaseInventoryTest.liquidWithdrawalUsesExplicitPhaseAndRejectsAbsentPhase`
fails on the baseline after 32 general VU calls. The inventory-local coupled refinement
passes it with the original assertions. The general `OptimizedVUflash` implementation is
unchanged by this repair.

`qualification.csv` records the public regression's 24 independently configured states:
SRK/PR, classical rule 2/HV rule 4, 295/300/305 K, and 0.002/0.02 m3 vessels.
Each undergoes three successive 0.01 s withdrawals at a controlled 0.002 kg/s flux.
The controlled release model conserves the stagnation enthalpy and isolates inventory
thermodynamics from nozzle correlations. The original regression separately retains the
homogeneous-equilibrium nozzle model.

All 72 steps close volume and open-system energy at the unchanged relative 1e-11 target.
The largest residuals are 8.840e-12 (volume) and 6.717e-12 (energy); at most five
volume/energy solves or accepted refinements are needed. This work counter does not count
TP line-search trials or the Newton iterations internal to a general VU call. It is not a
wall-clock performance claim. The new matrix fails the eight-solve work bound with the
baseline class in all four EOS/rule configurations.

The coupled derivatives are independently central-differenced at two step sizes in
`VUCaloricStateQualificationTest`; fixed-composition derivatives apply only to an unchanged
equilibrated single phase. TP-reflashed bounded trials must lower the scaled residual.
Multiphase states and rejected corrections retain the existing general VU path and strict
failure gate. Existing separator-transient, phase-transition, rollback and CPA-isolation
regressions must remain passing.

Reproduce after compilation:

```sh
./mvnw -o -q -Dtest=ReleaseInventoryTest,ReleaseInventoryVuRefinementTest,ReleaseInventoryCpaIsolationTest,VUCaloricStateQualificationTest,TransientSeparatorVUFlashTest,VUFlashTest,VUFlashEOSCGTest -DexcludedTestGroups= surefire:test
```

The CSV is numerical model-equation qualification, not measured fluid accuracy or
qualification of the unrelated phase-changing separator trajectory tracked in #4583.
