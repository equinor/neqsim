# NeqSim AI Guidance for Coding Agents

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
| "semicolon missing" | Malformed HTML in JavaDoc | Check HTML tag closure |

### Verification
Before committing, run `./mvnw javadoc:javadoc` to catch JavaDoc errors early.

- **Java 8 Features**: All new code must be Java 8 compatible; use streams, lambdas, and `Optional` where they enhance readability. NEVER use `String.repeat()` - use `StringUtils.repeat()` from Apache Commons. NEVER use `var`, `List.of()`, `Map.of()`, text blocks, or any Java 9+ syntax.
- **Validation Framework**: Use `SimulationValidator.validate(object)` before running simulations to catch configuration errors early. When extending equipment, override `validateSetup()` to add custom validation. See `neqsim.util.validation` package and docs/integration/ai_validation_framework.md.
- **AI-Friendly Error Handling**: Exceptions in `neqsim.util.exception` provide `getRemediation()` hints. When adding new errors, include actionable fix suggestions that AI agents can parse.
- **Auto-Validation for New Equipment**: When creating a new class that extends `ProcessEquipmentBaseClass`, ALWAYS generate a `validateSetup()` method that checks: (1) required input streams are connected, (2) required parameters are set and within valid ranges, (3) return `ValidationResult` with remediation hints for each issue.
- **Auto-Annotation for Public Methods**: When adding new public methods to core classes (SystemInterface, ProcessEquipmentInterface), consider adding `@AIExposable` annotation with description, category, example, and `@AIParameter` annotations documenting valid ranges/options.
- **Jupyter Notebook Examples**: When creating Jupyter notebook examples, ensure they run end-to-end and reflect the latest API changes; place them in the `notebooks/` directory and link to them from the main documentation. Follow the neqsim-python direct Java API bindings as shown at https://github.com/equinor/neqsim-python?tab=readme-ov-file#4-direct-java-access-full-control
- **Add markdown files with documentation**: When adding documentation as markdown files be sure to update REFERENCE_MANUAL_INDEX.md applyTo: **/*.md

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

