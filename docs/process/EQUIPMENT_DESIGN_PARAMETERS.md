# Equipment Design Parameters Guide

This guide describes how to manually set design parameters for process equipment in NeqSim when not using `autoSizeEquipment()`. Understanding these parameters is essential for accurate capacity utilization tracking and bottleneck analysis.

> **📘 Related Documentation**
>
> | Topic | Documentation |
> |-------|---------------|
> | **Mechanical Design** | [mechanical_design.md](mechanical_design.md) - Equipment sizing, weights, JSON export |
> | **Constraints & Optimization** | [optimization/OPTIMIZATION_AND_CONSTRAINTS.md](optimization/OPTIMIZATION_AND_CONSTRAINTS.md) - Complete optimization guide |
> | **Capacity Constraints** | [CAPACITY_CONSTRAINT_FRAMEWORK.md](CAPACITY_CONSTRAINT_FRAMEWORK.md) - Multi-constraint bottleneck detection |
> | **Cost Estimation** | [COST_ESTIMATION_FRAMEWORK.md](COST_ESTIMATION_FRAMEWORK.md) - CAPEX, OPEX, financial metrics |

## Table of Contents

- [Overview](#overview)
- [Capacity Utilization Quick Reference](#capacity-utilization-quick-reference)
- [autoSize vs MechanicalDesign](#autosize-vs-mechanicaldesign)
- [How to Override autoSize Results](#how-to-override-autosize-results)
- [Separator Design Parameters](#separator-design-parameters)
- [Pipe/Pipeline Design Parameters](#pipepipeline-design-parameters)
- [Compressor Design Parameters](#compressor-design-parameters)
- [Heat Exchanger Design Parameters](#heat-exchanger-design-parameters)
- [Valve Design Parameters](#valve-design-parameters)
- [Pump Design Parameters](#pump-design-parameters)
- [Using autoSizeEquipment() vs Manual Sizing](#using-autosizeequipment-vs-manual-sizing)
- [Capacity Constraints and Utilization](#capacity-constraints-and-utilization)

---

## Overview

NeqSim equipment can be configured in two ways:

1. **Manual Design**: Set specific design parameters before running
2. **Auto-Sizing**: Call `autoSizeEquipment()` after running to size based on actual flow rates

### When to Use Manual Design

- Known equipment dimensions from vendor data sheets
- Existing equipment with fixed sizes
- Design verification against specific standards
- Scenario analysis with fixed equipment

### When to Use Auto-Sizing

- Greenfield design where equipment sizes are unknown
- Quick feasibility studies
- Capacity studies where equipment should match flow rates

---

## Capacity Utilization Quick Reference

This table summarizes how capacity utilization is calculated for each equipment type:

| Equipment | Utilization Formula | Duty Metric | Capacity Metric | Override Design Methods |
|-----------|--------------------|--------------|-----------------|-----------------------|
| **Separator** | `gasFlow / maxAllowableGasFlow` | Gas volumetric flow (m³/s) | K-factor × area × density function | `setDesignGasLoadFactor()`, `setInternalDiameter()` |
| **Compressor** | `power / maxPower` | Shaft power (W) | Driver power or design power | `setMaximumPower()`, `setMaximumSpeed()` |
| **Pump** | `power / maxPower` | Shaft power (W) | Design power | `getMechanicalDesign().setMaxDesignVolumeFlow()` |
| **ThrottlingValve** | `volumeFlow / maxVolumeFlow` | Outlet flow (m³/hr) | Design Cv × conditions | `setDesignCv()`, `setDesignVolumeFlow()` |
| **Heater/Cooler** | `duty / maxDuty` | Heat duty (W) | Max design duty | `getMechanicalDesign().setMaxDesignDuty()` |
| **Pipe/Pipeline** | `volumeFlow / maxVolumeFlow` | Volume flow (m³/hr) | Area × design velocity | `setMaxDesignVelocity()`, `setMaxDesignVolumeFlow()` |
| **Manifold** | `velocity / maxVelocity` | Header/branch velocity (m/s) | Design velocity limits | `setDesignHeaderVelocity()`, `setDesignBranchVelocity()` |

### Utilization Interpretation

| Value | Meaning |
|-------|---------|
| 0.0 - 0.8 | Normal operation with headroom |
| 0.8 - 0.95 | Approaching design limits |
| 0.95 - 1.0 | At design capacity |
| > 1.0 | **Overloaded** - exceeds design |

---

## How to Override autoSize Results

After calling `autoSize()`, you can override specific design parameters while keeping others:

### Option 1: Override Before autoSize (Preferred)

```java
// Set your custom values first - they will be respected by autoSize
separator.setDesignGasLoadFactor(0.15);  // Custom K-factor (won't be overwritten)
separator.autoSize(1.2);                  // Sizes diameter/length using your K-factor
```

### Option 2: Override After autoSize

```java
// Auto-size first
separator.autoSize(1.2);

// Then override specific parameters
separator.setInternalDiameter(3.0);  // Override calculated diameter
// Note: This changes capacity but keeps other design parameters
```

### Option 3: Manual Sizing (Skip autoSize)

```java
// Set all parameters manually - don't call autoSize
separator.setInternalDiameter(2.5);
separator.setSeparatorLength(8.0);
separator.setDesignGasLoadFactor(0.107);
separator.setOrientation("horizontal");
// Now capacity is fully user-controlled
```

### Option 4: Partial Override with Design Standards

```java
// Use company standards but override specific values
separator.autoSize("Equinor", "TR2000");  // Load Equinor K-factors
separator.setInternalDiameter(2.8);        // But use custom diameter
```

### Equipment-Specific Override Examples

**Separator:**
```java
separator.autoSize(1.2);
// Override the K-factor used for utilization calculations
separator.setDesignGasLoadFactor(0.12);  // Changes max allowable gas flow
// Or override dimensions directly
separator.setInternalDiameter(2.5);
separator.setSeparatorLength(7.0);
```

**Compressor:**
```java
compressor.autoSize(1.2);
// Override power limits
compressor.setMaximumPower(5000.0);  // kW - overrides driver power
compressor.setMaximumSpeed(12000.0); // RPM - sets speed limit
// Or disable auto-generated curves and use manual efficiency
compressor.setUsePolytropicCalc(true);
compressor.setPolytropicEfficiency(0.78);
```

**Valve:**
```java
valve.autoSize(1.2);
// Override Cv for different valve selection
valve.setCv(200.0);  // Set Cv directly
// Or set design opening target
valve.setDesignVolumeFlow(500.0);  // m³/hr at design conditions
```

**Pipe:**
```java
pipe.autoSize(1.2);
// Override velocity limit for different service
pipe.setMaxDesignVelocity(25.0);  // m/s for clean dry gas
// Or set diameter directly
pipe.setDiameter(0.4);  // 400mm ID
```

---

## autoSize vs MechanicalDesign

NeqSim has two related but distinct design systems that work together:

### Quick Comparison

| Aspect | `autoSize()` | `MechanicalDesign` |
|--------|--------------|-------------------|
| **Purpose** | Quick sizing for capacity/utilization | Detailed mechanical engineering calculations |
| **Scope** | Sets basic dimensions (diameter, length) | Wall thickness, materials, weights, costs |
| **Usage** | Process simulation, capacity studies | Detailed design, procurement, fabrication |
| **Output** | Equipment dimensions | Complete design report with JSON export |
| **Speed** | Fast | More comprehensive |

### How They Work Together

Starting with NeqSim 3.x, `autoSize()` **delegates to MechanicalDesign** internally, ensuring consistent calculations and access to design standards:

```
autoSize(safetyFactor)
    │
    ├── 1. Initialize MechanicalDesign (if needed)
    │
    ├── 2. Read design specifications from database
    │       └── Loads K-factor, Fg, retention time from design standards
    │
    ├── 3. Apply user's safety factor
    │
    ├── 4. Check for user overrides (e.g., custom K-factor)
    │
    ├── 5. Perform sizing calculations via MechanicalDesign
    │       └── Calculates diameter, length, wall thickness, weights, costs
    │
    └── 6. Apply dimensions back to equipment
```

### When autoSize() Uses MechanicalDesign

For **Separators**, `autoSize()` now:
1. Loads design standards (K-factor, liquid level fraction, retention time)
2. Applies safety factor to flow rates
3. Calculates diameter using Souders-Brown equation
4. Calculates length using liquid retention time
5. Calculates wall thickness, weights, and module dimensions
6. Sets all dimensions on the separator

### Key Integration Points

**Parameter Synchronization:**
```java
// autoSize() synchronizes parameters bidirectionally:
// 1. User's K-factor → MechanicalDesign (if user set it)
// 2. Design standard K-factor → Separator (if user didn't set it)
// 3. Calculated dimensions → Separator (diameter, length)
// 4. Design parameters → Separator (K-factor, liquid level)
```

**Design Standard Priority:**
1. User-specified values (highest priority)
2. Company TR document values
3. Industry standard defaults (lowest priority)

### Example: autoSize with MechanicalDesign Access

```java
// Create and run separator
ThreePhaseSeparator separator = new ThreePhaseSeparator("HP-Sep", feed);
process.add(separator);
process.run();

// Auto-size using design standards
separator.autoSize(1.2);  // 20% safety margin

// Access detailed mechanical design data
SeparatorMechanicalDesign mechDesign = separator.getMechanicalDesign();
System.out.println("Wall thickness: " + mechDesign.getWallThickness() + " m");
System.out.println("Empty vessel weight: " + mechDesign.getWeigthVesselShell() + " kg");
System.out.println("Total module weight: " + mechDesign.getWeightTotal() + " kg");

// Get complete JSON report
String report = mechDesign.toJson();
```

### Example: Full MechanicalDesign Workflow

For detailed engineering, use MechanicalDesign directly:

```java
// Create separator
ThreePhaseSeparator separator = new ThreePhaseSeparator("HP-Sep", feed);
separator.run();

// Initialize and configure mechanical design
separator.initMechanicalDesign();
SeparatorMechanicalDesign design = separator.getMechanicalDesign();

// Set company-specific design standards
design.setCompanySpecificDesignStandards("Equinor");
design.readDesignSpecifications();

// Override specific parameters if needed
design.setGasLoadFactor(0.107);      // Custom K-factor
design.setVolumeSafetyFactor(1.25);  // 25% margin
design.setFg(0.5);                   // 50% gas area (50% liquid level)

// Perform full design calculations
design.calcDesign();

// Apply calculated dimensions to separator
design.setDesign();

// Get comprehensive report
String json = design.toJson();
design.displayResults();  // Show GUI dialog
```

### Design Parameters Explained

| Parameter | MechanicalDesign Field | Default | Description |
|-----------|----------------------|---------|-------------|
| K-factor | `gasLoadFactor` | 0.107 | Souders-Brown coefficient [m/s] |
| Gas area fraction | `Fg` | 0.5 | Fraction of vessel for gas (1 - liquid level) |
| Safety factor | `volumeSafetyFactor` | 1.0 | Multiplier for design flow rates |
| Retention time | `retentionTime` | 120s | Liquid residence time [seconds] |
| Wall thickness | `wallThickness` | calculated | From pressure vessel code |

### Which Approach to Use?

**Use `autoSize()` when:**
- You need quick sizing for process simulation
- You want reasonable utilization percentages
- You're doing capacity or debottlenecking studies
- You don't need detailed weight/cost data

**Use `MechanicalDesign` directly when:**
- You need wall thickness calculations
- You need weight and cost estimates
- You're doing detailed engineering
- You need to export design data (JSON)
- You need to apply specific design codes (ASME, API, etc.)

---

## Separator Design Parameters

### Required Parameters

| Parameter | Method | Unit | Description |
|-----------|--------|------|-------------|
| Internal Diameter | `setInternalDiameter(double)` | meters | Vessel internal diameter |
| Length | `setSeparatorLength(double)` | meters | Vessel length (tangent-to-tangent) |
| Orientation | `setOrientation(String)` | - | "horizontal" or "vertical" |

### Optional Design Parameters

| Parameter | Method | Unit | Typical Values |
|-----------|--------|------|----------------|
| Design Gas Load Factor | `setDesignGasLoadFactor(double)` | m/s | 0.07-0.15 (horizontal) |
| Design Liquid Level | `setDesignLiquidLevelFraction(double)` | fraction | 0.3-0.6 |
| Liquid Residence Time | `setLiquidRetentionTime(double, String)` | time | 2-5 minutes |

### Example: Manual Separator Design

```java
// Create separator
ThreePhaseSeparator separator = new ThreePhaseSeparator("HP Separator", feedStream);

// Set physical dimensions
separator.setInternalDiameter(2.5);      // 2.5 meters diameter
separator.setSeparatorLength(8.0);        // 8 meters length
separator.setOrientation("horizontal");

// Set design limits for capacity tracking
separator.setDesignGasLoadFactor(0.107);  // K-factor for mesh pad demister
separator.setDesignLiquidLevelFraction(0.5); // 50% liquid level

// Run the separator
separator.run();

// Check utilization
double utilization = separator.getCapacityUtilization();
System.out.println("Separator utilization: " + (utilization * 100) + "%");
```

### Key Design Equations

**Souders-Brown Equation** (Gas Load Factor):
```
V_max = K × √((ρ_liq - ρ_gas) / ρ_gas)
```

Where:
- K = Design gas load factor (0.07-0.15 m/s typical)
- ρ_liq = Liquid density (kg/m³)
- ρ_gas = Gas density (kg/m³)

**Typical K-Factors**:
| Internals Type | K-Factor (m/s) |
|----------------|----------------|
| No internals | 0.06-0.08 |
| Wire mesh demister | 0.10-0.12 |
| Vane pack | 0.12-0.15 |
| Cyclone | 0.15-0.20 |

---

## Pipe/Pipeline Design Parameters

### Required Parameters

| Parameter | Method | Unit | Description |
|-----------|--------|------|-------------|
| Diameter | `setDiameter(double)` | meters | Internal pipe diameter |
| Length | `setLength(double)` | meters | Pipe segment length |
| Roughness | `setPipeWallRoughness(double)` | meters | Wall roughness (5e-6 typical) |

### Optional Design Limits

| Parameter | Method | Unit | Typical Values |
|-----------|--------|------|----------------|
| Max Design Velocity | `setMaxDesignVelocity(double)` | m/s | 15-25 (gas), 3-5 (liquid) |
| Max Design LOF | `setMaxDesignLOF(double)` | - | 0.5-1.0 (liquid holdup) |
| Max Design FRMS | `setMaxDesignFRMS(double)` | Pa/m | 200-500 |

### Example: Manual Pipe Design

```java
// Create pipe
AdiabaticPipe pipe = new AdiabaticPipe("Export Pipeline", feedStream);

// Set physical dimensions
pipe.setDiameter(0.508);        // 20 inch (converted to meters)
pipe.setLength(50000.0);         // 50 km
pipe.setPipeWallRoughness(5e-5); // Typical for aged carbon steel

// Set design velocity limit for capacity tracking
pipe.setMaxDesignVelocity(20.0); // Max 20 m/s for gas

// Run the pipe
pipe.run();

// Check velocity and utilization
double velocity = pipe.getSuperficialVelocity();
System.out.println("Actual velocity: " + velocity + " m/s");
```

### Standard Pipe Sizes (API 5L)

| Nominal Size | OD (inches) | ID (approx, Sch 40) |
|--------------|-------------|---------------------|
| 6" | 6.625 | 6.065 |
| 8" | 8.625 | 7.981 |
| 10" | 10.75 | 10.02 |
| 12" | 12.75 | 11.938 |
| 16" | 16.0 | 15.0 |
| 20" | 20.0 | 18.812 |
| 24" | 24.0 | 22.624 |

### Velocity Guidelines

| Service | Velocity Range (m/s) |
|---------|---------------------|
| Gas (no liquid) | 15-25 |
| Gas (with liquid) | 10-15 |
| Two-phase | 5-15 |
| Oil | 1-3 |
| Water | 1-4 |

---

## Compressor Design Parameters

### Required Parameters

| Parameter | Method | Unit | Description |
|-----------|--------|------|-------------|
| Outlet Pressure | `setOutletPressure(double)` | bara | Target discharge pressure |
| Efficiency | `setPolytropicEfficiency(double)` | fraction | 0.70-0.85 typical |

### Optional Design Parameters

| Parameter | Method | Unit | Description |
|-----------|--------|------|-------------|
| Isentropic Efficiency | `setIsentropicEfficiency(double)` | fraction | Alternative to polytropic |
| Max Outlet Pressure | `setMaxOutletPressure(double)` | bara | Safety limit |
| Speed | `setSpeed(double)` | RPM | Actual operating speed |

### Using Compressor Curves

```java
// Create compressor with curve
Compressor compressor = new Compressor("1st Stage", feedStream);
compressor.setOutletPressure(50.0);  // 50 bara discharge

// Load performance curve from file
compressor.getCompressorChart().setCurves(
    "path/to/compressor_curve.json"
);

// Or set efficiency directly
compressor.setPolytropicEfficiency(0.78);

// Run
compressor.run();

// Check power and head
double power = compressor.getPower("MW");
double head = compressor.getPolytropicHead("kJ/kg");
```

### Typical Efficiencies

| Compressor Type | Polytropic Efficiency |
|-----------------|----------------------|
| Centrifugal (single stage) | 0.75-0.82 |
| Centrifugal (multi-stage) | 0.70-0.78 |
| Reciprocating | 0.80-0.90 |
| Screw | 0.65-0.75 |

---

## Heat Exchanger Design Parameters

### Required Parameters

| Parameter | Method | Unit | Description |
|-----------|--------|------|-------------|
| UA Value | `setUAvalue(double)` | W/K | Overall heat transfer coefficient × area |

Or specify outlet conditions:

| Parameter | Method | Unit | Description |
|-----------|--------|------|-------------|
| Outlet Temperature | `setOutletTemperature(double, String)` | °C or K | Target outlet temperature |

### Example: Manual Heat Exchanger Design

```java
// Create heat exchanger
HeatExchanger hx = new HeatExchanger("Gas Cooler", hotStream);
hx.setGuessOutTemperature(40.0);  // Guess for iteration

// Option 1: Set UA value directly
hx.setUAvalue(50000.0);  // 50 kW/K

// Option 2: Set target outlet temperature
// hx.setOutletTemperature(40.0, "C");

// Run
hx.run();

// Get duty
double duty = hx.getDuty();
System.out.println("Heat duty: " + (duty / 1e6) + " MW");
```

---

## Valve Design Parameters

### Required Parameters

| Parameter | Method | Unit | Description |
|-----------|--------|------|-------------|
| Outlet Pressure | `setOutletPressure(double)` | bara | Downstream pressure |

Or for control valves:

| Parameter | Method | Unit | Description |
|-----------|--------|------|-------------|
| Cv | `setCv(double)` | - | Valve flow coefficient |
| Percent Opening | `setPercentValveOpening(double)` | % | 0-100% |

### Design Parameters for Capacity Tracking

| Parameter | Method | Description |
|-----------|--------|-------------|
| Design Cv | `setDesignCv(double)` | Design flow coefficient for utilization |
| Design Volume Flow | `setDesignVolumeFlow(double)` | Max design volume flow (m³/hr) |
| Design Opening | `setDesignOpening(double)` | Target opening at design flow (default 50%) |

### Example: Manual Valve Design

```java
// Create throttling valve
ThrottlingValve valve = new ThrottlingValve("HP Choke", feedStream);

// Option 1: Set outlet pressure
valve.setOutletPressure(30.0);  // 30 bara

// Option 2: Set Cv and opening for control valve
// valve.setCv(150.0);
// valve.setPercentValveOpening(50.0);

// Set valve characteristics
valve.setIsCalcOutPressure(false);  // Use specified outlet pressure

// Run
valve.run();

// Override after autoSize to set custom capacity limits
valve.autoSize(1.2);
valve.setDesignCv(200.0);  // Override with actual valve Cv
```

### Capacity Utilization for Valves

Valve utilization is calculated as:
```
Utilization = Actual Volume Flow / Max Design Volume Flow
```

Where max design flow is derived from Cv at current conditions. A valve at 50% opening with full Cv utilization is at 50% capacity (typical design point).

---

## Pump Design Parameters

### Required Parameters

| Parameter | Method | Unit | Description |
|-----------|--------|------|-------------|
| Outlet Pressure | `setOutletPressure(double, String)` | bara | Discharge pressure |

### Optional Design Parameters

| Parameter | Method | Unit | Description |
|-----------|--------|------|-------------|
| Efficiency | `setPumpEfficiency(double)` | fraction | 0.6-0.85 typical |
| Speed | `setSpeed(double)` | RPM | Operating speed |

### Design Parameters for Capacity Tracking

| Parameter | Method | Unit | Description |
|-----------|--------|------|-------------|
| Design Volume Flow | `getMechanicalDesign().setMaxDesignVolumeFlow(double)` | m³/hr | Max design flow |
| Design Power | `getMechanicalDesign().setMaxDesignPower(double)` | W | Max design power |

### Example: Manual Pump Design

```java
// Create pump
Pump pump = new Pump("Export Pump", liquidStream);

// Set operating point
pump.setOutletPressure(50.0, "bara");
pump.setPumpEfficiency(0.75);

// Run
pump.run();

// Check power
double power = pump.getPower("kW");
System.out.println("Pump power: " + power + " kW");

// Set capacity limits for utilization tracking
pump.autoSize(1.2);
// Or manually override:
pump.getMechanicalDesign().setMaxDesignPower(power * 1.3);  // 30% margin
```

### Capacity Utilization for Pumps

Pump utilization is calculated as:
```
Utilization = Actual Shaft Power / Max Design Power
```

### Typical Pump Efficiencies

| Pump Type | Efficiency Range |
|-----------|-----------------|
| Centrifugal (single stage) | 0.60-0.75 |
| Centrifugal (multi-stage) | 0.65-0.80 |
| Positive Displacement | 0.80-0.90 |

---

## Using autoSizeEquipment() vs Manual Sizing

### Auto-Sizing Workflow

```java
// 1. Create process with equipment (no dimensions set)
ProcessSystem process = new ProcessSystem();

Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(1000.0, "kg/hr");
process.add(feed);

ThreePhaseSeparator sep = new ThreePhaseSeparator("Separator", feed);
process.add(sep);  // No dimensions set yet

AdiabaticPipe pipe = new AdiabaticPipe("Outlet", sep.getGasOutStream());
pipe.setLength(100.0);  // Length still needed
process.add(pipe);     // Diameter not set

// 2. Run to establish flow rates
process.run();

// 3. Auto-size all equipment (uses 1.2 safety factor by default)
int count = process.autoSizeEquipment();
System.out.println("Auto-sized " + count + " equipment items");

// 4. Re-run with sized equipment
process.run();

// 5. Check utilization (should be ~83% with 1.2 safety factor)
Map<String, Double> utilization = process.getCapacityUtilizationSummary();
utilization.forEach((name, util) -> 
    System.out.println(name + ": " + util + "% utilized")
);
```

### Custom Safety Factor

```java
// Size with 30% margin (1.3 safety factor)
process.autoSizeEquipment(1.3);

// Size with 10% margin (1.1 safety factor) - tighter design
process.autoSizeEquipment(1.1);
```

### Company-Specific Standards

```java
// Use Equinor TR standards
process.autoSizeEquipment("Equinor", "TR2000");

// Use Shell DEP standards
process.autoSizeEquipment("Shell", "DEP-31.38.01.11");
```

---

## Design Validation Methods

Each mechanical design class provides validation methods to verify that the design meets industry standards and process requirements. These methods return either boolean values for individual checks or comprehensive validation results with issue lists.

### Separator Design Validation

```java
SeparatorMechanicalDesign sepDesign = (SeparatorMechanicalDesign) separator.getMechanicalDesign();

// Individual parameter validation
boolean gasOk = sepDesign.validateGasVelocity(actualVelocity);     // m/s
boolean liqOk = sepDesign.validateLiquidVelocity(actualVelocity);  // m/s
boolean retOk = sepDesign.validateRetentionTime(minutes, isOil);   // true=oil, false=water
boolean dropOk = sepDesign.validateDropletDiameter(diameterUm, isGasLiq);

// Comprehensive validation
SeparatorMechanicalDesign.SeparatorValidationResult result = sepDesign.validateDesignComprehensive();
System.out.println("Valid: " + result.isValid());
for (String issue : result.getIssues()) {
    System.out.println("  Issue: " + issue);
}
```

**Separator Validation Checks:**
- Gas velocity vs maximum allowed
- L/D ratio within 2.0-6.0 range
- Retention time vs minimum requirements
- Design pressure margin adequacy

### Compressor Design Validation

```java
CompressorMechanicalDesign compDesign = compressor.getMechanicalDesign();

// Individual validation
boolean effOk = compDesign.validateEfficiency(efficiencyPercent);  // e.g., 78.0 for 78%
boolean tempOk = compDesign.validateDischargeTemperature(tempC);
boolean prOk = compDesign.validatePressureRatioPerStage(ratio);
boolean vibOk = compDesign.validateVibration(mmPerSec);

// Comprehensive validation
CompressorMechanicalDesign.CompressorValidationResult result = compDesign.validateDesign();
```

**Compressor Validation Checks:**
- Discharge temperature vs material/process limits
- Polytropic efficiency vs minimum target
- Pressure ratio per stage vs maximum
- Surge margin adequacy (minimum 10%)

### Pump Design Validation (API-610)

```java
PumpMechanicalDesign pumpDesign = pump.getMechanicalDesign();

// NPSH margin validation
boolean npshOk = pumpDesign.validateNpshMargin(npshAvailable, npshRequired);

// Operating region validation
boolean porOk = pumpDesign.validateOperatingInPOR(operatingFlow, bepFlow);  // Preferred region
boolean aorOk = pumpDesign.validateOperatingInAOR(operatingFlow, bepFlow);  // Allowable region

// Suction specific speed validation
boolean nssOk = pumpDesign.validateSuctionSpecificSpeed(actualNss);

// Comprehensive validation
PumpMechanicalDesign.PumpValidationResult result = pumpDesign.validateDesign();
```

**Pump Validation Checks:**
- NPSH margin (NPSHa / NPSHr) vs minimum factor
- Operating point within Preferred Operating Region (80-110% of BEP)
- Operating point within Allowable Operating Region (60-130% of BEP)
- Suction specific speed vs maximum (typically 11,000)
- Driver power margin adequacy

### Heat Exchanger Design Validation (TEMA)

```java
HeatExchangerMechanicalDesign hxDesign = heatExchanger.getMechanicalDesign();

// Velocity validation
boolean tubeOk = hxDesign.validateTubeVelocity(velocity);    // Must be between min and max
boolean shellOk = hxDesign.validateShellVelocity(velocity);

// Temperature validation
boolean approachOk = hxDesign.validateApproachTemperature(approachC);

// Geometry validation
boolean lengthOk = hxDesign.validateTubeLength(lengthM);

// Comprehensive validation
HeatExchangerMechanicalDesign.HeatExchangerValidationResult result = hxDesign.validateDesign();
```

**Heat Exchanger Validation Checks:**
- Tube velocity between minimum (fouling) and maximum (erosion)
- Shell velocity vs erosion limit
- Approach temperature vs minimum (pinch)
- Tube length vs mechanical limits
- Design pressure margin adequacy

### Example: Complete Design Validation Workflow

```java
// Build and run process
ProcessSystem process = new ProcessSystem();
// ... add equipment ...
process.run();

// Validate all equipment
boolean allValid = true;
StringBuilder report = new StringBuilder();

for (ProcessEquipmentInterface equip : process.getEquipmentList()) {
    MechanicalDesign design = equip.getMechanicalDesign();
    design.calcDesign();
    
    if (design instanceof SeparatorMechanicalDesign) {
        SeparatorMechanicalDesign.SeparatorValidationResult result = 
            ((SeparatorMechanicalDesign) design).validateDesignComprehensive();
        if (!result.isValid()) {
            allValid = false;
            report.append(equip.getName() + " issues:\n");
            result.getIssues().forEach(i -> report.append("  - " + i + "\n"));
        }
    }
    // Similar for other equipment types...
}

System.out.println("All designs valid: " + allValid);
if (!allValid) {
    System.out.println(report.toString());
}
```

---

## Capacity Constraints and Utilization

### Understanding Utilization

Utilization is calculated as:
```
Utilization = Actual Value / Design Value
```

For example:
- Separator at 80% utilization: Gas velocity is 80% of maximum allowable
- Pipe at 50% utilization: Actual velocity is 50% of design velocity limit

### Setting Custom Design Limits

```java
// Separator: Set custom gas load factor limit
separator.setDesignGasLoadFactor(0.1);  // K = 0.1 m/s

// Pipe: Set custom velocity limit
pipe.setMaxDesignVelocity(15.0);  // Max 15 m/s

// After running, check constraints
Map<String, CapacityConstraint> constraints = separator.getCapacityConstraints();
for (CapacityConstraint c : constraints.values()) {
    System.out.println(c.getName() + ": " + 
        (c.getUtilization() * 100) + "% of design");
}
```

### Bottleneck Detection

```java
// Find the bottleneck in the process
BottleneckResult bottleneck = process.findBottleneck();

if (bottleneck.hasBottleneck()) {
    System.out.println("Bottleneck: " + bottleneck.getEquipmentName());
    System.out.println("Constraint: " + bottleneck.getConstraintName());
    System.out.println("Utilization: " + bottleneck.getUtilizationPercent() + "%");
}

// Check for overloaded equipment
if (process.isAnyEquipmentOverloaded()) {
    System.out.println("WARNING: Equipment exceeds design capacity!");
}

// Get equipment near capacity (>90% by default)
List<String> nearLimit = process.getEquipmentNearCapacityLimit();
```

---

## Summary: Required Parameters by Equipment Type

| Equipment | Minimum Required | For Capacity Tracking |
|-----------|------------------|----------------------|
| **Separator** | Diameter, Length, Orientation | Design K-factor |
| **Pipe** | Diameter, Length, Roughness | Max design velocity |
| **Compressor** | Outlet pressure, Efficiency | Speed limits, surge line |
| **Heat Exchanger** | UA value OR outlet temp | Design duty |
| **Valve** | Outlet pressure OR Cv | Cv, max opening |

---

## See Also

- [AutoSizeable Interface](../src/main/java/neqsim/process/design/AutoSizeable.java)
- [Capacity Constraint Framework](CAPACITY_CONSTRAINT_FRAMEWORK.md)
- [Mechanical Design Framework](mechanical_design.md)
- [Optimization & Constraints Guide](optimization/OPTIMIZATION_AND_CONSTRAINTS.md)
- [Cost Estimation Framework](COST_ESTIMATION_FRAMEWORK.md)
