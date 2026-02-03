---
title: Flow Assurance Overview
description: Integrated guide to flow assurance in NeqSim covering hydrate prediction, wax precipitation, asphaltene stability, scale formation, and combined screening workflows.
---

# Flow Assurance Overview

Flow assurance ensures reliable hydrocarbon transport from reservoir to processing facility. This guide provides an integrated approach to the main flow assurance challenges using NeqSim.

## Key Flow Assurance Challenges

| Challenge | Cause | Risk | NeqSim Capability |
|-----------|-------|------|-------------------|
| **Hydrates** | Water + gas at low T, high P | Blockage | ✅ Full prediction |
| **Wax** | Paraffin precipitation at low T | Deposition, restart | ✅ WAT calculation |
| **Asphaltenes** | Pressure/composition change | Deposition, fouling | ✅ CPA model |
| **Scale** | Mineral precipitation | Blockage, corrosion | ⚠️ Basic |
| **Slugging** | Multiphase flow instability | Equipment damage | ✅ Transient models |
| **Corrosion** | CO₂, H₂S, water | Pipe failure | ⚠️ Indirect |

---

## Quick Screening Workflow

### Java - Complete Flow Assurance Screen

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class FlowAssuranceScreen {
    public static void main(String[] args) {
        
        // Create production fluid
        SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(293.15, 100.0);
        
        // Typical Gulf of Mexico fluid
        fluid.addComponent("nitrogen", 0.005);
        fluid.addComponent("CO2", 0.02);
        fluid.addComponent("H2S", 0.001);
        fluid.addComponent("methane", 0.40);
        fluid.addComponent("ethane", 0.08);
        fluid.addComponent("propane", 0.06);
        fluid.addComponent("i-butane", 0.02);
        fluid.addComponent("n-butane", 0.03);
        fluid.addComponent("i-pentane", 0.015);
        fluid.addComponent("n-pentane", 0.02);
        fluid.addComponent("n-hexane", 0.025);
        fluid.addTBPfraction("C7", 0.05, 95.0, 0.72);
        fluid.addTBPfraction("C10", 0.08, 135.0, 0.78);
        fluid.addTBPfraction("C20", 0.08, 280.0, 0.85);
        fluid.addTBPfraction("C30+", 0.06, 450.0, 0.91);
        fluid.addComponent("water", 0.05);
        
        fluid.setMixingRule(10);  // CPA mixing rule
        fluid.setMultiPhaseCheck(true);
        
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        
        System.out.println("========== FLOW ASSURANCE SCREENING ==========");
        System.out.println();
        
        // === 1. HYDRATE SCREENING ===
        System.out.println("--- HYDRATE RISK ---");
        try {
            fluid.setTemperature(280.0);  // 7°C - subsea temperature
            fluid.setPressure(150.0);     // Pipeline pressure
            ops.hydrateFormationTemperature();
            double hydrateT = fluid.getTemperature("C");
            System.out.printf("Hydrate formation temperature at 150 bara: %.1f °C%n", hydrateT);
            
            if (hydrateT > 4.0) {
                System.out.println("⚠️  HIGH HYDRATE RISK - Inhibition required");
            } else {
                System.out.println("✅ Low hydrate risk at seabed temperature");
            }
        } catch (Exception e) {
            System.out.println("Could not calculate hydrate temperature");
        }
        System.out.println();
        
        // === 2. WAX SCREENING ===
        System.out.println("--- WAX RISK ---");
        try {
            ops.calcWAT();
            double waxT = fluid.getWAT("C");
            System.out.printf("Wax Appearance Temperature (WAT): %.1f °C%n", waxT);
            
            if (waxT > 20.0) {
                System.out.println("⚠️  HIGH WAX RISK - Pigging/inhibition needed");
            } else if (waxT > 4.0) {
                System.out.println("⚠️  MODERATE WAX RISK - Monitor");
            } else {
                System.out.println("✅ Low wax risk");
            }
        } catch (Exception e) {
            System.out.println("WAT calculation not available - check C20+ content");
        }
        System.out.println();
        
        // === 3. ASPHALTENE SCREENING (De Boer) ===
        System.out.println("--- ASPHALTENE RISK ---");
        double reservoirP = 350.0;  // bara
        double bubblePointP = 180.0;  // bara (estimate)
        double asphalteneContent = 2.5;  // wt%
        double apiGravity = 32.0;
        
        // De Boer screening criterion
        double deltaP = reservoirP - bubblePointP;
        double deBoerRisk = deltaP * asphalteneContent / 100.0;
        
        System.out.printf("Reservoir pressure: %.0f bara%n", reservoirP);
        System.out.printf("Bubble point: %.0f bara%n", bubblePointP);
        System.out.printf("ΔP (supersaturation): %.0f bar%n", deltaP);
        System.out.printf("De Boer risk parameter: %.2f%n", deBoerRisk);
        
        if (apiGravity > 40 && deltaP > 200) {
            System.out.println("⚠️  HIGH ASPHALTENE RISK - Light oil with high supersaturation");
        } else if (deBoerRisk > 1.5) {
            System.out.println("⚠️  MODERATE ASPHALTENE RISK");
        } else {
            System.out.println("✅ Low asphaltene risk");
        }
        System.out.println();
        
        // === 4. SCALE SCREENING ===
        System.out.println("--- SCALE RISK (Qualitative) ---");
        double co2Content = fluid.getComponent("CO2").getx() * 100;
        double h2sContent = fluid.getComponent("H2S").getx() * 100;
        double waterCut = fluid.getComponent("water").getx() * 100;
        
        System.out.printf("CO2 content: %.2f mol%%%n", co2Content);
        System.out.printf("H2S content: %.3f mol%%%n", h2sContent);
        System.out.printf("Water content: %.1f mol%%%n", waterCut);
        
        if (co2Content > 2.0 && waterCut > 5.0) {
            System.out.println("⚠️  CARBONATE SCALE RISK - CO2 + water present");
        }
        if (h2sContent > 0.01) {
            System.out.println("⚠️  SULFIDE SCALE RISK - H2S present");
        }
        System.out.println();
        
        // === 5. CORROSION SCREENING ===
        System.out.println("--- CORROSION RISK ---");
        if (co2Content > 0.5 && waterCut > 1.0) {
            System.out.println("⚠️  CO2 CORROSION RISK - Sweet corrosion");
        }
        if (h2sContent > 0.001) {
            System.out.println("⚠️  H2S CORROSION RISK - Sour service materials required");
        }
        System.out.println();
        
        // === 6. pH STABILIZATION CHECK ===
        System.out.println("--- pH STABILIZATION ---");
        if (co2Content > 0.5 && waterCut > 1.0) {
            System.out.println("💡 Consider pH stabilization (NaOH) to form protective FeCO3 layer");
            System.out.println("   Target pH: 6.0-6.5 for optimal siderite protection");
            System.out.println("   Use Electrolyte CPA EoS for detailed calculations");
        }
        System.out.println();
        
        // === SUMMARY ===
        System.out.println("========== SCREENING SUMMARY ==========");
        System.out.println("Use detailed models for high-risk areas");
        System.out.println("See individual flow assurance guides for mitigation");
    }
}
```

---

## Corrosion Control and pH Stabilization

CO2 corrosion (sweet corrosion) is a major flow assurance challenge. NeqSim's **Electrolyte CPA EoS** enables sophisticated corrosion control modeling.

### pH Stabilization Strategy

pH stabilization uses NaOH to:

1. **Raise aqueous pH** above 6.0-6.5
2. **Promote FeCO3 (siderite)** protective layer on steel
3. **Reduce corrosion rates** by 1-2 orders of magnitude

The siderite protective layer forms when:

$$\text{Fe}^{2+} + \text{CO}_3^{2-} \rightarrow \text{FeCO}_3 \downarrow$$

### Quick pH Calculation

```java
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create electrolyte system
SystemElectrolyteCPAstatoil fluid = new SystemElectrolyteCPAstatoil(353.15, 50.0);

// Add gas and aqueous components
fluid.addComponent("methane", 0.85);
fluid.addComponent("CO2", 0.03);
fluid.addComponent("water", 0.10);
fluid.addComponent("Na+", 0.01);    // With NaOH treatment
fluid.addComponent("OH-", 0.005);   // Hydroxide from NaOH
fluid.addComponent("Cl-", 0.005);
fluid.addComponent("Fe++", 0.00002);
fluid.addComponent("HCO3-", 0.002);

// Initialize and flash
fluid.chemicalReactionInit();
fluid.createDatabase(true);
fluid.setMixingRule(10);  // Electrolyte mixing rule

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.init(3);

// Get pH and check FeCO3 saturation
int aqPhase = fluid.getPhaseNumberOfPhase("aqueous");
double pH = fluid.getPhase(aqPhase).getpH();
System.out.printf("Aqueous pH: %.2f%n", pH);

// Check scale/protective layer potential
ops.checkScalePotential(aqPhase);
```

### FeCO3 Protection Criteria

| Factor | Optimal Range | Notes |
|--------|---------------|-------|
| **pH** | 6.0-6.5 | Higher promotes carbonate layer |
| **Temperature** | > 60°C | Faster scale kinetics |
| **Fe++ concentration** | 1-10 mg/L | Required for layer formation |
| **FeCO3 saturation ratio** | 1-10 | SR > 1 required for precipitation |
| **Flow velocity** | < 3 m/s | Avoid erosion of layer |

> **📚 See [pH Stabilization and Corrosion Control](ph_stabilization_corrosion)** for comprehensive documentation including NaOH dosing calculations, combined MEG/pH stabilization for subsea systems, and corrosion rate estimation.

---

## Hydrate Prediction Details

### Hydrate Equilibrium Curve

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Generate hydrate curve
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(273.15, 50.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("CO2", 0.03);
fluid.addComponent("water", 0.02);
fluid.setMixingRule(10);
fluid.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

System.out.println("Pressure (bara) | Hydrate T (°C)");
System.out.println("----------------|---------------");

double[] pressures = {10, 20, 50, 100, 150, 200, 300, 400};
for (double p : pressures) {
    fluid.setPressure(p);
    try {
        ops.hydrateFormationTemperature();
        double hydrateT = fluid.getTemperature("C");
        System.out.printf("%15.0f | %13.1f%n", p, hydrateT);
    } catch (Exception e) {
        System.out.printf("%15.0f | No hydrate%n", p);
    }
}
```

### With Inhibitor

```java
// Add MEG inhibitor
double megConcentration = 0.30;  // 30 wt% in water phase
fluid.addComponent("MEG", megConcentration * 0.02);  // Adjust water amount
fluid.setComponent("water", (1 - megConcentration) * 0.02);

// Recalculate - hydrate T will be depressed
ops.hydrateFormationTemperature();
double inhibitedT = fluid.getTemperature("C");
System.out.printf("Hydrate T with 30%% MEG: %.1f °C%n", inhibitedT);
```

---

## Wax Modeling

### Wax Appearance Temperature (WAT)

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Waxy crude - need heavy paraffins
SystemSrkEos fluid = new SystemSrkEos(323.15, 50.0);
fluid.addComponent("methane", 0.20);
fluid.addComponent("n-hexane", 0.10);
fluid.addComponent("n-heptane", 0.10);
fluid.addComponent("n-octane", 0.10);
fluid.addTBPfraction("C10", 0.15, 142.0, 0.78);
fluid.addTBPfraction("C15", 0.15, 212.0, 0.82);
fluid.addTBPfraction("C20", 0.10, 282.0, 0.85);
fluid.addTBPfraction("C25+", 0.10, 350.0, 0.87);
fluid.setMixingRule("classic");

// Enable wax calculations
fluid.setSolidPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.calcWAT();

double wat = fluid.getWAT("C");
System.out.printf("Wax Appearance Temperature: %.1f °C%n", wat);

// Wax content vs temperature
System.out.println("\nTemperature (°C) | Wax Content (wt%)");
for (double t = wat; t >= wat - 30; t -= 5) {
    fluid.setTemperature(t + 273.15);
    ops.TPSolidflash();
    double waxWt = fluid.getWaxContent() * 100;
    System.out.printf("%16.0f | %15.2f%n", t, waxWt);
}
```

---

## Asphaltene Stability

### CPA-Based Asphaltene Model

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Configure for asphaltene calculations
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(373.15, 300.0);

// Light ends
fluid.addComponent("methane", 0.35);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-butane", 0.03);

// Oil fractions
fluid.addTBPfraction("C7", 0.10, 95.0, 0.72);
fluid.addTBPfraction("C15", 0.15, 210.0, 0.82);
fluid.addTBPfraction("C30", 0.15, 420.0, 0.89);

// Asphaltene pseudo-component (high MW, high density)
fluid.addTBPfraction("Asphaltene", 0.09, 1000.0, 1.10);

fluid.setMixingRule(10);
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

// Check stability at different pressures (depletion path)
System.out.println("Pressure (bara) | Asphaltene Stable?");
System.out.println("----------------|-------------------");

double[] pressures = {300, 250, 200, 180, 160, 140, 120, 100};
for (double p : pressures) {
    fluid.setPressure(p);
    ops.TPflash();
    
    // Check if asphaltene-rich phase forms
    int nPhases = fluid.getNumberOfPhases();
    String stability = (nPhases > 2) ? "⚠️ UNSTABLE" : "✅ Stable";
    System.out.printf("%15.0f | %s%n", p, stability);
}
```

### Asphaltene Onset Pressure

```java
// Find pressure where asphaltenes first precipitate
try {
    ops.asphalteneOnsetPressure();
    double aop = fluid.getPressure("bara");
    System.out.printf("Asphaltene Onset Pressure (AOP): %.1f bara%n", aop);
} catch (Exception e) {
    System.out.println("Could not determine AOP");
}
```

---

## Combined Operating Envelope

Plot the operating envelope showing all constraints:

```java
import java.util.ArrayList;
import java.util.List;

// Calculate all constraint curves
List<double[]> hydratesCurve = new ArrayList<>();
List<double[]> waxCurve = new ArrayList<>();
double[] aopLine;

// 1. Hydrate equilibrium curve
for (double p = 10; p <= 300; p += 10) {
    fluid.setPressure(p);
    try {
        ops.hydrateFormationTemperature();
        hydratesCurve.add(new double[]{fluid.getTemperature("C"), p});
    } catch (Exception e) {}
}

// 2. WAT line (vertical - temperature independent of pressure to first order)
double wat = 35.0;  // From WAT calculation

// 3. AOP line (horizontal - pressure independent of T to first order)
double aop = 150.0;  // From AOP calculation

// Print operating envelope
System.out.println("========== OPERATING ENVELOPE ==========");
System.out.println();
System.out.println("Safe operating region:");
System.out.printf("  - Temperature > %.1f °C (hydrate limit at operating P)%n", 15.0);
System.out.printf("  - Temperature > %.1f °C (wax limit)%n", wat);
System.out.printf("  - Pressure > %.0f bara (asphaltene onset)%n", aop);
System.out.println();
System.out.println("Or use inhibition/insulation to expand envelope");
```

---

## Mitigation Strategies

### Hydrate Prevention

| Strategy | Description | NeqSim Support |
|----------|-------------|----------------|
| **MEG Injection** | Thermodynamic inhibitor | ✅ Full |
| **Methanol Injection** | Thermodynamic inhibitor | ✅ Full |
| **Insulation** | Keep T above hydrate curve | Pipeline models |
| **LDHI** | Low-dosage hydrate inhibitors | ❌ Not modeled |
| **Depressurization** | Emergency blowdown | ✅ Full |

### Wax Prevention

| Strategy | Description | NeqSim Support |
|----------|-------------|----------------|
| **Pigging** | Mechanical removal | ❌ Not modeled |
| **Hot oil circulation** | Thermal | Pipeline models |
| **Pour point depressants** | Chemical | ❌ Not modeled |
| **Insulation** | Keep T above WAT | Pipeline models |

### Asphaltene Prevention

| Strategy | Description | NeqSim Support |
|----------|-------------|----------------|
| **Pressure maintenance** | Stay above AOP | ✅ Full |
| **Chemical dispersants** | Prevent aggregation | ❌ Not modeled |
| **Blending** | Dilute with light oil | ✅ Mixing |

---

## Pipeline Temperature Profile

For realistic screening, calculate temperature along the pipeline:

```java
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

// Create pipeline
PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("Subsea Pipeline", wellStream);
pipeline.setLength(50000.0);     // 50 km
pipeline.setDiameter(0.3048);    // 12 inch
pipeline.setOuterTemperature(4.0, "C");  // Seabed temp
pipeline.setRoughness(1.5e-5);

// Get temperature at outlet
pipeline.run();
double outletT = pipeline.getOutletStream().getTemperature("C");

// Compare with hydrate temperature at outlet pressure
double outletP = pipeline.getOutletStream().getPressure("bara");
fluid.setPressure(outletP);
ops.hydrateFormationTemperature();
double hydrateT = fluid.getTemperature("C");

System.out.printf("Pipeline outlet: T=%.1f°C, P=%.1f bara%n", outletT, outletP);
System.out.printf("Hydrate temperature: %.1f°C%n", hydrateT);

if (outletT < hydrateT) {
    System.out.println("⚠️ HYDRATE RISK at pipeline outlet!");
}
```

---

## See Also

- [Mineral Scale Formation](mineral_scale_formation) - Comprehensive scale guide
- [pH Stabilization and Corrosion Control](ph_stabilization_corrosion) - Electrolyte CPA, FeCO3 protection
- [Scale Potential Calculations](../physical_properties/scale_potential) - Scale potential API details
- [Hydrate Models Guide](../thermo/hydrate_models) - Detailed hydrate prediction
- [Wax Characterization](../thermo/characterization/wax_characterization) - Wax calculations
- [Asphaltene Modeling](flowassurance/asphaltene_modeling) - Asphaltene stability
- [Pipeline Modeling](../process/equipment/pipelines) - Multiphase flow
- [Pipeline Recipes](../cookbook/pipeline-recipes) - Quick code recipes
