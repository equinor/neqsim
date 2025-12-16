# Repository layout quick reference

Use this guide to place new files consistently across the repository.

## Production code

* Java sources live under `src/main/java` and follow the `com.equinor.neqsim` package root.
* Keep contributions inside the established functional modules:
  * `com.equinor.neqsim.thermo` – thermodynamic routines.
  * `com.equinor.neqsim.physicalproperties` – transport and thermophysical property models.
  * `com.equinor.neqsim.processsimulation` – unit operations, process models, and flowsheet orchestration.
  * `com.equinor.neqsim.chemicalreactions` – equilibrium and kinetic reactions.
  * `com.equinor.neqsim.parameterfitting` – parameter estimation tools.
* Shared helpers can go in `com.equinor.neqsim.util.*` when they are not domain-specific.
* Avoid inventing parallel hierarchies; extend the closest existing module instead.

## Tests

* Place JUnit tests in `src/test/java`, mirroring the package of the code under test.
* Small fixtures and golden files belong in `src/test/resources` within the same package path.
* Integration-style examples that demonstrate APIs should stay in `examples/` rather than under `src/test/java`.

## Resources and data

* Runtime resources (configuration templates, lookup tables) belong in `src/main/resources`.
* Test-only resources go in `src/test/resources`.
* Larger datasets or notebooks should live in `data/` or `notebooks/` to keep the packaged library lean.
* Runnable samples and tutorial code belong in `examples/` and should avoid depending on internal test fixtures.
