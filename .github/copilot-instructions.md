# NeqSim AI Guidance for Coding Agents

## ⚠️ CRITICAL: Java 8 Compatibility (READ FIRST)

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
  Copy-Item -Path "C:\Users\ESOL\Documents\GitHub\neqsim2\target\neqsim-3.3.0.jar" -Destination "C:\Users\ESOL\AppData\Roaming\Python\Python312\site-packages\neqsim\lib\java11\" -Force
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

---

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
- **Auto-Validation for New Equipment**: When creating a new class that extends `ProcessEquipmentBaseClass`, ALWAYS generate a `validateSetup()` method that checks: (1) required input streams are connected, (2) required parameters are set and within valid ranges, (3) return `ValidationResult` with remediation hints for each issue.
- **Auto-Annotation for Public Methods**: When adding new public methods to core classes (SystemInterface, ProcessEquipmentInterface), consider adding `@AIExposable` annotation with description, category, example, and `@AIParameter` annotations documenting valid ranges/options.
- **Jupyter Notebook Examples**: When creating Jupyter notebook examples, ensure they run end-to-end and reflect the latest API changes; place them in the `notebooks/` directory and link to them from the main documentation. Follow the neqsim-python direct Java API bindings as shown at https://github.com/equinor/neqsim-python?tab=readme-ov-file#4-direct-java-access-full-control
- **Add markdown files with documentation**: When adding documentation as markdown files be sure to update REFERENCE_MANUAL_INDEX.md applyTo: **/*.md

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

## Mechanical Design Implementation Pattern (MANDATORY)

When implementing mechanical design for any process equipment, follow this established architecture pattern:

### 1. Database Tables (CSV Files)

Create or update CSV files in `src/main/resources/designdata/` for design data:

- **MaterialProperties CSV**: Store material grades with mechanical properties (SMYS, SMTS, density, etc.)
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

### 2. MechanicalDesign Class

Create a class extending `MechanicalDesign` in `neqsim.process.mechanicaldesign.<equipment>/`:

```java
public class VesselMechanicalDesign extends MechanicalDesign {
    private VesselMechanicalDesignDataSource dataSource;
    private VesselDesignCalculator calculator;
    private String materialGrade = "SA-516-70";
    private String designStandardCode = "ASME-VIII-Div1";
    
    public VesselMechanicalDesign(ProcessEquipmentInterface equipment) {
        super(equipment);
        this.dataSource = new VesselMechanicalDesignDataSource();
        this.calculator = new VesselDesignCalculator();
    }
    
    @Override
    public void readDesignSpecifications() {
        dataSource.loadIntoCalculator(calculator, 
            getCompanySpecificDesignStandards(), designStandardCode, "Vessel");
    }
    
    @Override
    public void calcDesign() {
        calculator.setDesignPressure(getMaxOperationPressure() * 1.1);
        calculator.setDesignTemperature(getMaxOperationTemperature());
        calculator.calculate();
        setWallThickness(calculator.getRequiredWallThickness());
    }
    
    @Override
    public String toJson() {
        // Return comprehensive JSON with all design data
    }
}
```

### 3. DataSource Class

Create a data source class for database queries in `neqsim.process.mechanicaldesign.<equipment>/`:

```java
public class VesselMechanicalDesignDataSource {
    
    public void loadIntoCalculator(VesselDesignCalculator calc, 
            String company, String designCode, String equipmentType) {
        // Query TechnicalRequirements_Process for company-specific values
        // Query standards tables based on designCode
        // Set values on calculator
    }
    
    public void loadFromStandardsTable(VesselDesignCalculator calc, 
            String designCode) {
        // Query appropriate standards table (asme_standards, dnv_iso_en_standards, etc.)
        // Apply standard-specific design factors
    }
}
```

### 4. Calculator Class

Create a calculator class with standards-based formulas:

```java
public class VesselDesignCalculator {
    private double designPressure;
    private double designTemperature;
    private double innerDiameter;
    private String materialGrade;
    private double smys;  // Specified Minimum Yield Strength
    private double jointEfficiency;
    private double designFactor;
    
    public void calculate() {
        calculateWallThicknessASME();
        calculateStresses();
        checkDesignMargins();
    }
    
    public Map<String, Object> toMap() {
        // Return all calculation results as Map
    }
    
    public String toJson() {
        return new GsonBuilder().setPrettyPrinting().create()
            .toJson(toMap());
    }
}
```

### 5. JSON Reporting (MANDATORY)

All mechanical design classes MUST implement `toJson()` returning comprehensive design data:

```java
@Override
public String toJson() {
    MechanicalDesignResponse response = new MechanicalDesignResponse(this);
    JsonObject jsonObj = JsonParser.parseString(response.toJson()).getAsJsonObject();
    JsonObject calcObj = JsonParser.parseString(calculator.toJson()).getAsJsonObject();
    
    // Add equipment-specific fields
    jsonObj.addProperty("materialGrade", materialGrade);
    jsonObj.addProperty("designStandardCode", designStandardCode);
    jsonObj.add("designCalculations", calcObj);
    
    return new GsonBuilder().setPrettyPrinting()
        .serializeSpecialFloatingPointValues().create().toJson(jsonObj);
}
```

### 6. Database Loading

Ensure `NeqSimProcessDesignDataBase.initH2DatabaseFromCSVfiles()` loads your CSV tables:
- Main tables from `designdata/`
- Standards tables from `designdata/standards/`

### 7. Required Industry Standards

When adding design standards, include values from:
- **ASME**: B31.3 (Process Piping), B31.4 (Liquid Pipelines), B31.8 (Gas Pipelines), Section VIII (Vessels)
- **API**: 5L (Line Pipe), 650 (Storage Tanks), 510 (Inspection), 620 (Low-Pressure Tanks)
- **DNV**: ST-F101 (Submarine Pipelines), OS-F101, RP-C203 (Fatigue)
- **ISO**: 13623 (Pipelines), 3183 (Line Pipe), 14692 (GRP Piping)
- **NORSOK**: L-001/L-002 (Piping), M-001/M-630 (Materials), P-001 (Process)

### 8. TR Document Specifications (Company Technical Requirements)

TR (Technical Requirement) documents are company-specific design specifications that override or supplement industry standards. When implementing mechanical design, handle TR documents as follows:

#### TR Document Structure in Database

Create entries in `TechnicalRequirements_Process.csv` with TR document references:

```csv
Company,EquipmentType,ParameterName,Value,Unit,Standard
Equinor,Pipeline,DesignFactor,0.72,,TR1414
Equinor,Pipeline,CorrosionAllowance,3.0,mm,TR1414
Equinor,Separator,DesignPressureMargin,1.1,,TR2000
Equinor,Separator,MinWallThickness,6.0,mm,TR2000
Equinor,HeatExchanger,FoulingFactor,0.0002,m2K/W,TR3100
```

#### TR Document CSV Table (Optional)

For complex TR requirements, create a dedicated `designdata/tr_documents.csv`:

```csv
Company,TRNumber,TRTitle,EquipmentType,ParameterName,Value,Unit,Section,ValidFrom
Equinor,TR1414,Piping and Pipeline Design,Pipeline,DesignFactor,0.72,,5.2.1,2020-01-01
Equinor,TR1414,Piping and Pipeline Design,Pipeline,LocationClassDefault,Class 1,,5.2.2,2020-01-01
Equinor,TR2000,Pressure Vessel Design,Separator,JointEfficiency,0.85,,4.3.1,2019-06-01
Equinor,TR2000,Pressure Vessel Design,Separator,CorrosionAllowance,3.0,mm,4.4.2,2019-06-01
Shell,DEP-31.38.01.11,Pressure Vessels,Separator,DesignPressureMargin,1.1,,3.2,2021-01-01
```

#### DataSource Implementation for TR Documents

```java
public class VesselMechanicalDesignDataSource {
    
    public void loadTRRequirements(VesselDesignCalculator calc, 
            String company, String equipmentType) {
        try (Connection conn = NeqSimProcessDesignDataBase.createConnection()) {
            // Query TR-specific requirements
            String sql = "SELECT ParameterName, Value, Unit, Standard " +
                         "FROM TechnicalRequirements_Process " +
                         "WHERE Company = ? AND EquipmentType = ? " +
                         "AND Standard LIKE 'TR%'";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, company);
            stmt.setString(2, equipmentType);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String param = rs.getString("ParameterName");
                double value = rs.getDouble("Value");
                String trDoc = rs.getString("Standard");
                
                // Apply TR-specific values (override industry standards)
                applyTRParameter(calc, param, value, trDoc);
            }
        }
    }
    
    private void applyTRParameter(VesselDesignCalculator calc, 
            String param, double value, String trDoc) {
        // TR values take precedence over industry standards
        switch (param) {
            case "DesignFactor":
                calc.setDesignFactor(value);
                calc.addAppliedStandard(trDoc + " - Design Factor");
                break;
            case "CorrosionAllowance":
                calc.setCorrosionAllowance(value);
                calc.addAppliedStandard(trDoc + " - Corrosion Allowance");
                break;
            // ... other parameters
        }
    }
}
```

#### TR Document Hierarchy (Priority Order)

When loading design parameters, apply in this order (later overrides earlier):

1. **Industry Standards** (ASME, API, DNV, ISO, NORSOK) - Base values
2. **Company Standards** (TechnicalRequirements_Process) - Company defaults
3. **TR Documents** - Specific technical requirements
4. **Project-Specific** - User-provided overrides

```java
@Override
public void readDesignSpecifications() {
    // 1. Load industry standard defaults
    dataSource.loadFromStandardsTable(calculator, designStandardCode);
    
    // 2. Load company-specific values
    dataSource.loadIntoCalculator(calculator, 
        getCompanySpecificDesignStandards(), designStandardCode, equipmentType);
    
    // 3. Load TR document requirements (highest priority)
    dataSource.loadTRRequirements(calculator, 
        getCompanySpecificDesignStandards(), equipmentType);
}
```

#### JSON Reporting with TR References

Include TR document references in JSON output for traceability:

```java
@Override
public String toJson() {
    // ... existing code ...
    
    // Add TR document references
    JsonArray appliedStandards = new JsonArray();
    for (String std : calculator.getAppliedStandards()) {
        appliedStandards.add(std);
    }
    jsonObj.add("appliedStandards", appliedStandards);
    jsonObj.addProperty("trDocumentReference", trDocumentNumber);
    
    return gson.toJson(jsonObj);
}
```

#### Common TR Document Types by Company

| Company | TR Series | Scope |
|---------|-----------|-------|
| Equinor | TR1000-series | Process & Safety |
| Equinor | TR1400-series | Piping & Pipelines |
| Equinor | TR2000-series | Pressure Vessels |
| Equinor | TR3000-series | Heat Transfer Equipment |
| Shell | DEP | Design & Engineering Practice |
| TotalEnergies | GS EP | General Specifications |
| BP | GP | Group Practices |

### 9. Cost Estimation and Detailed Design (MANDATORY for Full Design)

All mechanical design calculator classes should include cost estimation and detailed design capabilities:

#### Required Cost Estimation Features

```java
// Weight calculations
public void calculateWeightsAndAreas();       // Steel, coating, insulation weights
public double calculateSubmergedWeight(double contentDensity);  // For subsea

// Quantity calculations  
public void calculateJointsAndWelds();        // Number of joints, welds
public int selectFlangeClass();               // Per ASME B16.5

// Cost calculations
public double calculateProjectCost();         // Complete cost estimate
public double calculateLaborManhours();       // Labor estimation
public java.util.List<Map<String, Object>> generateBillOfMaterials();  // BOM
```

#### Required Detailed Design Features

```java
// Structural design
public double calculateSupportSpacing(double maxDeflection);
public double calculateExpansionLoopLength(double deltaT, String loopType);
public double calculateMinimumBendRadius();

// Subsea/buried design
public double calculateCollapsePressure();
public double calculatePropagationBucklingPressure();
public double calculateAllowableSpanLength(double currentVelocity);
public double estimateFatigueLife(double stressRange, double cycles);

// Thermal design
public double calculateInsulationThickness(double inletT, double arrivalT, 
    double massFlow, double specificHeat);
```

#### JSON Output Structure for Cost Estimation

The `toMap()` method must include these sections:

```java
result.put("weightAndBuoyancy", weightData);      // Weights per meter and total
result.put("surfaceAreas", surfaceData);          // For coating/painting
result.put("jointsAndWelds", jointsData);         // Fabrication quantities
result.put("flangesAndConnections", connectionsData);
result.put("supportsAndExpansion", supportsData);
result.put("detailedDesignResults", detailedDesign); // Buckling, fatigue, etc.
result.put("installationParameters", installData);
result.put("costEstimation", costData);           // All cost breakdown
result.put("laborEstimation", laborData);
result.put("costRateAssumptions", rateData);      // Unit rates used
```

#### Standard Data Tables for Cost Estimation

Create or update these CSV tables in `designdata/`:

- **MaterialPrices.csv**: Steel prices per grade, coating prices
- **InstallationRates.csv**: Cost per meter by installation method
- **LaborRates.csv**: Manhours and hourly rates by activity
- **EquipmentRates.csv**: Valve, flange, fitting costs by class and size

#### Example with Cost Estimation

```java
// Pipeline with cost estimation
AdiabaticPipe pipe = new AdiabaticPipe("Export Pipeline", feed);
pipe.setLength(50000.0); // 50 km
pipe.setDiameter(0.508); // 20 inch

PipelineMechanicalDesign design = (PipelineMechanicalDesign) pipe.getMechanicalDesign();
design.setMaxOperationPressure(150.0);
design.setMaterialGrade("X65");
design.setDesignStandardCode("DNV-OS-F101");
design.getCalculator().setInstallationMethod("S-lay");
design.getCalculator().setWaterDepth(350.0);
design.getCalculator().setNumberOfValves(5);
design.getCalculator().setNumberOfFlangePairs(10);

// Run full design and cost
design.calcDesign();
design.getCalculator().calculateProjectCost();
design.getCalculator().calculateLaborManhours();

// Get complete report
String json = design.toJson();
List<Map<String, Object>> bom = design.getCalculator().generateBillOfMaterials();
```

### Example Usage Pattern

```java
// Initialize equipment
Separator separator = new Separator("HP Separator", feed);
separator.initMechanicalDesign();

// Configure design
SeparatorMechanicalDesign design = (SeparatorMechanicalDesign) separator.getMechanicalDesign();
design.setMaxOperationPressure(85.0);
design.setMaxOperationTemperature(273.15 + 80.0);
design.setMaterialGrade("SA-516-70");
design.setDesignStandardCode("ASME-VIII-Div1");
design.setCompanySpecificDesignStandards("Equinor");

// Calculate and report
design.readDesignSpecifications();
design.calcDesign();
String jsonReport = design.toJson();
```

---

## Jupyter Notebook Creation Guidelines (MANDATORY)

When creating Jupyter notebooks for NeqSim examples, follow these critical patterns:

### Import Pattern (USE THIS EXACT PATTERN)

**CORRECT** - Use the `jneqsim` gateway for direct Java access:
```python
# Import NeqSim - Direct Java Access via jneqsim
from neqsim import jneqsim

# Import commonly used Java classes through the jneqsim gateway
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.heatexchanger.Cooler
Heater = jneqsim.process.equipment.heatexchanger.Heater
```

**WRONG** - Do NOT use jpype imports directly for new notebooks:
```python
# WRONG - This pattern may not work reliably
import jpype
import jpype.imports
from jpype.types import *
if not jpype.isJVMStarted():
    jpype.startJVM(classpath=['../target/classes'])
from neqsim.thermo.system import SystemSrkEos  # Direct jpype import
```

### Common Problems and Solutions

| Problem | Cause | Solution |
|---------|-------|----------|
| `ModuleNotFoundError: No module named 'neqsim'` | neqsim-python not installed | Run `pip install neqsim` |
| `AttributeError: 'NoneType' object has no attribute...` | JVM not started | Use `from neqsim import jneqsim` which auto-starts JVM |
| `TypeError: No matching overloads found` | Wrong parameter types | Cast Python types explicitly: `float(value)`, `str(name)` |
| Import fails for nested classes | Wrong import path | Use `jneqsim.package.subpackage.ClassName` pattern |
| `RuntimeError: JVM cannot be restarted` | JVM already started/stopped | Restart the kernel |

### Temperature and Pressure Units

**CRITICAL**: NeqSim Java API uses **Kelvin** for temperatures and **bara** for pressures by default!

```python
# Creating fluid - temperature in KELVIN, pressure in bara
fluid = SystemSrkEos(273.15 + 25.0, 60.0)  # 25°C, 60 bara

# Setting stream conditions - can use unit strings
stream.setTemperature(30.0, "C")    # Celsius
stream.setPressure(60.0, "bara")    # bara

# Getting values - returns KELVIN
temp_kelvin = stream.getTemperature()
temp_celsius = stream.getTemperature() - 273.15  # Convert to Celsius

# WRONG - forgetting Kelvin conversion
temp = fluid.getTemperature()  # This is in Kelvin!
print(f"Temperature: {temp}°C")  # WRONG - will show ~298°C instead of 25°C
```

### Required Steps for Fluid Creation

Always follow this sequence:
```python
# 1. Create fluid with (T_Kelvin, P_bara)
fluid = SystemSrkEos(273.15 + 25.0, 60.0)

# 2. Add components (name, mole_fraction)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)

# 3. MANDATORY: Set mixing rule
fluid.setMixingRule("classic")

# 4. Optional but recommended for custom components
# fluid.createDatabase(True)
```

**NEVER skip the mixing rule** - simulations will fail or give wrong results without it.

### Process Equipment Connections

```python
# Create process system
process = ProcessSystem("My Process")

# Feed stream
feed = Stream("Feed", fluid)
feed.setFlowRate(50000.0, "kg/hr")
process.add(feed)

# Equipment takes inlet stream in constructor
separator = Separator("HP Sep", feed)  # Stream object, not name
process.add(separator)

# Get outlet streams from equipment
gas_out = separator.getGasOutStream()      # Gas phase
liquid_out = separator.getLiquidOutStream() # Liquid phase

# Connect to next equipment
compressor = Compressor("Comp", gas_out)
process.add(compressor)

# For equipment outlet
next_stream = compressor.getOutletStream()
```

### Common Class Import Paths

```python
# Thermo systems
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
SystemPrEos = jneqsim.thermo.system.SystemPrEos
SystemSrkCPAstatoil = jneqsim.thermo.system.SystemSrkCPAstatoil

# Process equipment
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
ThreePhaseSeparator = jneqsim.process.equipment.separator.ThreePhaseSeparator
Compressor = jneqsim.process.equipment.compressor.Compressor
Expander = jneqsim.process.equipment.expander.Expander
Heater = jneqsim.process.equipment.heatexchanger.Heater
Cooler = jneqsim.process.equipment.heatexchanger.Cooler
HeatExchanger = jneqsim.process.equipment.heatexchanger.HeatExchanger
Mixer = jneqsim.process.equipment.mixer.Mixer
Splitter = jneqsim.process.equipment.splitter.Splitter
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve
Recycle = jneqsim.process.equipment.util.Recycle
Adjuster = jneqsim.process.equipment.util.Adjuster

# Pipes
AdiabaticPipe = jneqsim.process.equipment.pipeline.AdiabaticPipe
PipeBeggsAndBrills = jneqsim.process.equipment.pipeline.PipeBeggsAndBrills

# Distillation
DistillationColumn = jneqsim.process.equipment.distillation.DistillationColumn
```

### Getting Results

```python
# After running process
process.run()

# Stream properties
stream.getTemperature()         # Kelvin
stream.getPressure()            # bara
stream.getFlowRate("kg/hr")     # Mass flow with unit

# Fluid properties
fluid = stream.getFluid()
fluid.getDensity("kg/m3")       # Density
fluid.getMolarMass("kg/mol")    # Molar mass
fluid.getZ()                    # Compressibility factor
fluid.getNumberOfPhases()       # Phase count
fluid.hasPhaseType("gas")       # Check phase presence

# Compressor
comp.getPower("kW")             # Power consumption
comp.getOutletStream()          # Outlet stream

# Heat exchangers
heater.getDuty()                # Heat duty in Watts
cooler.getDuty()                # Cooling duty (positive = heat removed)
```

### Notebook Structure Best Practice

1. **Introduction cell** - Describe what the notebook demonstrates
2. **Setup cell** - All imports in one cell
3. **Fluid creation** - Separate cell for creating thermodynamic system
4. **Process building** - Build process step by step
5. **Run simulation** - Single `process.run()` call
6. **Results** - Extract and display results
7. **Visualization** - Plots using matplotlib
8. **Tips/Next steps** - Summary and links to related examples

### Type Conversion for Java

When passing values to Java methods, ensure correct types:
```python
# Explicitly convert to float for numeric parameters
pressure = 60.0  # Already float
comp.setOutletPressure(float(pressure))  # Explicit conversion if unsure

# String parameters
stream.setFlowRate(50000.0, str("kg/hr"))  # Usually not needed but safe

# Boolean
fluid.setMultiPhaseCheck(True)  # Python bool works
```

---
