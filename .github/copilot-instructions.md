# NeqSim AI Guidance for Coding Agents

## Quick Orientation

> **Start here:** Read `CONTEXT.md` in the repo root for a 60-second overview of the
> entire codebase â€” repo map, code patterns, build commands, and constraints.
>
> **Solving a task?** See `docs/development/TASK_SOLVING_GUIDE.md` for the step-by-step
> workflow: classify the task, find similar past solutions, write code, verify, log it.
>
> **Looking for code patterns?** `docs/development/CODE_PATTERNS.md` has copy-paste
> starters for every common task (fluids, flash, equipment, PVT, tests, notebooks).
>
> **Was this solved before?** Search `docs/development/TASK_LOG.md` for keywords.
> Every solved task gets an entry there â€” check before starting from scratch.

---

## âš ï¸ CRITICAL: Java 8 Compatibility (READ FIRST)

**All code MUST compile with Java 8.** The CI build will FAIL if you use Java 9+ features.

**This applies to ALL Java files including test classes in `src/test/java/`.**

### FORBIDDEN Java 9+ Features (NEVER USE):
| Forbidden | Java 8 Alternative |
|-----------|-------------------|
| `"str".repeat(n)` | `StringUtils.repeat("str", n)` (Apache Commons) |
| `var x = ...` | Explicit type declaration: `String x = ...`, `Map<String, Object> map = ...` |
| `List.of(a, b)` | `Arrays.asList(a, b)` or `Collections.singletonList(a)` |
| `Set.of(a, b)` | `new HashSet<>(Arrays.asList(a, b))` |
| `Map.of(k, v)` | `Collections.singletonMap(k, v)` or HashMap |
| `str.isBlank()` | `str.trim().isEmpty()` |
| `str.strip()` | `str.trim()` |
| `str.lines()` | `str.split("\\R")` or BufferedReader |
| `Optional.isEmpty()` | `!optional.isPresent()` |
| Text blocks `"""..."""` | Regular strings with `\n` |
| Records | Regular class with fields |
| Pattern matching `instanceof` | Traditional instanceof + cast |

### Common `var` Replacement Examples:
```java
// WRONG (Java 10+):
var map = someMethod.toMap();
var list = getItems();
var result = calculate();

// CORRECT (Java 8):
Map<String, Object> map = someMethod.toMap();
List<String> list = getItems();
CalculationResult result = calculate();
```

### Required Import for String Repeat:
```java
import org.apache.commons.lang3.StringUtils;
// Usage: StringUtils.repeat("=", 70)
```

---

## Quick Commands

- **Package and Update Python**: When the user says "package and update python" or similar, run these commands:
  ```powershell
  .\mvnw.cmd package -DskipTests
   Copy-Item -Path "C:\Users\ESOL\Documents\GitHub\neqsim\target\neqsim-3.10.0.jar" -Destination "C:\Users\ESOL\AppData\Roaming\Python\Python312\site-packages\neqsim\lib\java11\" -Force
  ```
  This builds the NeqSim JAR and copies it to the Python neqsim package for immediate use.

---

## API Consistency (MANDATORY)

When creating example files or documentation that references existing classes:

1. **ALWAYS verify method signatures** before using them - read the actual class to confirm:
   - Constructor parameters (type and order)
   - Method names exist and have correct parameter types
   - Return types match expected usage

2. **Common API verification pattern**:
   ```
   # Before writing example code that uses SomeClass:
   1. Search for the class: file_search("**/SomeClass.java")
   2. Read constructor and method signatures
   3. Use only methods that actually exist with correct parameter types
   ```

3. **Do NOT assume API patterns** - different classes may have different conventions:
   - Some constructors take `String name`, others take `ProcessSystem`
   - Method names like `addEquipment` vs `addEquipmentReliability` vs `addEquipmentMtbf`
   - Parameter counts vary (e.g., 3 params vs 4 params)

4. **Inner classes and enums** - verify the exact location:
   - Enums may be in different classes: `RiskEvent.ConsequenceCategory` vs `RiskMatrix.ConsequenceCategory`
   - Inner classes require full path: `BowTieModel.Threat`, `PortfolioRiskAnalyzer.CommonCauseScenario`
   - Check imports in the actual class to see which enum/type it uses

5. **Object-based vs convenience APIs** - do NOT assume convenience methods exist:
   - Wrong: `model.addThreat("name", 0.1)` (assuming convenience overload)
   - Right: First check if method takes objects: `model.addThreat(new Threat(...))`
   - Many APIs use builder patterns or require creating objects explicitly

6. **Common API mistakes to avoid**:
   - Assuming `getXxx95()` exists when actual method is `getXxx(int percentile)`
   - Assuming enum constants like `SEVERE_WEATHER` when actual is `CommonCauseType.WEATHER`
   - Assuming 1-arg constructors when 2+ args are required
   - Calling methods on wrong class (e.g., `analyzer.getFrequency()` vs `model.getFrequency()`)
   - Assuming `calculate()` when actual method is `calculateRisk()` or `run()`
   - Assuming convenience overloads like `addAsset(name, value1, value2, value3)` when API is `addAsset(id, name, value)`
   - Using descriptive names as IDs when API distinguishes between `id` and `name` parameters

## Documentation Code Verification (MANDATORY)

**Every code example in documentation MUST be verified by a runnable test.**
**Documentation is NOT complete until the test has been executed and passes.**

When writing documentation that includes Java or Python code examples:

1. **Write a JUnit 5 test** that exercises every API call shown in the documentation.
   - Append to `src/test/java/neqsim/DocExamplesCompilationTest.java` for general utilities.
   - Or create a dedicated test in the appropriate package directory.
   - The test must instantiate classes, call all documented methods, and assert results are non-null/valid.

2. **Run the test** and confirm all assertions pass before finalizing documentation.
   - Use `./mvnw test -Dtest=DocExamplesCompilationTest` (or the specific test class).
   - **If the test fails, fix the documentation code â€” do NOT finalize with broken examples.**
   - This step is NON-NEGOTIABLE â€” never skip it, even for "simple" examples.

3. **Keep tests in sync** â€” when documentation changes, update the corresponding test.

4. **For Python examples**: verify the equivalent Java API calls work (Python examples call
   the same Java methods via jpype). If the Java test passes, the Python example will work.

5. **Common doc-code bugs to catch with tests**:
   - Plus fraction names with `+` character (`"C20+"` crashes â€” use `"C20"`)
   - Wrong method names (`getUnitOperation()` vs `getUnit()`)
   - Wrong parameter types (`int` vs `double`)
   - Calling characterization before setting mixing rule
   - Wrong risk threshold descriptions not matching source logic
   - Methods requiring unit strings (e.g., `setDesignAmbientTemperature(15.0, "C")` not `setDesignAmbientTemperature(15.0)`)
   - Getter methods requiring arguments (e.g., `getFanStaticPressure(flow)` not `getFanStaticPressure()`)

---

- **Mission Focus**: NeqSim is a Java toolkit for thermodynamics and process simulation; changes usually affect physical property models (`src/main/java/neqsim/thermo`) or process equipment (`src/main/java/neqsim/process`).
- **Architecture Overview**: Packages map to the seven base modules in docs/modules.md; keep new code within the existing package boundaries so thermodynamic, property, and process layers stay decoupled.
- **âš ï¸ Property Initialization After Flash (CRITICAL)**: After any flash calculation (`TPflash`, `PHflash`, `PSflash`, etc.), you MUST call `fluid.initProperties()` before reading physical/transport properties. `init(3)` alone does NOT initialize transport properties (viscosity, thermal conductivity). Use `fluid.initProperties()` which calls both `init(2)` + `initPhysicalProperties()`. Without this, `getViscosity()`, `getThermalConductivity()`, and `getDensity()` may return **zero**.- **âš ï¸ Phase Envelope Branch Labels (CRITICAL)**: When using `calcPTphaseEnvelope(true, 1.0)` (bubblePointFirst=true), `getBubblePointTemperatures()` returns physically DEW curve data and `getDewPointTemperatures()` returns physically BUBBLE curve data (labels are swapped). **Always classify branches by physical reasoning**: the branch with the higher maximum temperature is the dew curve (contains cricondentherm). See `neqsim-api-patterns` skill for the correct pattern.- **Thermo Systems**: Fluids are represented by `SystemInterface` implementations such as `SystemSrkEos` or `SystemSrkCPAstatoil`; always set a mixing rule (`setMixingRule("classic")` or numeric CPA rule) and call `createDatabase(true)` when introducing new components.
- **Process Equipment Pattern**: Equipment extends `ProcessEquipmentBaseClass` and is registered inside a `ProcessSystem`; `ProcessSystem` enforces unique names and handles recycle/adjuster coordination, so reuse it for multi-unit workflows. Use `MultiPortEquipment` as the base class for equipment with multiple inlet/outlet streams.
- **Stream Introspection**: Every `ProcessEquipmentInterface` exposes `getInletStreams()` and `getOutletStreams()` returning `List<StreamInterface>`. Use these to walk flowsheets programmatically, build topology graphs, or auto-generate DEXPI/P&IDs. Equipment classes (Separator, Mixer, Splitter, etc.) override these to return their specific connected streams.
- **Named Controllers**: Attach multiple controllers to equipment via `addController("tag", controller)`, retrieve with `getController("tag")`, list all with `getControllers()`. The legacy `setController()`/`getController()` still work (backward-compatible). During dynamic simulation `runTransient()`, the `ProcessSystem` explicitly runs all controller devices and measurement devices each timestep.
- **Explicit Connections**: Record typed connection metadata via `process.connect(source, target, ProcessConnection.ConnectionType.MATERIAL, "label")`. Connection types: `MATERIAL`, `ENERGY`, `SIGNAL`. Query with `process.getConnections()`.
- **Unified Element Model**: `ProcessElementInterface` is the common supertype for equipment, controllers, and measurement devices. Query all elements with `process.getAllElements()`. This enables DEXPI export, topology analysis, and flowsheet introspection.
- **Streams & Cloning**: Instantiate feeds with `Stream`/`StreamInterface`, call `setFlowRate`, `setTemperature`, `setPressure`, then `run()`; clone fluids (`system.clone()`) before branching to avoid shared state between trays or unit operations.
- **Distillation Column**: `DistillationColumn` provides sequential, damped, and inside-out solvers; maintain solver metrics (`lastIterationCount`, `lastMassResidual`, `lastEnergyResidual`) and feed-tray bookkeeping when altering column logic to keep tests like `insideOutSolverMatchesStandardOnDeethanizerCase` green.
- **ProcessSystem Utilities**: Use `ProcessSystem.add(unit)` to build flowsheets, `run()`/`run(UUID)` for execution, `copy()` when duplicating equipment, `connect()` for explicit connections, and `getAllElements()` to query all equipment, controllers, and measurements; modules can self-initialize through `ModuleInterface`â€”respect these hooks if you add packaged subsystems.
- **ProcessModel for Multi-Area Plants (MANDATORY)**: For large plants (platforms, gas plants), split into separate `ProcessSystem` objects per process area then combine with `ProcessModel`. Use `plant.add("area name", processSystem)` to register named areas, `plant.run()` iterates until convergence, `plant.get("area name")` retrieves sub-processes, and `plant.getConvergenceSummary()` reports status. See the reference platform models for the canonical pattern: each area is a Python function returning a `ProcessSystem`, cross-system streams are shared by object reference, and all systems are composed into a `ProcessModel` at the end. **NEVER** add a `ProcessModule` or `ProcessModel` to a `ProcessSystem` â€” it will throw TypeError.
- **Automation API (PREFERRED for agents)**: Use `ProcessAutomation` for string-addressable variable access instead of navigating Java class hierarchies. Get the facade via `process.getAutomation()` or `plant.getAutomation()`. Discover equipment with `getUnitList()`, list variables with `getVariableList("unitName")` (returns `SimulationVariable` with INPUT/OUTPUT type, address, unit, description), read values with `getVariableValue("Unit.stream.property", "unit")`, write with `setVariableValue("Unit.property", value, "unit")`. For multi-area models, use area-qualified addresses: `"Area::Unit.stream.property"` with `getAreaList()` for discovery.
- **Self-Healing Automation (PREFERRED for agents)**: Use `getVariableValueSafe()` and `setVariableValueSafe()` instead of direct get/set. These return JSON with the value on success, or diagnostics with suggestions, auto-corrections, and remediation hints on failure. Access `auto.getDiagnostics()` for fuzzy name matching (`autoCorrectName()`), physical bounds validation (`validatePhysicalBounds()`), and operation tracking (`getLearningReport()`). The `AutomationDiagnostics` class learns from past failures â€” corrections are cached and reused automatically.
- **Lifecycle State (Save/Restore/Compare)**: Use `ProcessSystemState.fromProcessSystem(process)` and `ProcessModelState.fromProcessModel(plant)` to create portable JSON snapshots. Save with `state.saveToFile("model.json")`, load with `ProcessSystemState.loadFromFile("model.json")`, validate with `state.validate()`. Compare versions with `ProcessModelState.compare(v1, v2)` returning a `ModelDiff` (modified parameters, added/removed equipment). Use `toCompressedBytes()`/`fromCompressedBytes()` for network transfer. All state classes live in `neqsim.process.processmodel.lifecycle`.
- **Data & Resources**: Component metadata lives under `src/main/resources`; heavy datasets (e.g., `neqsim_component_names.txt`) must remain synchronized with thermodynamic model expectations before publishing new components.
- **Logging & Diagnostics**: log4j2 powers runtime logging; tests often assert solver convergence instead of inspecting logs, so prefer returning residuals over printing when adding instrumentation.
- **Build & Test Workflow**: Use `./mvnw install` for a full build (Windows: `mvnw.cmd install`); run the entire suite with `./mvnw test` and checkstyle/spotbugs/pmd with `./mvnw checkstyle:check spotbugs:check pmd:check`.
- **Focused Tests**: Use the Maven `-Dtest` flag to run individual classes or methods; this keeps solver regressions quick to triage.
- **Style & Formatting**: Java code follows Google style with project overrides from `.config/checkstyle_neqsim.xml` and formatter profiles (`.config/neqsim_formatter.xml`); keep indentation at two spaces and respect existing comment minimalism.
- **Serialization & Copying**: Many equipment classes rely on Java serialization (`ProcessEquipmentBaseClass.copy()`); avoid introducing non-serializable fields or mark them `transient` to preserve cloning. SpotBugs enforces this via the SE_BAD_FIELD rule. When adding fields to any `Serializable` class (equipment, measurement devices, mechanical design, thermo phases), use the correct modifier order: `private transient Type field;` or `private final transient Type field;`. Common non-serializable types that need `transient`: `Function`, `BiConsumer`, `Consumer`, `Thread`, JDBC `Connection`/`Statement`, Apache Commons Math interpolators, and any inner class that doesn't implement `Serializable`. The `ProcessLogic` interface extends `Serializable`.
- **External Dependencies**: Core math depends on EJML, Commons Math, JAMA, and MTJ; check numerical stability when swapping linear algebra routines, and keep JSON/YAML handling aligned with gson/jackson versions pinned in pom.xml.
- **Java 8 Compatibility (MANDATORY)**: See the critical section at the top of this document. All code MUST compile with Java 8. The CI build will FAIL if you use Java 9+ features like `String.repeat()`, `var`, `List.of()`, etc.
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
- **JavaDoc Standards (MANDATORY)**: ALWAYS document ALL classes and methods (public, protected, AND private) with complete JavaDoc. The Maven JavaDoc plugin checks all methods. Required elements: (1) class-level description with `@author` and `@version`, (2) method description, (3) `@param` for EVERY parameter with type and valid range, (4) `@return` describing what is returned (for non-void methods), (5) `@throws` for each exception. Before completing any code change, verify JavaDoc is complete and accurate. Update JavaDoc when modifying method signatures. Private methods also require complete JavaDoc with all @param and @return tags.

## JavaDoc HTML5 Compatibility (MANDATORY)

When writing JavaDoc, ensure HTML5 compatibility for the Maven JavaDoc plugin:

### Tables
- **ALWAYS** include `<caption>` element after `<table>` tag
- **NEVER** use the `summary` attribute (deprecated in HTML5)
- Correct format:
```java
/**
 * <table>
 * <caption>Description of table contents</caption>
 * <tr><th>Header</th></tr>
 * <tr><td>Data</td></tr>
 * </table>
 */
```

### @see Tags
- **NEVER** use `@see` with plain text like `@see IEC 61508` - this causes "reference not found" errors
- Only use `@see` with valid Java references: `@see ClassName`, `@see #methodName`, `@see package.ClassName#method`
- For standards references, put them in the description text instead:
```java
/**
 * Implements safety functions per IEC 61508 and IEC 61511 standards.
 */
```

### Common JavaDoc Errors to Avoid
| Error | Cause | Fix |
|-------|-------|-----|
| "no summary or caption for table" | Missing `<caption>` | Add `<caption>` after `<table>` |
| "attribute not supported in HTML5: summary" | Using `summary=""` on table | Remove `summary` attribute |
| "reference not found" | Invalid `@see` reference | Use valid class/method reference or move to description |
| "no @param for X" | Missing parameter documentation | Add `@param X description` |
| "no @return" | Missing return documentation | Add `@return description` |
| "no @throws for X" | Method throws exception without doc | Add `@throws X description` |
| "unexpected end tag" | Mismatched HTML tags like extra `</p>` | Check tag nesting, remove orphan closing tags |
| "semicolon missing" | Malformed HTML in JavaDoc | Check HTML tag closure |
| "bad use of '>'" | Lambda arrow `->` or comparison `>` in JavaDoc | Use `&gt;` for `>` or rewrite lambdas as anonymous classes |

### Methods with throws Clause (CRITICAL)
- **EVERY** method with a `throws` clause MUST have `@throws` documentation for each exception
- This applies to ALL methods including private methods
- Format:
```java
/**
 * Writes data to the output.
 *
 * @param out the appendable to write to
 * @param data the data to write
 * @throws IOException if an I/O error occurs during writing
 */
private void writeData(Appendable out, String data) throws IOException {
```

### HTML Tag Nesting
- **NEVER** have orphan closing tags (e.g., `</p>` without matching `<p>`)
- Check that `<ul>` lists end with `</ul>`, not `</p>`
- Common mistake: ending a list with `</ul></p>` when there's no opening `<p>` after the list
- Wrong:
```java
/**
 * <ul>
 * <li>Item one</li>
 * </ul>
 * </p>
 */
```
- Correct:
```java
/**
 * <ul>
 * <li>Item one</li>
 * </ul>
 */
```

### Lambda Expressions in JavaDoc Examples
- **NEVER** use lambda arrow syntax (`->`) in JavaDoc code examples - it causes HTML parsing errors
- Instead, use anonymous inner class syntax or escape the arrow
- Wrong: `list.forEach(item -> System.out.println(item));`
- Correct: `list.forEach(new Consumer() { public void accept(Object item) { ... } });`
- For comparisons, use `&gt;` entity: `if (value &gt; threshold)`

### Verification
Before committing, run `./mvnw javadoc:javadoc` to catch JavaDoc errors early.

- **Java 8 Features**: All new code must be Java 8 compatible; use streams, lambdas, and `Optional` where they enhance readability. NEVER use `String.repeat()` - use `StringUtils.repeat()` from Apache Commons. NEVER use `var`, `List.of()`, `Map.of()`, text blocks, or any Java 9+ syntax. See the critical Java 8 Compatibility section at the top of this document for complete list.
- **Validation Framework**: Use `SimulationValidator.validate(object)` before running simulations to catch configuration errors early. When extending equipment, override `validateSetup()` to add custom validation. See `neqsim.util.validation` package and docs/integration/ai_validation_framework.md.
- **AI-Friendly Error Handling**: Exceptions in `neqsim.util.exception` provide `getRemediation()` hints. When adding new errors, include actionable fix suggestions that AI agents can parse.
- **Troubleshooting**: When simulations fail (flash non-convergence, zero properties, recycle divergence), consult the `neqsim-troubleshooting` skill for ranked recovery strategies before retrying blindly.
- **Input Validation**: Before creating NeqSim objects, validate inputs using the `neqsim-input-validation` skill â€” catches physically impossible temperatures, pressures, compositions, and wrong component names.
- **Regression Baselines**: When modifying solver logic or property correlations, capture baseline values FIRST using the `neqsim-regression-baselines` skill. This prevents silent accuracy drift.
- **Standards Lookup**: For any engineering task, identify applicable industry standards using the `neqsim-standards-lookup` skill. It maps equipment types to standards (API, NORSOK, DNV, ISO, ASME), provides CSV database query patterns, and defines the `standards_applied` schema for results.json. Standards compliance is mandatory for all task scales.
- **Plant Data Integration**: When connecting NeqSim models to plant historian data (OSIsoft PI, Aspen IP.21), use the `neqsim-plant-data` skill for tagreader API patterns, tag mapping, digital twin loops, and data quality handling. See also the `@plant.data` agent.
- **Model Calibration and Data Reconciliation**: When reducing model-vs-plant mismatch, tuning parameters with bounded optimization, reconciling noisy measurements, or producing train/validation fit reports, use the `neqsim-model-calibration-and-data-reconciliation` skill.
- **API Changelog**: Check `CHANGELOG_AGENT_NOTES.md` in the repo root for recent API changes, new classes, deprecated methods, and known method name corrections.
- **Capability Assessment**: Before starting complex engineering tasks, use the `@capability.scout` agent or the `neqsim-capability-map` skill to identify what NeqSim can do, find gaps, and plan implementations. The result MUST be saved to `step1_scope_and_research/capability_assessment.md` (mandatory artifact for Standard/Comprehensive tasks).
- **Skill Discovery**: Run `python devtools/skill_search.py "<task title>" --top 5` at the start of any task to surface the most relevant skills via TF-IDF over the SKILL.md `description` fields. Prefer this over manual lookup in `skill-index.json` (which is now a curated short-list, not exhaustive).
- **Literature & Document Pull**: Use `@literature.scout` to fetch papers, standards, and internal STID/vendor docs into `step1_scope_and_research/references/`. The agent writes `references/manifest.json` and summarises sources into `notes.md`.
- **Pre-PR Quality Gate**: Before opening a PR for a task, invoke `@review <task folder>` (read-only). It wraps `validate_task_results.py`, `consistency_checker.py`, capability-assessment presence, figure→discussion traceability, and repo-memory hits, returning a PASS/WARN/FAIL grade.
- **Skills/Agents CI Lint**: `.github/workflows/skills_agents_lint.yml` runs `devtools/verify_skills_agents.py` (front-matter + skill-index reference check) and `devtools/generate_agent_skill_map.py` (auto-generates `docs/development/AGENT_SKILL_MAP.md`) on every PR touching `.github/skills/` or `.github/agents/`. The map is rebuilt from `Loaded skills:` lines in agent files; CI fails if the committed map is stale.
- **Flow Assurance**: For hydrate, wax, asphaltene, corrosion, or pipeline hydraulics analyses, use the `neqsim-flow-assurance` skill for comprehensive patterns covering all flow assurance threats with NeqSim code patterns. See also the `@flow.assurance` agent.
- **Water/Liquid Hammer**: For fast valve closure, ESD, pump trip, check-valve slam, hydraulic surge, or STID/tagreader-based surge screening, use the `neqsim-water-hammer` skill. Prefer `WaterHammerStudy` and MCP `runWaterHammer` for complete workflows that combine route geometry, field-data overrides, event schedules, pressure envelopes, and design-pressure validation.
- **CCS and Hydrogen**: For CO2 capture/transport/storage or hydrogen systems (blending, electrolysis, blue/green H2), use the `neqsim-ccs-hydrogen` skill for CO2 phase behavior, impurity management, injection well analysis, and H2 pipeline design. See also the `@ccs.hydrogen` agent.
- **Power Generation**: For gas turbines, steam turbines, HRSG, or combined cycle systems, use the `neqsim-power-generation` skill for equipment patterns and efficiency calculations.
- **Platform Process Modeling**: For building full topside process models of oil & gas platforms (FPSO, fixed, semi-sub) from design documents or operational data, use the `neqsim-platform-modeling` skill. Covers multi-stage separation with oil recycles, recompression trains with compressor curves and anti-surge, export/injection compression, scrubber liquid recovery, Cv-based valve flow, iteration strategies, and structured result extraction. Derived from 15+ production NCS platform models.
- **Technical Document and Image Reading**: For extracting data from PDFs, Word docs, Excel files, and engineering images (P&IDs, mechanical drawings, vendor API datasheets, compressor maps, phase envelopes), use the `neqsim-technical-document-reading` skill. Use `devtools/pdf_to_figures.py` to convert PDF pages to PNG images, then `view_image` for multimodal analysis of engineering drawings. See also the `@read technical documents` agent. The skill includes structured extraction patterns for P&ID topology (equipment/valve/instrument tags, piping), vendor datasheet operating conditions, mechanical arrangement dimensions, material certificates, trapped-liquid rupture evidence packs, and performance map digitization.
- **Vendor Document Retrieval**: For retrieving vendor documents (compressor curves, mechanical drawings, data sheets) for engineering tasks, use the `neqsim-stid-retriever` skill. Supports local directories, manual upload to `references/`, and pluggable retrieval backends (configured via gitignored `devtools/doc_retrieval_config.yaml`). Documents are classified by type, filtered by relevance to the task, and fed into the `neqsim-technical-document-reading` pipeline for data extraction.
- **Trapped-Liquid Fire Rupture Studies**: For blocked-in liquid, trapped liquid, thermal expansion rupture, no relief, flange/pipe rupture under fire, or PFP-demand studies, use the `neqsim-trapped-liquid-fire-rupture` skill. Retrieve P&IDs/STIDs, line lists, piping specs, material certificates, flange/bolt/gasket data, fire-zone/PFP documents, relief basis, and acceptance criteria before running `neqsim.process.safety.rupture` calculations. Report missing final-design evidence explicitly in `results.json` assumptions/gaps.
- **Auto-Validation for New Equipment**: When creating a new class that extends `ProcessEquipmentBaseClass`, ALWAYS generate a `validateSetup()` method that checks: (1) required input streams are connected, (2) required parameters are set and within valid ranges, (3) return `ValidationResult` with remediation hints for each issue.
- **Equipment Design Feasibility Reports**: After running compressors or heat exchangers in a process simulation, use the Design Feasibility Report classes to assess if equipment is realistic to build and operate. `CompressorDesignFeasibilityReport` (API 617 + cost + 15 OEM suppliers + curve generation) and `HeatExchangerDesignFeasibilityReport` (TEMA/ASME + cost + 14 HX suppliers) produce FEASIBLE / FEASIBLE_WITH_WARNINGS / NOT_FEASIBLE verdicts and comprehensive JSON reports. See `neqsim-api-patterns` skill for usage patterns.
- **Auto-Annotation for Public Methods**: When adding new public methods to core classes (SystemInterface, ProcessEquipmentInterface), consider adding `@AIExposable` annotation with description, category, example, and `@AIParameter` annotations documenting valid ranges/options.
- **Jupyter Notebook Examples**: When creating Jupyter notebook examples, ensure they run end-to-end and reflect the latest API changes; place them in the `notebooks/` directory and link to them from the main documentation. Follow the neqsim-python direct Java API bindings as shown at https://github.com/equinor/neqsim-python?tab=readme-ov-file#4-direct-java-access-full-control
- **Add markdown files with documentation**: When adding documentation as markdown files:
  1. Update `REFERENCE_MANUAL_INDEX.md` with the new file entry
  2. Update the relevant section's `index.md` (e.g., `docs/examples/index.md`)
  3. Verify ALL links to other docs using `file_search` before adding them
  4. See "Documentation Links (MANDATORY)" section below for link guidelines

## Markdown Documentation Guidelines (MANDATORY)

### Jekyll Front Matter (REQUIRED for Search)

**ALL markdown documentation files in `docs/` MUST have Jekyll YAML front matter** at the very beginning of the file for proper search indexing. Without front matter, files may not appear in search results with proper titles.

**Required format:**
```yaml
---
title: Your Document Title
description: A concise description (1-2 sentences) of what the document covers. Include key terms users might search for.
---
```

**Example for a thermodynamics guide:**
```yaml
---
title: Reading Fluid Properties in NeqSim
description: Comprehensive guide to calculating and reading thermodynamic and physical properties from fluids, phases, and components. Covers init levels, TPflash, density, enthalpy, viscosity, units, volume translation, and JSON reports.
---
```

**Rules:**
1. Front matter MUST be the first thing in the file (before any content)
2. Use three dashes `---` to delimit the YAML block
3. `title` should be descriptive but concise (appears in search results)
4. `description` should include searchable keywords relevant to the content
5. Do NOT duplicate the title as an H1 heading immediately after front matter (Jekyll handles this)
6. **CRITICAL: Quote values containing colons** - In YAML, colons have special meaning. If your title or description contains a colon (`:`), wrap the entire value in double quotes:
   ```yaml
   # WRONG - causes YAML parse error:
   title: PVT Workflow: From Lab Data to Model
   description: This guide covers: setup, configuration, and testing.

   # CORRECT - quoted values:
   title: "PVT Workflow: From Lab Data to Model"
   description: "This guide covers: setup, configuration, and testing."
   ```
7. **Avoid trailing colons** - Don't end descriptions with a colon (e.g., `description: "Features include:"`) - complete the sentence instead

**Files that already have front matter:** Check if they have both `title` and `description`. If missing `description`, add it.

When creating or editing markdown documentation files:

### HTML and Markdown Mixing Rules

**NEVER mix markdown syntax inside HTML block elements.** Many markdown parsers don't process markdown inside `<div>` tags.

| Problematic Pattern | Why It Fails | Solution |
|---------------------|--------------|----------|
| `<div>` containing markdown tables (`\|---\|`) | Parser ignores markdown inside HTML blocks | Use pure markdown OR pure HTML |
| `<div>` containing numbered lists (`1. Item`) | Lists don't render as lists | Remove div wrapper or use `<ol><li>` |
| `<div>` containing bullet lists (`- Item`) | Lists don't render as lists | Remove div wrapper or use `<ul><li>` |

### Correct Patterns

**For styled content boxes, choose ONE approach:**

1. **Pure Markdown (preferred for tables/lists):**
   ```markdown
   ### Section Title

   **Heading text:**

   | Column 1 | Column 2 |
   |----------|----------|
   | Data     | Data     |

   > *Note: Use blockquotes for callouts*
   ```

2. **Pure HTML (for complex styling):**
   ```html
   <div style="background: #e8f5e9; padding: 1rem;">
   <h4>Title</h4>
   <ul>
   <li>Item one</li>
   <li>Item two</li>
   </ul>
   </div>
   ```

### Table Formatting

- Always include a blank line before and after tables
- Use consistent column separator widths: `|----------|` not `|---|`
- Ensure header separator row has same column count as data rows

### List Formatting

- Always include a blank line before numbered/bullet lists
- For nested content after bold headers, add a blank line:
  ```markdown
  **Suggested Approach:**

  1. **Step one:** Description here
  2. **Step two:** Description here
  ```

### LaTeX Math Equations (MANDATORY for KaTeX Rendering)

The documentation site uses **KaTeX** for math rendering. Use the correct delimiters to ensure equations render properly.

#### Display Math (Block Equations)

**USE `$$...$$`** for display/block equations:

```markdown
The cubic equation of state:

$$
P = \frac{RT}{v - b} - \frac{a(T)}{(v + \epsilon b)(v + \sigma b)}
$$

Where $P$ is pressure and $T$ is temperature.
```

**NEVER use `\[...\]`** - these delimiters are often stripped by markdown processors and render as plain text like `[ P = \frac{RT}{v-b} ]`.

#### Inline Math

**USE `$...$`** for inline math:

```markdown
The acentric factor $\omega$ affects the alpha function $\alpha(T_r, \omega)$.
```

**NEVER use `\(...\)`** - these are less reliably rendered.

#### Common LaTeX Mistakes to Avoid

| Wrong | Correct | Issue |
|-------|---------|-------|
| `\[ P = \frac{RT}{v-b} \]` | `$$ P = \frac{RT}{v-b} $$` | `\[...\]` stripped by parser |
| `\(T_r\)` | `$T_r$` | `\(...\)` less reliable |
| `$$ P = ... $$ where` | `$$ P = ... $$` + newline + `where` | No text on same line as `$$` |
| Equation inside `<div>` | Move equation outside HTML block | Markdown not processed in HTML |

#### Verification

After adding equations, preview locally or check that:
1. Display equations appear centered on their own line
2. Inline math renders within the text flow
3. No raw LaTeX syntax (backslashes, braces) appears in rendered output

### Documentation Links (MANDATORY)

When adding links to other documentation files, follow these rules to prevent broken links:

#### Link Verification Rules

1. **ALWAYS verify target files exist** before adding links:
   - Use `file_search` to confirm the file exists in the repository
   - Check the exact path and filename (case-sensitive on some systems)

2. **Use correct relative paths** based on the source file location:
   - From `docs/fielddevelopment/` to `docs/process/`: use `../process/filename.md`
   - From `docs/examples/` to `docs/tutorials/`: use `../tutorials/filename.md`
   - Within same folder: use just `filename.md`

3. **Prefer existing documentation** over creating placeholder links:
   - If a linked file doesn't exist, either create it OR link to an existing alternative
   - NEVER add links to files that don't exist

#### Common Documentation Paths

| Documentation Area | Path | Example Files |
|--------------------|------|---------------|
| Process equipment | `docs/process/` | `separators.md`, `compressors.md`, `heat-exchangers.md` |
| Field development | `docs/fielddevelopment/` | `pressure_boundary_optimization.md`, `CAPACITY_CONSTRAINT_FRAMEWORK.md` |
| Thermodynamics | `docs/thermo/` | `equations-of-state.md`, `flash-calculations.md` |
| Examples | `docs/examples/` | `*.ipynb`, `*.java`, `index.md` |
| Tutorials | `docs/tutorials/` | Getting started guides |
| Troubleshooting | `docs/troubleshooting/` | Common issues and solutions |

#### When Adding New Documentation

1. **Update index files** when creating new documentation:
   - Add entry to `docs/REFERENCE_MANUAL_INDEX.md` (master index of 360+ files)
   - Add entry to the relevant section's `index.md` (e.g., `docs/examples/index.md`)

2. **Cross-reference related docs** with verified links:
   ```markdown
   ## Related Documentation

   - [Pressure Boundary Optimization](pressure_boundary_optimization.md)
   - [Capacity Constraint Framework](CAPACITY_CONSTRAINT_FRAMEWORK.md)
   ```

3. **For Jupyter notebooks**, also add to:
   - `docs/examples/index.md` - Examples index
   - `docs/REFERENCE_MANUAL_INDEX.md` - Master reference

#### Link Format Examples

```markdown
<!-- Same directory -->
[Related Topic](related-topic.md)

<!-- Parent directory -->
[Process Overview](../process/index.md)

<!-- Sibling directory -->
[Tutorial](../tutorials/getting-started.md)

<!-- Link to specific section -->
[VFP Tables](pressure_boundary_optimization.md#vfp-generation)

<!-- Link to Java example -->
[Java Example](MultiScenarioVFPExample.java)

<!-- Link to notebook -->
[Notebook Tutorial](ProductionSystem_BottleneckAnalysis.ipynb)
```

#### Broken Link Prevention Checklist

Before finalizing documentation with links:

- [ ] All linked `.md` files exist (use `file_search("**/filename.md")`)
- [ ] All linked `.ipynb` notebooks exist
- [ ] All linked `.java` examples exist
- [ ] Relative paths are correct for source file location
- [ ] Index files updated for new documentation
- [ ] No links to planned-but-not-created files

## Mechanical Design & Well Design (MANDATORY)

> **Full patterns are in skills â€” load them before implementing:**
> - `neqsim-api-patterns` â€” Equipment design feasibility reports, separator mechanical design, cost estimation
> - `neqsim-subsea-and-wells` â€” Well casing design (API 5C3), SURF cost, barrier verification (NORSOK D-010)
> - `neqsim-standards-lookup` â€” Industry standards mapping (ASME, API, DNV, ISO, NORSOK)
>
> **Architecture:** MechanicalDesign class + DataSource class + Calculator class + JSON reporting.
> Physical dimensions and internals are configured through `SeparatorMechanicalDesign`, NOT directly on `Separator`.
> See `AGENTS.md` "Separator MechanicalDesign Architecture" and "Well Mechanical Design" sections for details.

<!--
  The ~500 lines of mechanical design code templates and well design patterns that were here
  have been moved to the neqsim-api-patterns, neqsim-subsea-and-wells, and neqsim-standards-lookup
  skill files. Load those skills before implementing mechanical design.
-->
  - Location: `designdata/MaterialPipeProperties.csv`, `designdata/MaterialPlateProperties.csv`, etc.
  - Required columns: `MaterialGrade`, `SMYS_psi`, `SMTS_psi`, `Density_kg_m3`, `Standard`

- **TechnicalRequirements_Process.csv**: Equipment-specific design parameters by company
  - Required columns: `Company`, `EquipmentType`, `ParameterName`, `Value`, `Unit`, `Standard`

- **TechnicalRequirements_Piping.csv**: Piping code-specific design values
  - Required columns: `Code`, `ParameterName`, `Value`, `Unit`, `Description`

- **Standards Tables** (in `designdata/standards/` subdirectory):
  - `api_standards.csv` - API standard parameters
  - `asme_standards.csv` - ASME code requirements
  - `dnv_iso_en_standards.csv` - DNV/ISO/EN requirements
  - `norsok_standards.csv` - NORSOK requirements
  - `standards_index.csv` - Index mapping equipment types to applicable standards

---

## Jupyter Notebook Creation Guidelines

> **Full patterns are in the `neqsim-notebook-patterns` skill.** Load it before creating notebooks.

**Key rules (always apply):**
- Use `devtools/neqsim_dev_setup.py` for task notebooks and runner workflows:
   call `neqsim_init(project_root=PROJECT_ROOT, ...)`, then use classes through
   `ns.*` or `ns.JClass(...)`. Do not use `from neqsim import jneqsim` in
   repository task notebooks because it can load a stale installed package.
- Temperature in **Kelvin**, pressure in **bara** by default in Java API
- **Always** set mixing rule: `fluid.setMixingRule("classic")`
- Call `fluid.initProperties()` after flash before reading transport properties
- **Every notebook MUST be executed** — use NeqSim Runner by default for task
   notebooks; unexecuted notebooks are incomplete
- Include **2-3 matplotlib figures** with axis labels, units, titles, legends, grids
- Save results to `results.json` in the task folder

See `AGENTS.md` "Jupyter Notebook Creation Guidelines" section for common class import paths and the full getting-results reference.

## Task-Solving Workflow (MANDATORY)

> **Full workflow is in `docs/development/TASK_SOLVING_GUIDE.md`.** Read it before starting any task.
> Past solved tasks are indexed in `docs/development/TASK_LOG.md` — search before starting from scratch.

**Key rules (always apply):**
1. **Create task folder FIRST:** `neqsim new-task "title" --type X --author "Name"`
2. **All output goes to** `task_solve/YYYY-MM-DD_slug/` — never to `examples/`, `docs/`, or workspace root
3. **All downloaded documents** go inside the task folder at `step1_scope_and_research/references/`
4. Follow the 3-step workflow: **Scope & Research** → **Analysis & Evaluation** → **Report**
5. **Benchmark validation (MANDATORY):** Compare NeqSim results against independent reference data
6. **Uncertainty analysis:** Monte Carlo with P10/P50/P90 + tornado diagram (MANDATORY for Standard/Comprehensive tasks with economics or reserves; optional for Quick tasks)
7. **Risk evaluation:** Risk register with ISO 31000 5×5 matrix (MANDATORY for Standard/Comprehensive tasks; optional for Quick tasks)
8. **Consistency check:** Run `python devtools/consistency_checker.py` before generating reports
9. **After completing:** Add entry to `docs/development/TASK_LOG.md`

### Task Log Entry Format

**Privacy rule:** Task log entries are public/reusable memory. Do not include
company/operator names, field/facility/asset names, equipment tag numbers,
internal document names, private system names, access diagnostics, or task folder
slugs containing those details. Use generic descriptors and `private task folder
(redacted)` for confidential task outputs.

```markdown
### YYYY-MM-DD — Short task title
**Type:** A (Property) | B (Process) | C (PVT) | D (Standards) | E (Feature) | F (Design) | G (Workflow) | G (Workflow)
**Keywords:** comma, separated, search, terms
**Solution:** path/to/test/or/notebook
**Notes:** Key decisions, gotchas, or results
```
