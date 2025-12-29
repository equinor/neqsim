# NeqSim AI Guidance for Coding Agents
- **Mission Focus**: NeqSim is a Java toolkit for thermodynamics and process simulation; changes usually affect physical property models (`src/main/java/neqsim/thermo`) or process equipment (`src/main/java/neqsim/process`).
- **Architecture Overview**: Packages map to the seven base modules in docs/modules.md; keep new code within the existing package boundaries so thermodynamic, property, and process layers stay decoupled.
- **Thermo Systems**: Fluids are represented by `SystemInterface` implementations such as `SystemSrkEos` or `SystemSrkCPAstatoil`; always set a mixing rule (`setMixingRule("classic")` or numeric CPA rule) and call `createDatabase(true)` when introducing new components.
- **Process Equipment Pattern**: Equipment extends `ProcessEquipmentBaseClass` and is registered inside a `ProcessSystem`; `ProcessSystem` enforces unique names and handles recycle/adjuster coordination, so reuse it for multi-unit workflows.
- **Streams & Cloning**: Instantiate feeds with `Stream`/`StreamInterface`, call `setFlowRate`, `setTemperature`, `setPressure`, then `run()`; clone fluids (`system.clone()`) before branching to avoid shared state between trays or unit operations.
- **Distillation Column**: `DistillationColumn` provides sequential, damped, and inside-out solvers; maintain solver metrics (`lastIterationCount`, `lastMassResidual`, `lastEnergyResidual`) and feed-tray bookkeeping when altering column logic to keep tests like `insideOutSolverMatchesStandardOnDeethanizerCase` green.
- **ProcessSystem Utilities**: Use `ProcessSystem.add(unit)` to build flowsheets, `run()`/`run(UUID)` for execution, and `copy()` when duplicating equipment; modules can self-initialize through `ModuleInterface`—respect these hooks if you add packaged subsystems.
- **Data & Resources**: Component metadata lives under `src/main/resources`; heavy datasets (e.g., `neqsim_component_names.txt`) must remain synchronized with thermodynamic model expectations before publishing new components.
- **Logging & Diagnostics**: log4j2 powers runtime logging; tests often assert solver convergence instead of inspecting logs, so prefer returning residuals over printing when adding instrumentation.
- **Build & Test Workflow**: Use `./mvnw install` for a full build (Windows: `mvnw.cmd install`); run the entire suite with `./mvnw test` and checkstyle/spotbugs/pmd with `./mvnw checkstyle:check spotbugs:check pmd:check`.
- **Focused Tests**: Use the Maven `-Dtest` flag to run individual classes or methods; this keeps solver regressions quick to triage.
- **Style & Formatting**: Java code follows Google style with project overrides from `checkstyle_neqsim.xml` and formatter profiles (`neqsim_formatter.xml`); keep indentation at two spaces and respect existing comment minimalism.
- **Serialization & Copying**: Many equipment classes rely on Java serialization (`ProcessEquipmentBaseClass.copy()`); avoid introducing non-serializable fields or mark them `transient` to preserve cloning.
- **External Dependencies**: Core math depends on EJML, Commons Math, JAMA, and MTJ; check numerical stability when swapping linear algebra routines, and keep JSON/YAML handling aligned with gson/jackson versions pinned in pom.xml.
- **Java 8 Compatibility (MANDATORY)**: All code MUST be Java 8 compatible. NEVER use these Java 9+ features: `var` keyword, `String.repeat()`, `String.isBlank()`, `String.strip()`, `String.lines()`, `List.of()`, `Set.of()`, `Map.of()`, `Optional.isEmpty()`, `InputStream.transferTo()`, `Stream.takeWhile()`, `Stream.dropWhile()`, text blocks ("""), records, sealed classes, pattern matching. Use `StringUtils.repeat()` from Apache Commons instead of `String.repeat()`. Use `str.trim().isEmpty()` instead of `str.isBlank()`. Use `Arrays.asList()` or `Collections.singletonList()` instead of `List.of()`.
- **Sample Flow**:

```java
SystemInterface gas = new SystemSrkEos(216.0, 30.0);
gas.addComponent("methane", 0.5);
gas.setMixingRule("classic");
Stream feed = new Stream("feed", gas);
feed.setFlowRate(100.0, "kg/hr");
feed.run();
DistillationColumn column = new DistillationColumn("Deethanizer", 5, true, false);
column.addFeedStream(feed, 5);
column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
column.run();
```
- **Test Authoring Tips**: Place new tests under the matching feature package (see docs/wiki/test-overview.md) and assert on physical outputs or solver residuals rather than internal arrays to keep tests resilient.
- **Regression Safety**: When modifying solver logic or property correlations, capture baseline values in tests and drop CSV/JSON fixtures into `src/test/resources` instead of hardcoding magic numbers in code.
- **Documentation Touchpoints**: Update README sections or docs/wiki entries when adding new models; the docs mirror the package layout and help downstream consumers understand new unit operations.
- **Community Norms**: Engage on GitHub issues or discussions for design questions; NeqSim has an active user base familiar with thermodynamics and process simulation who can provide valuable insights.
- **Performance Considerations**: Profile long-running simulations with Java Flight Recorder or VisualVM; optimize critical loops in thermodynamic calculations but prioritize clarity and maintainability in the codebase.
- **JavaDoc Standards (MANDATORY)**: ALWAYS document all public classes and methods with complete JavaDoc. Required elements: (1) class-level description with `@author` and `@version`, (2) method description, (3) `@param` for every parameter with type and valid range, (4) `@return` describing what is returned, (5) `@throws` for each exception. Before completing any code change, verify JavaDoc is complete and accurate. Update JavaDoc when modifying method signatures.
- **Java 8 Features**: All new code must be Java 8 compatible; use streams, lambdas, and `Optional` where they enhance readability. NEVER use `String.repeat()` - use `StringUtils.repeat()` from Apache Commons. NEVER use `var`, `List.of()`, `Map.of()`, text blocks, or any Java 9+ syntax.
- **Validation Framework**: Use `SimulationValidator.validate(object)` before running simulations to catch configuration errors early. When extending equipment, override `validateSetup()` to add custom validation. See `neqsim.util.validation` package and docs/integration/ai_validation_framework.md.
- **AI-Friendly Error Handling**: Exceptions in `neqsim.util.exception` provide `getRemediation()` hints. When adding new errors, include actionable fix suggestions that AI agents can parse.
- **Auto-Validation for New Equipment**: When creating a new class that extends `ProcessEquipmentBaseClass`, ALWAYS generate a `validateSetup()` method that checks: (1) required input streams are connected, (2) required parameters are set and within valid ranges, (3) return `ValidationResult` with remediation hints for each issue.
- **Auto-Annotation for Public Methods**: When adding new public methods to core classes (SystemInterface, ProcessEquipmentInterface), consider adding `@AIExposable` annotation with description, category, example, and `@AIParameter` annotations documenting valid ranges/options.
- **Jupyter Notebook Examples**: When creating Jupyter notebook examples, ensure they run end-to-end and reflect the latest API changes; place them in the `notebooks/` directory and link to them from the main documentation. Follow the neqsim-python direct Java API bindings as shown at https://github.com/equinor/neqsim-python?tab=readme-ov-file#4-direct-java-access-full-control
---
