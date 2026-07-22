---
title: Integrated Field Development Framework
description: This document describes how NeqSim integrates PVT, reservoir, well, and process simulations
---

# Integrated Field Development Framework

## Overview

This document describes how NeqSim integrates PVT, reservoir, well, and process simulations
into a unified field development workflow. The framework supports progressive refinement
from early feasibility studies through detailed design, with increasing fidelity at each stage.

This framework is designed to support education and industry workflows aligned with academic
programs such as NTNU's **TPG4230 - Underground reservoirs fluid production and injection**
course, covering the complete lifecycle from discovery through operations.

---

## TPG4230 Course Topic Mapping

The following table maps key course topics to their NeqSim implementations:

| Course Topic | NeqSim Implementation | Key Classes |
|--------------|----------------------|-------------|
| **Field Lifecycle Management** | `FieldDevelopmentWorkflow` with `StudyPhase` enum (DISCOVERY→FEASIBILITY→CONCEPT_SELECT→FEED→OPERATIONS) | `FieldDevelopmentWorkflow`, `FidelityLevel`, `StudyPhase` |
| **PVT Characterization & EOS Tuning** | Equation of state selection, plus-fraction characterization, regression to lab data | `SystemSrkEos`, `SystemPrEos`, `Characterization`, `PVTRegression`, `SaturationPressure` |
| **Reservoir Material Balance** | Tank model with production/injection tracking, pressure depletion, voidage replacement | `SimpleReservoir`, `InjectionStrategy`, `InjectionStrategy.InjectionResult` |
| **Well Performance (IPR/VLP)** | Inflow performance relationships (Vogel, Fetkovich), vertical lift performance, nodal analysis | `WellFlow`, `WellSystem`, `TubingPerformance`, `WellSystem.IPRModel` |
| **Production Network Optimization** | Multi-well gathering systems, manifold pressure-rate equilibrium, rate allocation | `NetworkSolver`, `NetworkResult`, `SolutionMode` |
| **Economic Evaluation** | NPV, IRR, payback, country-specific tax models (Norway, UK, Brazil, etc.), Monte Carlo uncertainty | `CashFlowEngine`, `TaxModel`, `NorwegianTaxModel`, `SensitivityAnalyzer` |
| **Flow Assurance Screening** | Hydrate formation temperature, wax appearance, corrosion, scaling, erosion risk assessment | `FlowAssuranceScreener`, `FlowAssuranceReport`, `FlowAssuranceResult` |
| **Process Facility Design** | Process simulation with heat/mass balance, equipment sizing, power calculations | `ProcessSystem`, `Separator`, `Compressor`, `HeatExchanger` |
| **Mechanical Design** | Pressure vessel sizing, wall thickness (ASME VIII), weight estimation, module footprint | `SystemMechanicalDesign`, `SeparatorMechanicalDesign`, `CompressorMechanicalDesign` |
| **Power & Sustainability** | Power consumption, CO2 emissions, emission intensity (kg/boe), electrification scenarios | `EmissionsTracker`, `EmissionsReport`, `WorkflowResult.totalPowerMW` |
| **Subsea Production Systems** | Subsea wells, flowlines, manifolds, tieback analysis, subsea CAPEX estimation, flow assurance | `SubseaProductionSystem`, `SubseaWell`, `SimpleFlowLine`, `TiebackAnalyzer`, `TiebackReport` |

### Detailed Topic Coverage

#### 1. Field Lifecycle Management (Discovery → Operations)

NeqSim's `FieldDevelopmentWorkflow` class provides a unified orchestrator that supports all
phases of field development with appropriate fidelity levels:

```java
// Create workflow and set study phase
FieldDevelopmentWorkflow workflow = new FieldDevelopmentWorkflow("My Field");
workflow.setStudyPhase(StudyPhase.FEASIBILITY);
workflow.setFidelityLevel(FidelityLevel.SCREENING);  // ±50% accuracy

// Progress to concept selection
workflow.setStudyPhase(StudyPhase.CONCEPT_SELECT);
workflow.setFidelityLevel(FidelityLevel.CONCEPTUAL);  // ±30% accuracy
workflow.setFluid(tunedEosFluid);  // Add tuned EOS model

// Progress to FEED
workflow.setStudyPhase(StudyPhase.FEED);
workflow.setFidelityLevel(FidelityLevel.DETAILED);   // ±20% accuracy
workflow.setProcessSystem(fullProcessModel);
workflow.setMonteCarloIterations(1000);
```

| Study Phase | Typical Fidelity | NeqSim Features Used |
|-------------|------------------|---------------------|
| Discovery | SCREENING | PVT lab simulation, volumetrics, analogs |
| Feasibility (DG1) | SCREENING | Flow assurance screening, cost correlations, Arps decline |
| Concept (DG2) | CONCEPTUAL | EOS tuning, IPR/VLP, process simulation |
| FEED (DG3/4) | DETAILED | Full process, reservoir coupling, Monte Carlo |
| Operations | DETAILED | History matching, optimization, debottlenecking |

#### 2. PVT Characterization with EOS Tuning

NeqSim provides comprehensive PVT modeling capabilities:

```java
// Create fluid with plus-fraction
SystemInterface fluid = new SystemSrkEos(373.15, 250.0);
fluid.addComponent("methane", 0.60);
fluid.addComponent("ethane", 0.08);
fluid.addTBPfraction("C7+", 0.20, 220.0, 0.85);  // mole frac, MW, SG
fluid.setMixingRule("classic");

// Characterize plus-fraction using Pedersen method
fluid.getCharacterization().characterisePlusFraction();

// Run PVT experiments
SaturationPressure satP = new SaturationPressure(fluid);
satP.runCalc();  // Bubble/dew point

DifferentialLiberation dle = new DifferentialLiberation(fluid);
dle.runCalc();   // Bo, Rs, viscosity vs pressure
```

#### 3. Reservoir Material Balance with Injection

The `SimpleReservoir` and `InjectionStrategy` classes support pressure maintenance:

```java
// Create reservoir with injection wells
SimpleReservoir reservoir = new SimpleReservoir("Main Reservoir");
reservoir.setReservoirFluid(fluid, giip, thickness, area);
reservoir.addOilProducer("P1");
reservoir.addWaterInjector("I1");

// Calculate voidage replacement injection rates
InjectionStrategy strategy = InjectionStrategy.waterInjection(1.0);  // VRR = 1.0
InjectionResult injection = strategy.calculateInjection(
    reservoir, oilRate, gasRate, waterRate
);
System.out.println("Required water injection: " + injection.waterInjectionRate + " Sm3/d");
System.out.println("Achieved VRR: " + injection.achievedVRR);
```

#### 4. Well Performance (IPR/VLP)

Nodal analysis with inflow and outflow curves:

```java
// Configure well with IPR model
WellSystem well = new WellSystem("Producer-1", reservoirStream);
well.setIPRModel(WellSystem.IPRModel.VOGEL);
well.setVogelParameters(qTest, pwfTest, pRes);

// Configure VLP (tubing performance)
well.setTubingLength(2500.0, "m");
well.setTubingDiameter(4.0, "in");
well.setPressureDropCorrelation(TubingPerformance.PressureDropCorrelation.BEGGS_BRILL);
well.setWellheadPressure(50.0, "bara");

// Find operating point
well.run();
double rate = well.getOperatingFlowRate("Sm3/day");
double bhp = well.getOperatingBHP("bara");
```

#### 5. Production Network Optimization

Multi-well gathering network solver:

```java
// Create network with multiple wells
NetworkSolver network = new NetworkSolver("Gathering System");
network.addWell(well1, 3.0);   // 3 km flowline
network.addWell(well2, 5.5);   // 5.5 km flowline
network.addWell(well3, 8.0);   // 8 km flowline

// Solve for rates given manifold pressure
network.setSolutionMode(SolutionMode.FIXED_MANIFOLD_PRESSURE);
network.setManifoldPressure(60.0);
NetworkResult result = network.solve();

// Or find manifold pressure for target rate
network.setSolutionMode(SolutionMode.FIXED_TOTAL_RATE);
network.setTargetTotalRate(15.0e6);  // Sm3/day
result = network.solve();
System.out.println("Required manifold pressure: " + result.manifoldPressure);
```

#### 6. Economic Evaluation with Country-Specific Tax Models

Comprehensive economics with tax regime modeling:

```java
// Create cash flow engine with Norwegian tax model
CashFlowEngine engine = new CashFlowEngine("NO");
engine.setCapex(500.0, 2025);       // MUSD
engine.setOpexPercentOfCapex(0.04); // 4% of CAPEX/year
engine.setOilPrice(70.0);           // USD/bbl
engine.setGasPrice(0.30);           // USD/Sm3

// Add production profile
for (int year = 2027; year <= 2045; year++) {
    engine.addAnnualProduction(year, oilSm3[year], gasSm3[year], 0);
}

// Calculate with 8% discount rate
CashFlowResult result = engine.calculate(0.08);
System.out.println("NPV: " + result.getNpv() + " MUSD");
System.out.println("IRR: " + (result.getIrr() * 100) + "%");
System.out.println("Payback: " + result.getPaybackYears() + " years");

// Monte Carlo uncertainty analysis
SensitivityAnalyzer analyzer = new SensitivityAnalyzer(engine);
MonteCarloResult mcResult = analyzer.runMonteCarlo(1000);
System.out.println("P10 NPV: " + mcResult.getPercentile(10));
System.out.println("P50 NPV: " + mcResult.getPercentile(50));
System.out.println("P90 NPV: " + mcResult.getPercentile(90));
```

#### 7. Flow Assurance Screening (Hydrates, Wax, Corrosion)

Risk-based flow assurance assessment:

```java
// Create screener and run assessment
FlowAssuranceScreener screener = new FlowAssuranceScreener();
FlowAssuranceReport report = screener.screen(concept, minTempC, operatingPressure);

// Check individual risks
System.out.println("Hydrate: " + report.getHydrateResult());    // PASS/MARGINAL/FAIL
System.out.println("Wax: " + report.getWaxResult());
System.out.println("Corrosion: " + report.getCorrosionResult());
System.out.println("Overall: " + report.getOverallResult());

// Get mitigation recommendations
Map<String, String> mitigations = report.getMitigationOptions();
```

---

## Mathematical Foundations

This section provides the mathematical basis for the engineering and economic calculations
used in the field development framework.

### Economics Mathematics

#### Net Present Value (NPV)

The Net Present Value discounts future cash flows to present value:

$$NPV = \sum_{t=0}^{n} \frac{CF_t}{(1+r)^t}$$

where:
- $CF_t$ = Cash flow in year $t$ (Revenue - OPEX - Tax - CAPEX)
- $r$ = Discount rate (typically 8-12% for oil & gas)
- $n$ = Project lifetime in years

The cash flow for each year is calculated as:

$$CF_t = (R_t - OPEX_t) \times (1 - \tau) + D_t \times \tau - CAPEX_t$$

where:
- $R_t$ = Revenue = $q_{oil,t} \times P_{oil} + q_{gas,t} \times P_{gas}$
- $OPEX_t$ = Operating expenditure
- $\tau$ = Effective tax rate
- $D_t$ = Depreciation (tax shield)

#### Internal Rate of Return (IRR)

The IRR is the discount rate that makes NPV equal to zero:

$$NPV = \sum_{t=0}^{n} \frac{CF_t}{(1+IRR)^t} = 0$$

Solved iteratively using Newton-Raphson or bisection method.

#### Norwegian Petroleum Tax Model

Norway has a two-tier tax system:

$$Tax_{total} = Tax_{corporate} + Tax_{petroleum}$$

**Corporate Tax (22%):**
$$Tax_{corporate} = \max(0, (R - OPEX - D) \times 0.22)$$

**Petroleum Tax (71.8% marginal, 49.8% net after deductions):**
$$Tax_{petroleum} = \max(0, (R - OPEX - D - U) \times 0.498)$$

where:
- $D$ = Depreciation (6-year straight-line for offshore)
- $U$ = Uplift deduction (5.2% of CAPEX for 4 years)

**Uplift Calculation:**
$$U_t = CAPEX \times 0.052 \quad \text{for } t = 1,2,3,4$$

**Effective Government Take:**
$$\text{Gov Take} = \frac{Tax_{corporate} + Tax_{petroleum}}{R - OPEX} \approx 78\%$$

#### Production Decline Curves (Arps)

**Exponential Decline:**
$$q(t) = q_i \times e^{-D_i \times t}$$

**Hyperbolic Decline:**
$$q(t) = \frac{q_i}{(1 + b \times D_i \times t)^{1/b}}$$

**Harmonic Decline (b=1):**
$$q(t) = \frac{q_i}{1 + D_i \times t}$$

where:
- $q_i$ = Initial production rate
- $D_i$ = Initial decline rate (1/year)
- $b$ = Decline exponent (0 = exponential, 0-1 = hyperbolic, 1 = harmonic)

**Cumulative Production:**

For exponential decline:
$$N_p(t) = \frac{q_i}{D_i}(1 - e^{-D_i \times t})$$

For hyperbolic decline:
$$N_p(t) = \frac{q_i}{D_i(1-b)}\left[1 - (1 + b \times D_i \times t)^{(1-1/b)}\right]$$

#### Monte Carlo Uncertainty Analysis

For uncertainty quantification, input parameters are sampled from probability distributions:

- **Oil Price:** $P_{oil} \sim \text{LogNormal}(\mu, \sigma)$
- **CAPEX:** $CAPEX \sim \text{Triangular}(min, mode, max)$
- **Reserves:** $GIIP \sim \text{Normal}(\mu, \sigma)$

The NPV distribution is built from $N$ simulations (typically 1000-10000):

$$\{NPV_1, NPV_2, ..., NPV_N\}$$

Key statistics extracted:
- **P10** = 10th percentile (downside case)
- **P50** = Median (base case)
- **P90** = 90th percentile (upside case)
- **P(NPV > 0)** = Probability of positive returns

---

### Engineering Mathematics

#### Inflow Performance Relationship (IPR)

**Darcy's Law (Linear, undersaturated oil):**
$$q = J \times (P_r - P_{wf})$$

where:
- $q$ = Flow rate (Sm³/d)
- $J$ = Productivity index (Sm³/d/bar)
- $P_r$ = Reservoir pressure (bar)
- $P_{wf}$ = Bottom-hole flowing pressure (bar)

**Vogel's Equation (Solution gas drive, below bubble point):**
$$\frac{q}{q_{max}} = 1 - 0.2\left(\frac{P_{wf}}{P_r}\right) - 0.8\left(\frac{P_{wf}}{P_r}\right)^2$$

Rearranged:
$$q = q_{max} \times \left[1 - 0.2\left(\frac{P_{wf}}{P_r}\right) - 0.8\left(\frac{P_{wf}}{P_r}\right)^2\right]$$

**Fetkovich's Equation (Gas wells):**
$$q = C \times (P_r^2 - P_{wf}^2)^n$$

where:
- $C$ = Deliverability coefficient
- $n$ = Exponent (0.5-1.0, typically ~0.8)

#### Vertical Lift Performance (VLP)

**Single-Phase Pressure Drop:**
$$\frac{dP}{dL} = \frac{\rho g \sin\theta}{1000} + \frac{f \rho v^2}{2D}$$

where:
- $\rho$ = Fluid density (kg/m³)
- $g$ = Gravitational acceleration (9.81 m/s²)
- $\theta$ = Well inclination from horizontal
- $f$ = Friction factor (from Moody diagram or correlation)
- $v$ = Fluid velocity (m/s)
- $D$ = Tubing inner diameter (m)

**Beggs-Brill Correlation (Two-phase flow):**

Pressure gradient consists of three components:
$$\left(\frac{dP}{dL}\right)_{total} = \left(\frac{dP}{dL}\right)_{elevation} + \left(\frac{dP}{dL}\right)_{friction} + \left(\frac{dP}{dL}\right)_{acceleration}$$

**Elevation term:**
$$\left(\frac{dP}{dL}\right)_{elevation} = \rho_m g \sin\theta$$

where mixture density:
$$\rho_m = \rho_L H_L + \rho_G (1 - H_L)$$

**Liquid holdup $H_L$** is calculated from flow regime correlations.

#### Nodal Analysis

The operating point is found where IPR and VLP curves intersect:

$$q_{IPR}(P_{wf}) = q_{VLP}(P_{wf})$$

Solved iteratively by finding $P_{wf}$ such that:
$$f(P_{wf}) = q_{IPR}(P_{wf}) - q_{VLP}(P_{wf}) = 0$$

#### Material Balance (Tank Model)

**General Material Balance Equation:**
$$N_p[B_o + (R_p - R_s)B_g] = N B_{oi}\left[\frac{(B_o - B_{oi}) + (R_{si} - R_s)B_g}{B_{oi}} + \frac{mB_{oi}(B_g - B_{gi})}{B_{gi}} + \frac{(1+m)B_{oi}(c_w S_{wi} + c_f)\Delta P}{1 - S_{wi}}\right] + W_e + W_{inj}B_w + G_{inj}B_g$$

For a **solution gas drive** reservoir (no aquifer, no injection):
$$N = \frac{N_p[B_o + (R_p - R_s)B_g]}{(B_o - B_{oi}) + (R_{si} - R_s)B_g}$$

#### Voidage Replacement Ratio (VRR)

$$VRR = \frac{\text{Injection Volume at Reservoir Conditions}}{\text{Production Voidage at Reservoir Conditions}}$$

$$VRR = \frac{W_{inj} \times B_w + G_{inj} \times B_g}{N_p \times B_o + (G_p - N_p \times R_s) \times B_g + W_p \times B_w}$$

where:
- $W_{inj}$ = Water injection rate (Sm³/d)
- $G_{inj}$ = Gas injection rate (Sm³/d)
- $B_w$ = Water formation volume factor (~1.02)
- $B_g$ = Gas formation volume factor
- $B_o$ = Oil formation volume factor
- $R_s$ = Solution gas-oil ratio

**VRR = 1.0** maintains reservoir pressure.

#### Formation Volume Factors

**Oil Formation Volume Factor:**
$$B_o = \frac{V_{oil,reservoir}}{V_{oil,standard}} \approx 1.0 + 0.00013 \times R_s$$

**Gas Formation Volume Factor (real gas):**
$$B_g = \frac{P_{std}}{P} \times \frac{T}{T_{std}} \times Z = \frac{1.01325}{P} \times \frac{T}{288.15} \times Z$$

where $Z$ is the compressibility factor from EOS.

#### Network Solver (Multi-well Gathering)

For a network of $N$ wells connected to a common manifold:

**Conservation of mass:**
$$q_{total} = \sum_{i=1}^{N} q_i$$

**Pressure balance for each well:**
$$P_{wh,i} - \Delta P_{flowline,i} = P_{manifold}$$

**Flowline pressure drop (simplified Beggs-Brill):**
$$\Delta P_{flowline} = \frac{f L \rho_m v^2}{2 D} + \rho_m g \Delta h$$

**Iterative solution (successive substitution):**
1. Assume manifold pressure $P_m$
2. For each well, calculate $P_{wh,i}$ from VLP
3. Calculate flowline pressure drop $\Delta P_i$
4. Check: $P_{wh,i} - \Delta P_i = P_m$ ?
5. Update rates and iterate until convergence

Convergence criterion:
$$\left|\frac{q_{total}^{k+1} - q_{total}^k}{q_{total}^k}\right| < \epsilon$$

#### Hydrate Formation Temperature

**Simplified Hammerschmidt Correlation:**
$$\Delta T = \frac{K_H \times w}{M(100-w)}$$

where:
- $\Delta T$ = Hydrate depression temperature (°C)
- $K_H$ = Constant (2335 for methanol, 1297 for MEG)
- $w$ = Inhibitor concentration (wt%)
- $M$ = Inhibitor molecular weight (32 for methanol, 62 for MEG)

**Hydrate formation condition (gas specific gravity method):**
$$T_{hyd} = 8.9 \times P^{0.285} \times \gamma_g^{0.5}$$

where:
- $T_{hyd}$ = Hydrate formation temperature (°C)
- $P$ = Pressure (bar)
- $\gamma_g$ = Gas specific gravity

#### Wax Appearance Temperature (WAT)

**Coutinho Model (simplified):**
$$\ln(x_i^L \gamma_i^L) = \frac{\Delta H_{fus,i}}{R}\left(\frac{1}{T_m} - \frac{1}{T}\right)$$

For screening, empirical correlations based on n-paraffin content:
$$WAT \approx 30 + 0.5 \times (C_{20+} \text{ content, wt\%})$$

#### Erosion Velocity

**API RP 14E Erosion Velocity Limit:**
$$V_e = \frac{C}{\sqrt{\rho_m}}$$

where:
- $V_e$ = Erosional velocity (m/s)
- $C$ = Empirical constant (100-150 for continuous service)
- $\rho_m$ = Mixture density (kg/m³)

---

### Mechanical Design Mathematics

#### Pressure Vessel Wall Thickness (ASME VIII)

**Cylindrical shell under internal pressure:**
$$t = \frac{P \times R}{S \times E - 0.6 \times P} + CA$$

where:
- $t$ = Required wall thickness (mm)
- $P$ = Design pressure (MPa)
- $R$ = Inside radius (mm)
- $S$ = Allowable stress (MPa)
- $E$ = Joint efficiency (0.7-1.0)
- $CA$ = Corrosion allowance (typically 3-6 mm)

#### Separator Sizing (API 12J)

**Gas capacity (Souders-Brown):**
$$V_{gas,max} = K \sqrt{\frac{\rho_L - \rho_G}{\rho_G}}$$

where:
- $V_{gas,max}$ = Maximum gas velocity (m/s)
- $K$ = Souders-Brown factor (typically 0.05-0.15 m/s)
- $\rho_L$ = Liquid density (kg/m³)
- $\rho_G$ = Gas density (kg/m³)

**Vessel diameter from gas capacity:**
$$D = \sqrt{\frac{4 Q_g}{\pi V_{gas,max}}}$$

**Liquid retention time:**
$$t_{ret} = \frac{V_{liq}}{Q_L}$$

Typical retention times: 2-5 minutes for 2-phase, 5-10 minutes for 3-phase.

#### Compressor Sizing (API 617)

**Polytropic head:**
$$H_p = \frac{Z_{avg} R T_1}{M_w} \times \frac{n}{n-1} \times \left[\left(\frac{P_2}{P_1}\right)^{\frac{n-1}{n}} - 1\right]$$

where:
- $H_p$ = Polytropic head (kJ/kg)
- $Z_{avg}$ = Average compressibility factor
- $R$ = Gas constant (8.314 J/mol·K)
- $T_1$ = Suction temperature (K)
- $M_w$ = Molecular weight (kg/kmol)
- $n$ = Polytropic exponent
- $P_1, P_2$ = Suction and discharge pressures

**Polytropic power:**
$$W_p = \dot{m} \times H_p / \eta_p$$

where:
- $W_p$ = Polytropic power (kW)
- $\dot{m}$ = Mass flow rate (kg/s)
- $\eta_p$ = Polytropic efficiency (typically 0.75-0.85)

**Number of stages:**
$$N_{stages} = \lceil H_p / H_{max,stage} \rceil$$

Typical maximum head per stage: 25-35 kJ/kg.

#### Pipeline Wall Thickness (ASME B31.8 / DNV-ST-F101)

**Barlow formula (internal pressure):**
$$t = \frac{P \times D}{2 \times S \times F \times E \times T}$$

where:
- $t$ = Wall thickness (mm)
- $P$ = Design pressure (bar)
- $D$ = Outside diameter (mm)
- $S$ = Specified Minimum Yield Strength (MPa)
- $F$ = Design factor (0.72 typical for offshore)
- $E$ = Longitudinal joint factor (1.0 for seamless)
- $T$ = Temperature derating factor

**DNV-ST-F101 collapse pressure (subsea):**
$$P_c = \frac{2 t}{D} \times S \times \alpha_u$$

where:
- $\alpha_u$ = Material strength factor (0.96 for NCS)

---

### Power & CO2 Emissions Calculations

#### Power Consumption Estimation

**Total facility power:**
$$P_{total} = P_{compression} + P_{pumping} + P_{heating} + P_{utilities}$$

**Compression power (from EOS):**
$$P_{comp} = \frac{\dot{m} \times H_p}{\eta_p \times \eta_{driver}}$$

where $\eta_{driver}$ = 0.95-0.98 for electric, 0.30-0.40 for gas turbine.

#### CO2 Emission Factors

| Power Source | Emission Factor |
|-------------|-----------------|
| Gas turbine (simple cycle) | 500 kg CO2/MWh |
| Gas turbine (combined cycle) | 350 kg CO2/MWh |
| Power from shore (Nordic grid) | 50 kg CO2/MWh |
| Power from shore (UK grid) | 200 kg CO2/MWh |
| Diesel generator | 600 kg CO2/MWh |

**Annual CO2 emissions:**
$$CO2_{annual} = P_{total} \times t_{op} \times EF$$

where:
- $P_{total}$ = Total power (MW)
- $t_{op}$ = Operating hours (typically 8000 hr/year)
- $EF$ = Emission factor (kg CO2/MWh)

**CO2 intensity:**
$$I_{CO2} = \frac{CO2_{annual}}{Q_{annual,boe}}$$

where:
- $I_{CO2}$ = CO2 intensity (kg CO2/boe)
- $Q_{annual,boe}$ = Annual production (boe/year)

Industry targets: < 10 kg CO2/boe for low-emission facilities.

---

## Process Modeling & Mechanical Design Integration

The `FieldDevelopmentWorkflow` class integrates process simulation, mechanical design, and
sustainability calculations into a unified workflow:

```java
// Configure workflow with mechanical design and emissions
FieldDevelopmentWorkflow workflow = new FieldDevelopmentWorkflow("Barents Sea Discovery");
workflow.setConcept(concept)
    .setFluid(tunedFluid)
    .setProcessSystem(processModel)           // Full process simulation
    .setFidelityLevel(FidelityLevel.DETAILED)
    .setRunMechanicalDesign(true)             // Enable mechanical design
    .setCalculateEmissions(true)              // Enable CO2 calculations
    .setPowerSupplyType("POWER_FROM_SHORE")   // Electrification
    .setGridEmissionFactor(0.05)              // Nordic grid
    .setDesignStandard("Equinor");            // Company standards

// Run workflow
WorkflowResult result = workflow.run();

// Access mechanical design results
System.out.println("Equipment weight: " + result.totalEquipmentWeightTonnes + " tonnes");
System.out.println("Module footprint: " + result.totalFootprintM2 + " m²");

// Access power and emissions
System.out.println("Total power: " + result.totalPowerMW + " MW");
System.out.println("Annual CO2: " + result.annualCO2eKtonnes + " ktonnes/yr");
System.out.println("CO2 intensity: " + result.co2IntensityKgPerBoe + " kg/boe");

// Power breakdown
for (Map.Entry<String, Double> entry : result.powerBreakdownMW.entrySet()) {
    System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " MW");
}
```

### Equipment-Level Mechanical Design

Each process equipment class has an associated mechanical design class:

| Equipment | Mechanical Design Class | Design Standard |
|-----------|------------------------|-----------------|
| Separator | `SeparatorMechanicalDesign` | ASME VIII, API 12J |
| Compressor | `CompressorMechanicalDesign` | API 617 |
| Pump | `PumpMechanicalDesign` | API 610 |
| Valve | `ValveMechanicalDesign` | IEC 60534 |
| Heat Exchanger | `HeatExchangerMechanicalDesign` | TEMA |
| Pipeline | `PipelineMechanicalDesign` | ASME B31.3/B31.8 |
| Tank | `TankMechanicalDesign` | API 650/620 |

```java
// Individual equipment mechanical design
Separator separator = new Separator("V-100", inletStream);
separator.run();

MechanicalDesign mecDesign = separator.getMechanicalDesign();
mecDesign.setCompanySpecificDesignStandards("Equinor");
mecDesign.calcDesign();

// Access results
double weight = mecDesign.getWeightTotal();           // kg
double wallThickness = mecDesign.getWallThickness();  // mm
double innerDiameter = mecDesign.getInnerDiameter();  // m

// Export to JSON
String json = mecDesign.toJson();
```

### System-Wide Design Aggregation

```java
// Create process system
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);
process.add(cooler);
process.run();

// Create system mechanical design
SystemMechanicalDesign sysMecDesign = new SystemMechanicalDesign(process);
sysMecDesign.setCompanySpecificDesignStandards("Equinor");
sysMecDesign.runDesignCalculation();

// Aggregated results
double totalWeight = sysMecDesign.getTotalWeight();       // kg
double totalVolume = sysMecDesign.getTotalVolume();       // m³
double plotSpace = sysMecDesign.getTotalPlotSpace();      // m²
double powerRequired = sysMecDesign.getTotalPowerRequired();  // kW

// Get breakdown by type
Map<String, Double> weightByType = sysMecDesign.getWeightByEquipmentType();
System.out.println("Separators: " + weightByType.get("Separator") + " kg");
System.out.println("Compressors: " + weightByType.get("Compressor") + " kg");
```

### Emissions Tracking Integration

```java
// Process-level emissions tracking
import neqsim.process.sustainability.EmissionsTracker;

EmissionsTracker tracker = new EmissionsTracker(process);
tracker.setGridEmissionFactor(0.05);  // Nordic grid: 50 g CO2/kWh
tracker.setIncludeIndirectEmissions(true);

EmissionsReport report = tracker.calculateEmissions();

// Results by category
System.out.println("Total CO2e: " + report.getTotalCO2e("ton/yr") + " ton/yr");
System.out.println("Compression: " + report.getEmissionsByCategory().get("COMPRESSION"));
System.out.println("Pumping: " + report.getEmissionsByCategory().get("PUMPING"));

// Export for regulatory reporting
report.exportToCSV("emissions_report.csv");
```

---

## Subsea Production System Integration

The `SubseaProductionSystem` class provides a unified abstraction for modeling subsea developments,
integrating wells, flowlines, manifolds, and tieback analysis into the field development workflow.

### Subsea Architecture Types

| Architecture | Description | Use Case |
|--------------|-------------|----------|
| `DIRECT_TIEBACK` | Wells tied directly to host | Short distances (<10km), few wells |
| `MANIFOLD_CLUSTER` | Wells grouped at subsea manifold | Standard development, 4-8 wells |
| `DAISY_CHAIN` | Wells connected in series | Long, narrow reservoir |
| `TEMPLATE` | Multiple wells from single structure | Compact field development |

### SURF Equipment Classes

NeqSim provides comprehensive SURF (Subsea, Umbilical, Riser, Flowline) equipment modeling
in `neqsim.process.equipment.subsea`:

| Equipment | Class | Description |
|-----------|-------|-------------|
| **PLET** | `PLET` | Pipeline End Termination - pipeline termination structures |
| **PLEM** | `PLEM` | Pipeline End Manifold - multi-slot pipeline connections |
| **Subsea Tree** | `SubseaTree` | Christmas tree for well control (vertical/horizontal) |
| **Manifold** | `SubseaManifold` | Production/test/injection routing |
| **Jumper** | `SubseaJumper` | Rigid or flexible connections between equipment |
| **Umbilical** | `Umbilical` | Control and chemical injection lines |
| **Flexible Pipe** | `FlexiblePipe` | Dynamic risers and flowlines |
| **Subsea Booster** | `SubseaBooster` | Multiphase pumps and wet gas compressors |

### Subsea System Configuration

```java
import neqsim.process.fielddevelopment.subsea.SubseaProductionSystem;
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.fielddevelopment.tieback.TiebackAnalyzer;

// Create subsea production system
SubseaProductionSystem subsea = new SubseaProductionSystem("Marginal Gas Satellite");
subsea.setArchitecture(SubseaProductionSystem.SubseaArchitecture.MANIFOLD_CLUSTER)
    .setWaterDepthM(350.0)
    .setTiebackDistanceKm(25.0)
    .setWellCount(4)
    .setRatePerWell(1.5e6)                    // Sm3/day per well
    .setWellheadConditions(180.0, 80.0)       // bara, °C
    .setFlowlineDiameterInches(12.0)
    .setSeabedTemperatureC(4.0)
    .setFlowlineMaterial("Carbon Steel")
    .setReservoirFluid(gasCondensateFluid);   // NeqSim fluid

// Build and run
subsea.build();
subsea.run();

// Get results
SubseaProductionSystem.SubseaSystemResult result = subsea.getResult();
System.out.println("Arrival pressure: " + result.getArrivalPressureBara() + " bara");
System.out.println("Arrival temperature: " + result.getArrivalTemperatureC() + " °C");
System.out.println("Subsea CAPEX: " + result.getTotalSubseaCapexMusd() + " MUSD");

// CAPEX breakdown
System.out.println("  Subsea trees: " + result.getSubseaTreeCostMusd() + " MUSD");
System.out.println("  Manifold: " + result.getManifoldCostMusd() + " MUSD");
System.out.println("  Pipeline: " + result.getPipelineCostMusd() + " MUSD");
System.out.println("  Umbilical: " + result.getUmbilicalCostMusd() + " MUSD");
```

### Detailed SURF Equipment Modeling

For detailed engineering, use the dedicated SURF equipment classes:

```java
import neqsim.process.equipment.subsea.*;
import neqsim.process.mechanicaldesign.subsea.*;

// Create well stream
Stream wellStream = new Stream("Well-1", reservoirFluid);
wellStream.setFlowRate(100000.0, "kg/hr");
wellStream.run();

// Subsea Tree with full configuration
SubseaTree tree = new SubseaTree("Well-1 Tree", wellStream);
tree.setTreeType(SubseaTree.TreeType.HORIZONTAL);
tree.setPressureRating(SubseaTree.PressureRating.PR10000);  // 10,000 psi
tree.setBoreSizeInches(7.0);
tree.setWaterDepth(380.0);
tree.setDesignPressure(690.0);  // bar
tree.setDesignTemperature(121.0);  // °C
tree.run();

// Production Manifold
SubseaManifold manifold = new SubseaManifold("Field Manifold");
manifold.setManifoldType(SubseaManifold.ManifoldType.PRODUCTION_TEST);
manifold.setNumberOfWellSlots(6);
manifold.setProductionHeaderSizeInches(12.0);
manifold.setTestHeaderSizeInches(6.0);
manifold.setWaterDepth(380.0);
manifold.setDesignPressure(250.0);
manifold.addWellStream(tree.getOutletStream(), 1);
manifold.routeWellToProduction(1);
manifold.run();

// Rigid Jumper (Tree to Manifold)
SubseaJumper jumper = new SubseaJumper("Tree-Manifold Jumper", tree.getOutletStream());
jumper.setJumperType(SubseaJumper.JumperType.RIGID_M_SHAPE);
jumper.setLength(50.0);
jumper.setNominalBoreInches(6.0);
jumper.setOuterDiameterInches(6.625);
jumper.setWallThicknessMm(12.7);
jumper.setDesignPressure(200.0);
jumper.setMaterialGrade("X65");
jumper.run();

// Export PLET
PLET exportPLET = new PLET("Export PLET", manifold.getProductionOutputStream());
exportPLET.setConnectionType(PLET.ConnectionType.VERTICAL_HUB);
exportPLET.setHubSizeInches(12.0);
exportPLET.setStructureType(PLET.StructureType.GRAVITY_BASE);
exportPLET.setWaterDepth(380.0);
exportPLET.setDesignPressure(200.0);
exportPLET.setMaterialGrade("X65");
exportPLET.run();

// Dynamic Riser with Lazy-Wave Configuration
FlexiblePipe riser = new FlexiblePipe("Production Riser", exportPLET.getOutletStream());
riser.setPipeType(FlexiblePipe.PipeType.UNBONDED);
riser.setApplication(FlexiblePipe.Application.DYNAMIC_RISER);
riser.setRiserConfiguration(FlexiblePipe.RiserConfiguration.LAZY_WAVE);
riser.setServiceType(FlexiblePipe.ServiceType.OIL_SERVICE);
riser.setLength(1200.0);
riser.setInnerDiameterInches(8.0);
riser.setDesignPressure(200.0);
riser.setWaterDepth(380.0);
riser.setHasBendStiffener(true);
riser.setHasBuoyancyModules(true);
riser.run();

// Control Umbilical
Umbilical umbilical = new Umbilical("Field Umbilical");
umbilical.setUmbilicalType(Umbilical.UmbilicalType.STEEL_TUBE);
umbilical.setLength(48000.0);  // 48 km
umbilical.setWaterDepth(380.0);
umbilical.setHasArmorWires(true);

// Add hydraulic control lines
umbilical.addHydraulicLine(12.7, 517.0, "HP Supply");    // 12.7mm ID, 517 bar
umbilical.addHydraulicLine(12.7, 517.0, "HP Return");
umbilical.addHydraulicLine(9.525, 345.0, "LP Supply");
umbilical.addHydraulicLine(9.525, 345.0, "LP Return");

// Add chemical injection lines
umbilical.addChemicalLine(25.4, 207.0, "MEG Injection");
umbilical.addChemicalLine(19.05, 207.0, "Scale Inhibitor");
umbilical.addChemicalLine(12.7, 207.0, "Corrosion Inhibitor");

// Add electrical and fiber
umbilical.addElectricalCable(35.0, 6600.0, "Power");
umbilical.addElectricalCable(4.0, 500.0, "Signal");
umbilical.addFiberOptic(12, "Communication");

umbilical.run(null);

// Subsea Boosting (if required for pressure support)
SubseaBooster mpPump = new SubseaBooster("MP Pump", manifold.getProductionOutputStream());
mpPump.setBoosterType(SubseaBooster.BoosterType.MULTIPHASE_PUMP);
mpPump.setPumpType(SubseaBooster.PumpType.HELICO_AXIAL);
mpPump.setDriveType(SubseaBooster.DriveType.ELECTRIC);
mpPump.setNumberOfStages(6);
mpPump.setDesignInletPressure(80.0);
mpPump.setDifferentialPressure(50.0);
mpPump.setWaterDepth(380.0);
mpPump.setDesignLifeYears(25);
mpPump.setRetrievable(true);
mpPump.run();
```

### SURF Mechanical Design and Cost Estimation

Each SURF equipment type has a dedicated mechanical design class with integrated cost estimation:

```java
import neqsim.process.mechanicaldesign.subsea.*;

// Initialize mechanical design for equipment
tree.initMechanicalDesign();
manifold.initMechanicalDesign();
jumper.initMechanicalDesign();
exportPLET.initMechanicalDesign();
riser.initMechanicalDesign();
umbilical.initMechanicalDesign();

// Configure and run mechanical designs
SubseaTreeMechanicalDesign treeDesign = 
    (SubseaTreeMechanicalDesign) tree.getMechanicalDesign();
treeDesign.setMaxOperationPressure(690.0);
treeDesign.setDesignStandardCode("API-17D");
treeDesign.setRegion(SubseaCostEstimator.Region.NORWAY);
treeDesign.calcDesign();

PLETMechanicalDesign pletDesign = 
    (PLETMechanicalDesign) exportPLET.getMechanicalDesign();
pletDesign.setMaxOperationPressure(200.0);
pletDesign.setMaterialGrade("X65");
pletDesign.setDesignStandardCode("DNV-ST-F101");
pletDesign.setRegion(SubseaCostEstimator.Region.NORWAY);
pletDesign.calcDesign();

// Get detailed design results
System.out.println("PLET Hub Wall Thickness: " + pletDesign.getHubWallThickness() + " mm");
System.out.println("PLET Mudmat Area: " + pletDesign.getRequiredMudmatArea() + " m²");

// Get cost breakdown
Map<String, Object> treeCosts = treeDesign.getCostBreakdown();
Map<String, Object> pletCosts = pletDesign.getCostBreakdown();

System.out.println("Tree Total Cost: $" + String.format("%,.0f", treeCosts.get("totalCostUSD")));
System.out.println("PLET Total Cost: $" + String.format("%,.0f", pletCosts.get("totalCostUSD")));

// Generate Bill of Materials
List<Map<String, Object>> pletBOM = pletDesign.generateBillOfMaterials();
for (Map<String, Object> item : pletBOM) {
    System.out.println("  " + item.get("item") + ": " + item.get("quantity") + " " + 
        item.get("unit") + " @ $" + String.format("%,.0f", (Double)item.get("unitCost")));
}

// Export full design report as JSON
String designJson = pletDesign.toJson();
```

### Subsea Cost Estimation with Regional Factors

The `SubseaCostEstimator` class provides comprehensive cost estimation with regional adjustments:

```java
import neqsim.process.mechanicaldesign.subsea.SubseaCostEstimator;

SubseaCostEstimator estimator = new SubseaCostEstimator(SubseaCostEstimator.Region.NORWAY);

// Calculate costs for complete SURF system
double totalSurfCapex = 0.0;

// Trees (6 wells)
estimator.calculateTreeCost(10000.0, 7.0, 380.0, true, false);
double treeCost = estimator.getTotalCost();
totalSurfCapex += treeCost * 6;
System.out.println("Trees (6x): $" + String.format("%,.0f", treeCost * 6));

// Manifold
estimator.calculateManifoldCost(6, 80.0, 380.0, true);
double manifoldCost = estimator.getTotalCost();
totalSurfCapex += manifoldCost;
System.out.println("Manifold: $" + String.format("%,.0f", manifoldCost));

// Jumpers (6 x 50m rigid)
estimator.calculateJumperCost(50.0, 6.0, true, 380.0);
double jumperCost = estimator.getTotalCost();
totalSurfCapex += jumperCost * 6;
System.out.println("Jumpers (6x): $" + String.format("%,.0f", jumperCost * 6));

// Export PLET
estimator.calculatePLETCost(25.0, 12.0, 380.0, true, true);
double pletCost = estimator.getTotalCost();
totalSurfCapex += pletCost;
System.out.println("PLET: $" + String.format("%,.0f", pletCost));

// Umbilical (48 km)
estimator.calculateUmbilicalCost(48.0, 4, 3, 2, 380.0, false);
double umbilicalCost = estimator.getTotalCost();
totalSurfCapex += umbilicalCost;
System.out.println("Umbilical: $" + String.format("%,.0f", umbilicalCost));

// Dynamic Riser (1200m)
estimator.calculateFlexiblePipeCost(1200.0, 8.0, 380.0, true, true);
double riserCost = estimator.getTotalCost();
totalSurfCapex += riserCost;
System.out.println("Riser: $" + String.format("%,.0f", riserCost));

System.out.println("\n=== TOTAL SURF CAPEX: $" + String.format("%,.0f", totalSurfCapex) + " ===");

// Compare by region
System.out.println("\nRegional Cost Comparison:");
for (SubseaCostEstimator.Region region : SubseaCostEstimator.Region.values()) {
    SubseaCostEstimator regional = new SubseaCostEstimator(region);
    regional.calculateManifoldCost(6, 80.0, 380.0, true);
    System.out.println("  " + region.name() + ": $" + String.format("%,.0f", regional.getTotalCost()));
}
```

**Regional Cost Factors:**

| Region | Factor | Typical Projects |
|--------|--------|------------------|
| NORWAY | 1.35 | Norwegian Continental Shelf |
| UK | 1.25 | UK North Sea |
| GOM | 1.00 | Gulf of Mexico (baseline) |
| BRAZIL | 0.85 | Pre-salt developments |
| WEST_AFRICA | 1.10 | West African margin |

### Tieback Analysis to Multiple Hosts

```java
// Define potential host facilities
HostFacility host1 = HostFacility.builder("Platform A")
    .location(60.5, 2.3)
    .waterDepth(120)
    .gasCapacity(15.0, "MSm3/d")
    .gasUtilization(0.75)
    .minTieInPressure(80)
    .build();

HostFacility host2 = HostFacility.builder("FPSO B")
    .location(60.8, 2.1)
    .waterDepth(350)
    .gasCapacity(25.0, "MSm3/d")
    .gasUtilization(0.60)
    .build();

// Analyze tieback options
TiebackAnalyzer analyzer = new TiebackAnalyzer();
TiebackReport report = analyzer.analyze(concept, Arrays.asList(host1, host2), 60.6, 2.5);

// Review results
System.out.println("Best option: " + report.getBestFeasibleOption().getHostName());
System.out.println("NPV: " + report.getBestFeasibleOption().getNpvMusd() + " MUSD");

// Print comparison
for (TiebackOption opt : report.getFeasibleOptions()) {
    System.out.println(opt.getHostName() + ": " + opt.getDistanceKm() + " km, " 
        + opt.getTotalCapexMusd() + " MUSD CAPEX");
}
```

### Integrated Subsea Workflow

The subsea system integrates seamlessly with the `FieldDevelopmentWorkflow`:

```java
// Configure workflow with subsea tieback
FieldDevelopmentWorkflow workflow = new FieldDevelopmentWorkflow("Satellite Field");
workflow.setConcept(FieldConcept.gasTieback("Satellite", 25.0, 4, 1.5))
    .setFluid(gasFluid)
    .setFidelityLevel(FidelityLevel.CONCEPTUAL)
    .setRunSubseaAnalysis(true)               // Enable subsea analysis
    .setWaterDepthM(350.0)
    .setTiebackDistanceKm(25.0)
    .setSubseaArchitecture(SubseaProductionSystem.SubseaArchitecture.MANIFOLD_CLUSTER)
    .addHostFacility(host1)
    .addHostFacility(host2)
    .setCountryCode("NO");

// Or provide a pre-configured subsea system
workflow.setSubseaSystem(subsea);

// Run workflow
WorkflowResult result = workflow.run();

// Access subsea results
System.out.println("Subsea CAPEX: " + result.subseaCapexMusd + " MUSD");
System.out.println("Arrival pressure: " + result.arrivalPressureBara + " bara");
System.out.println("Selected host: " + result.selectedTiebackOption.getHostName());

// Full subsea system result
if (result.subseaSystemResult != null) {
    System.out.println(result.subseaSystemResult.getSummary());
}
```

### Subsea Cost Estimation Model

The subsea CAPEX model uses parametric cost estimation:

| Component | Base Cost | Scaling |
|-----------|-----------|---------|
| Subsea tree | 25 MUSD/well | Fixed per well |
| Manifold/template | 35 MUSD | Per manifold |
| Pipeline | 2.5 MUSD/km | Diameter^1.3 × depth factor |
| Umbilical | 1.0 MUSD/km | Length × 1.05 for routing |
| Control system | 3 MUSD/well + 5 MUSD | Includes SCM, HPU |

**Water Depth Factors:**
- < 500m: 1.0
- 500-1000m: 1.0 + (depth - 500) / 1000
- > 1000m: 1.5 + (depth - 1000) / 500

**Material Factors:**
- Carbon Steel: 1.0
- CRA (13% Cr): 2.5
- Flexible: 3.0

### Subsea Flow Assurance Integration

The subsea system integrates with NeqSim's flow assurance capabilities:

```java
// The subsea system uses actual thermodynamic calculations
subsea.setReservoirFluid(gasCondensateFluid);
subsea.build();
subsea.run();

// Arrival conditions reflect actual pressure/temperature drop
double arrivalT = subsea.getArrivalTemperatureC();
double seabedT = 4.0;

// Check hydrate margin
ThermodynamicOperations ops = new ThermodynamicOperations(gasCondensateFluid.clone());
double hydrateT = ops.hydrateFormationTemperature(arrivalP);
double margin = arrivalT - hydrateT;

if (margin < 5.0) {
    System.out.println("WARNING: Hydrate margin is only " + margin + " °C");
    System.out.println("Consider MEG injection or insulation");
}
```

---

## Field Development Lifecycle

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                          FIELD DEVELOPMENT LIFECYCLE                            │
├──────────────┬──────────────┬──────────────┬──────────────┬──────────────────────┤
│  Discovery   │  Feasibility │   Concept    │   FEED/DG2   │  Operations          │
│              │   (DG1)      │   (DG2)      │   (DG3/4)    │                      │
├──────────────┼──────────────┼──────────────┼──────────────┼──────────────────────┤
│ • PVT Lab    │ • Volumetrics│ • EOS Tuning │ • Detailed   │ • History matching   │
│ • GIIP/STOIIP│ • Screening  │ • IPR/VLP    │   reservoir  │ • Production optim.  │
│ • Analogs    │ • Economics  │ • Process    │ • Full CAPEX │ • Debottlenecking    │
│              │   ±50%       │   simulation │   ±20%       │                      │
└──────────────┴──────────────┴──────────────┴──────────────┴──────────────────────┘
```

## NeqSim Classes by Development Phase

### Phase 1: Discovery & Appraisal

**Objective:** Characterize the fluid and estimate volumes

| Task | NeqSim Class | Package |
|------|--------------|---------|
| Create fluid from composition | `SystemSrkEos`, `SystemPrEos`, `SystemSrkCPAstatoil` | `thermo.system` |
| Plus-fraction characterization | `Characterization.Pedersen()` | `thermo.characterization` |
| Saturation pressure | `SaturationPressure` | `pvtsimulation.simulation` |
| CCE/DLE/CVD simulation | `ConstantMassExpansion`, `DifferentialLiberation`, `ConstantVolumeDepletion` | `pvtsimulation.simulation` |
| GOR estimation | `GOR`, `SeparatorTest` | `pvtsimulation.simulation` |
| Fluid type classification | `FluidInput.fluidType()` | `fielddevelopment.concept` |

**Example Workflow:**
```java
// Create reservoir fluid from lab composition
SystemInterface fluid = new SystemSrkEos(373.15, 250.0);
fluid.addComponent("nitrogen", 0.005);
fluid.addComponent("CO2", 0.015);
fluid.addComponent("methane", 0.60);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addTBPfraction("C7+", 0.20, 0.220, 0.85);
fluid.setMixingRule("classic");

// Characterize plus-fraction
fluid.getCharacterization().characterisePlusFraction();

// Calculate bubble point
SaturationPressure satP = new SaturationPressure(fluid);
satP.setTemperature(100.0, "C");
satP.runCalc();
double bubblePoint = satP.getSaturationPressure();

// Run DLE for Bo, Rs, μo
DifferentialLiberation dle = new DifferentialLiberation(fluid);
dle.setTemperature(100.0, "C");
dle.setPressures(new double[]{250, 200, 150, 100, 50, 1.01325}, "bara");
dle.runCalc();
```

---

### Phase 2: Feasibility Study (DG1)

**Objective:** Screen development options and estimate economics (±50%)

| Task | NeqSim Class | Package |
|------|--------------|---------|
| Define concept | `FieldConcept`, `FluidInput`, `WellsInput`, `InfrastructureInput` | `fielddevelopment.concept` |
| Flow assurance screening | `FlowAssuranceScreener` | `fielddevelopment.screening` |
| Hydrate risk | CPA thermodynamics in screener | `fielddevelopment.screening` |
| Wax risk | WAT estimation | `fielddevelopment.screening` |
| Cost estimation (±50%) | `EconomicsEstimator`, `RegionalCostFactors` | `fielddevelopment.screening` |
| Production forecast | `ProductionProfileGenerator` (Arps) | `fielddevelopment.economics` |
| NPV calculation | `CashFlowEngine`, `TaxModel` | `fielddevelopment.economics` |
| Tieback options | `TiebackAnalyzer` | `fielddevelopment.tieback` |
| Batch comparison | `BatchConceptRunner` | `fielddevelopment.evaluation` |

**Example Workflow:**
```java
// Quick concept definition for gas tieback
FieldConcept concept = FieldConcept.quickGasTieback(
    "Satellite Discovery",
    200.0,    // GIIP (GSm3)
    0.02,     // CO2 fraction
    25.0      // Tieback length (km)
);

// Add well details
concept.getWells()
    .setWellCount(4)
    .setInitialRate(2.5e6, "Sm3/day")  // Per well
    .setTHP(80.0, "bara");

// Flow assurance screening
FlowAssuranceScreener faScreener = new FlowAssuranceScreener();
FlowAssuranceResult faResult = faScreener.screen(concept);
System.out.println("Hydrate risk: " + faResult.getHydrateRisk());
System.out.println("Wax risk: " + faResult.getWaxRisk());

// Economics screening
EconomicsEstimator estimator = new EconomicsEstimator("NO");
EconomicsReport costs = estimator.quickEstimate(concept);
System.out.println("CAPEX: " + costs.getTotalCapexMUSD() + " MUSD");

// Production profile (Arps decline)
ProductionProfileGenerator gen = new ProductionProfileGenerator();
Map<Integer, Double> gasProfile = gen.generateFullProfile(
    10.0e6,                       // Peak rate (Sm3/d)
    1,                            // Ramp-up years
    5,                            // Plateau years
    0.12,                         // Decline rate
    ProductionProfileGenerator.DeclineType.EXPONENTIAL,
    2027,                         // First production
    25                            // Field life
);

// Cash flow analysis
CashFlowEngine engine = new CashFlowEngine("NO");
engine.setCapex(costs.getTotalCapexMUSD(), 2025);
engine.setOpexPercentOfCapex(0.04);
engine.setGasPrice(0.30);  // USD/Sm3
engine.setProductionProfile(null, gasProfile, null);

CashFlowResult result = engine.calculate(0.08);
System.out.println("NPV@8%: " + result.getNpv() + " MUSD");
System.out.println("IRR: " + result.getIrr() * 100 + "%");
```

---

### Phase 3: Concept Selection (DG2)

**Objective:** Select preferred concept with EOS-tuned fluid and well models

| Task | NeqSim Class | Package |
|------|--------------|---------|
| EOS tuning to lab data | `PVTRegression` | `pvtsimulation.regression` |
| PVT report generation | `PVTReportGenerator` | `pvtsimulation.util` |
| IPR modeling | `WellFlow` | `process.equipment.reservoir` |
| VLP modeling | `TubingPerformance` | `process.equipment.reservoir` |
| Integrated well model | `WellSystem` | `process.equipment.reservoir` |
| Nodal analysis | `WellSystem.findOperatingPoint()` | `process.equipment.reservoir` |
| Material balance | `SimpleReservoir` | `process.equipment.reservoir` |
| Process simulation | `ProcessSystem` | `process.processmodel` |
| Facility builder | `FacilityBuilder` | `fielddevelopment.facility` |
| Concept evaluation | `ConceptEvaluator` | `fielddevelopment.evaluation` |
| Sensitivity analysis | `SensitivityAnalyzer` | `fielddevelopment.economics` |

**Example Workflow:**
```java
// === EOS TUNING ===
// Start with base fluid
SystemInterface baseFluid = createBaseFluid();

// Add lab data and run regression
PVTRegression regression = new PVTRegression(baseFluid);
regression.addCCEData(ccePressures, cceRelativeVolumes, 100.0);
regression.addDLEData(dlePressures, dleRs, dleBo, dleViscosity, 100.0);
regression.addRegressionParameter(RegressionParameter.BIP_METHANE_C7PLUS, 0.0, 0.10);
regression.addRegressionParameter(RegressionParameter.C7PLUS_MW_MULTIPLIER, 0.9, 1.1);

RegressionResult regResult = regression.runRegression();
SystemInterface tunedFluid = regResult.getTunedFluid();

// === WELL MODELING ===
// Create reservoir stream
Stream reservoirStream = new Stream("Reservoir", tunedFluid);
reservoirStream.setFlowRate(5000.0, "Sm3/day");
reservoirStream.setTemperature(100.0, "C");
reservoirStream.setPressure(250.0, "bara");
reservoirStream.run();

// Integrated IPR + VLP model
WellSystem well = new WellSystem("Producer-1", reservoirStream);
well.setIPRModel(WellSystem.IPRModel.VOGEL);
well.setVogelParameters(8000.0, 180.0, 250.0);  // qTest, pwfTest, pRes
well.setTubingLength(2500.0, "m");
well.setTubingDiameter(4.0, "in");
well.setPressureDropCorrelation(TubingPerformance.PressureDropCorrelation.BEGGS_BRILL);
well.setWellheadPressure(50.0, "bara");
well.run();

double operatingRate = well.getOperatingFlowRate("Sm3/day");
double operatingBHP = well.getOperatingBHP("bara");

// === MATERIAL BALANCE RESERVOIR ===
SimpleReservoir reservoir = new SimpleReservoir("Main Field");
reservoir.setReservoirFluid(tunedFluid, 200e6, 10.0, 10.0);  // GIIP, thickness, area
Stream wellStream = reservoir.addOilProducer("Well-1");
wellStream.setFlowRate(operatingRate, "Sm3/day");

// === PROCESS SIMULATION ===
ProcessSystem process = new ProcessSystem("FPSO");
process.add(reservoir);

// HP Separator
ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Separator", wellStream);
hpSep.setInletPressure(50.0, "bara");
process.add(hpSep);

// Compressor
Compressor compressor = new Compressor("Export Comp", hpSep.getGasOutStream());
compressor.setOutletPressure(150.0, "bara");
process.add(compressor);

process.run();

// === CONCEPT EVALUATION ===
ConceptEvaluator evaluator = new ConceptEvaluator();
ConceptKPIs kpis = evaluator.evaluate(concept, tunedFluid);
System.out.println(kpis.getSummaryReport());
```

---

### Phase 4: FEED / Detailed Design (DG3/DG4)

**Objective:** Finalize design with detailed reservoir coupling and process simulation

| Task | NeqSim Class | Package |
|------|--------------|---------|
| Black oil tables | `BlackOilConverter` | `blackoil` |
| Eclipse PVT export | `EclipseEOSExporter` | `blackoil.io` |
| VFP table generation | `WellSystem.generateLiftCurves()` | `process.equipment.reservoir` |
| Reservoir depletion study | `SimpleReservoir.runDepletion()` | `process.equipment.reservoir` |
| Production scheduling | `FieldProductionScheduler` | `process.util.fielddevelopment` |
| Well scheduling | `WellScheduler` | `process.util.fielddevelopment` |
| Capacity analysis | `FacilityCapacity` | `process.util.fielddevelopment` |
| Monte Carlo | `SensitivityAnalyzer.monteCarloAnalysis()` | `fielddevelopment.economics` |
| MMP calculation | `MMPCalculator` | `pvtsimulation.simulation` |

**Example Workflow:**
```java
// === EXPORT TO RESERVOIR SIMULATOR ===
// Generate black oil tables
BlackOilConverter converter = new BlackOilConverter(tunedFluid);
converter.setReservoirTemperature(373.15);
converter.setPressureRange(1.01325, 300.0, 20);
BlackOilPVTTable pvtTable = converter.convert();

// Export to Eclipse format
EclipseEOSExporter.ExportConfig config = new EclipseEOSExporter.ExportConfig()
    .setUnits(EclipseEOSExporter.Units.METRIC)
    .setIncludePVTO(true)
    .setIncludePVTG(true)
    .setIncludePVTW(true)
    .setIncludeDensity(true);
    
EclipseEOSExporter.toFile(tunedFluid, Path.of("PVT_TABLES.INC"), config);

// === VFP TABLE GENERATION ===
double[] whPressures = {30, 40, 50, 60, 70, 80};  // bara
double[] waterCuts = {0, 0.2, 0.4, 0.6, 0.8};
WellSystem.VFPTable vfp = well.generateLiftCurves(whPressures, waterCuts);
vfp.exportToEclipse("VFP_WELL1.INC");

// === PRODUCTION SCHEDULING ===
FieldProductionScheduler scheduler = new FieldProductionScheduler();
scheduler.addReservoir(reservoir);
scheduler.setFacilityModel(process);
scheduler.setPlateauTarget(10.0e6, "Sm3/day");
scheduler.setEconomicLimit(0.5e6, "Sm3/day");
scheduler.setGasPrice(0.30);
scheduler.setDiscountRate(0.08);

ScheduleResult schedule = scheduler.runScheduling(2027, 2052);
System.out.println(schedule.getProductionForecast());
System.out.println(schedule.getEconomicSummary());

// === MONTE CARLO ANALYSIS ===
SensitivityAnalyzer analyzer = new SensitivityAnalyzer(engine, 0.08);
analyzer.setOilPriceDistribution(50.0, 100.0);
analyzer.setCapexDistribution(800, 1200);
analyzer.setProductionFactorDistribution(0.8, 1.2);

MonteCarloResult mc = analyzer.monteCarloAnalysis(10000);
System.out.println("P10: " + mc.getNpvP10() + " MUSD");
System.out.println("P50: " + mc.getNpvP50() + " MUSD");
System.out.println("P90: " + mc.getNpvP90() + " MUSD");
```

---

## Key Integration Points

### 1. PVT → Reservoir Coupling

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  Lab PVT Data    │────▶│  PVTRegression   │────▶│  Tuned EOS       │
│  (CCE/DLE/CVD)   │     │  (Parameter fit) │     │  SystemInterface │
└──────────────────┘     └──────────────────┘     └────────┬─────────┘
                                                           │
                         ┌─────────────────────────────────┼─────────────────────┐
                         │                                 │                     │
                         ▼                                 ▼                     ▼
               ┌──────────────────┐           ┌──────────────────┐    ┌──────────────────┐
               │ BlackOilConverter│           │  SimpleReservoir │    │  Process Streams │
               │ PVTO/PVTG tables │           │  Material Balance│    │  Compositional   │
               └────────┬─────────┘           └────────┬─────────┘    └────────┬─────────┘
                        │                              │                       │
                        ▼                              ▼                       ▼
               ┌──────────────────┐           ┌──────────────────┐    ┌──────────────────┐
               │ Eclipse/OPM      │           │  WellSystem      │    │  ProcessSystem   │
               │ Reservoir Sim    │           │  IPR/VLP Nodal   │    │  Facility Model  │
               └──────────────────┘           └──────────────────┘    └──────────────────┘
```

### 2. Well → Reservoir → Facility Loop

```java
// Nodal analysis loop with reservoir depletion
for (int year = 2027; year <= 2050; year++) {
    // Update reservoir pressure
    reservoir.runDepletion(365.0);  // 1 year
    double pRes = reservoir.getAveragePressure("bara");
    
    // Update IPR with new reservoir pressure
    well.setReservoirPressure(pRes, "bara");
    well.run();
    
    double newRate = well.getOperatingFlowRate("Sm3/day");
    
    // Check facility constraints
    if (newRate > facilityCapacity.getMaxGasRate()) {
        newRate = facilityCapacity.getMaxGasRate();
        // Back-calculate required choke setting
        well.setTargetRate(newRate, "Sm3/day");
        well.run();
    }
    
    schedule.addYear(year, newRate, pRes);
}
```

### 3. Economics → Decision Support

```java
// Compare multiple development options
BatchConceptRunner batch = new BatchConceptRunner();

// Option A: Direct tieback to platform
batch.addConcept(FieldConcept.quickGasTieback("Tieback-A", 200, 0.02, 15));

// Option B: Standalone FPSO
batch.addConcept(FieldConcept.quickOilDevelopment("FPSO-B", 50, 0.03));

// Option C: Subsea to shore
batch.addConcept(FieldConcept.quickGasTieback("S2S-C", 200, 0.02, 80));

// Run parallel evaluation
batch.evaluateAll();

// Get ranked results
List<ConceptKPIs> ranked = batch.getRankedResults();
for (ConceptKPIs kpi : ranked) {
    System.out.printf("%s: NPV=%.0f MUSD, Score=%.2f%n",
        kpi.getConceptName(), kpi.getNpv(), kpi.getOverallScore());
}
```

---

## Study Fidelity Levels

### Level 1: Screening (±50% accuracy)

```java
// Minimal input - analog-based
FieldConcept concept = FieldConcept.quickGasTieback(name, giip, co2, distance);
ConceptKPIs kpis = new ConceptEvaluator().quickScreen(concept);
```

**Inputs:** Fluid type, volumes (GIIP/STOIIP), distance, water depth  
**Models:** Correlations, analog-based costs, Arps decline  
**Outputs:** Order-of-magnitude CAPEX/OPEX, screening-level NPV

### Level 2: Conceptual (±30% accuracy)

```java
// EOS fluid, IPR/VLP, process blocks
SystemInterface fluid = createFluidFromComposition(labData);
WellSystem well = new WellSystem("Well-1", reservoirStream);
FacilityConfig facility = FacilityBuilder.forConcept(concept).autoGenerate().build();
ConceptKPIs kpis = new ConceptEvaluator().evaluate(concept, fluid, facility);
```

**Inputs:** Composition, lab PVT, well test data, facility configuration  
**Models:** EOS thermodynamics, Vogel/Fetkovich IPR, Beggs-Brill VLP  
**Outputs:** Detailed CAPEX breakdown, production forecast, flow assurance risk

### Level 3: Detailed (±20% accuracy)

```java
// Tuned EOS, full process simulation, Monte Carlo
PVTRegression regression = new PVTRegression(baseFluid);
regression.addCCEData(ccePressures, cceRelativeVolumes, temp);
regression.addDLEData(dlePressures, dleRs, dleBo, dleViscosity, temp);
SystemInterface tunedFluid = regression.runRegression().getTunedFluid();

ProcessSystem process = new ProcessSystem();
// ... detailed equipment configuration
process.run();

SensitivityAnalyzer analyzer = new SensitivityAnalyzer(engine, discountRate);
MonteCarloResult mc = analyzer.monteCarloAnalysis(10000);
```

**Inputs:** Full PVT report, well test interpretation, vendor quotes  
**Models:** Tuned EOS, mechanistic correlations, rigorous process simulation  
**Outputs:** P10/P50/P90 economics, FEED-level design

---

## Topics from TPG4230 Course Mapped to NeqSim

Based on the NTNU course "Field Development and Operations" (TPG4230), here is how
each topic maps to NeqSim capabilities:

| Course Topic | NeqSim Implementation | Status |
|--------------|----------------------|--------|
| **Life cycle of hydrocarbon field** | `FieldProductionScheduler`, `CashFlowEngine` | ✅ Complete |
| **Field development workflow** | `ConceptEvaluator`, `BatchConceptRunner` | ✅ Complete |
| **Probabilistic reserve estimation** | `SensitivityAnalyzer.monteCarloAnalysis()` | ✅ Complete |
| **Project economic evaluation** | `CashFlowEngine`, `TaxModel`, NPV/IRR | ✅ 15+ countries |
| **Offshore field architectures** | `FieldConcept`, `InfrastructureInput` | ✅ Complete |
| **Production systems** | `WellSystem` (IPR+VLP), `TubingPerformance` | ✅ Complete |
| **Injection systems** | `InjectionWellModel`, injectivity index, Hall plot | ✅ Complete |
| **Reservoir depletion** | `SimpleReservoir`, material balance | ✅ Tank model |
| **Field performance** | `ProductionProfile`, decline curves | ✅ Complete |
| **Production scheduling** | `FieldProductionScheduler`, `WellScheduler` | ✅ Complete |
| **Flow assurance** | `FlowAssuranceScreener`, hydrate/wax/corrosion | ✅ Complete |
| **Boosting (ESP, gas lift)** | `GasLiftCalculator`, `ArtificialLiftScreener` (6 methods) | ✅ Complete |
| **Field processing** | `ProcessSystem`, separators, compressors | ✅ Complete |
| **Export product control** | `ProcessSystem` export streams | ✅ Complete |
| **Integrated asset modeling** | `SimpleReservoir` + `ProcessSystem` | ✅ Complete |
| **Energy efficiency** | `EnergyEfficiencyCalculator`, SEC/EEI benchmarking | ✅ Complete |
| **Emissions to air/sea** | `DetailedEmissionsCalculator`, Scope 1/2/3 | ✅ Complete |

---

## Recommended Workflow by Project Phase

### Feasibility (Week 1-4)

1. **Define fluid type and volumes**
   ```java
   FluidInput fluid = new FluidInput().fluidType(FluidType.GAS_CONDENSATE).gor(3000);
   ```

2. **Screen concepts with `BatchConceptRunner`**
   ```java
   batch.addConcept(concept1);
   batch.addConcept(concept2);
   batch.evaluateAll();
   ```

3. **Generate economics comparison**
   ```java
   batch.generateComparisonReport("concepts_comparison.md");
   ```

### Concept Select (Week 5-12)

1. **Tune EOS to lab data**
   ```java
   PVTRegression regression = new PVTRegression(fluid);
   regression.addCCEData(...);
   SystemInterface tuned = regression.runRegression().getTunedFluid();
   ```

2. **Build well model (IPR + VLP)**
   ```java
   WellSystem well = new WellSystem("Producer", stream);
   well.setVogelParameters(qTest, pwfTest, pRes);
   well.setTubingLength(2500, "m");
   ```

3. **Run process simulation**
   ```java
   ProcessSystem process = new ProcessSystem();
   process.add(separator);
   process.add(compressor);
   process.run();
   ```

4. **Sensitivity analysis**
   ```java
   SensitivityAnalyzer analyzer = new SensitivityAnalyzer(engine, 0.08);
   TornadoResult tornado = analyzer.tornadoAnalysis(0.20);
   ```

### FEED (Week 13-26)

1. **Export to reservoir simulator**
   ```java
   EclipseEOSExporter.toFile(tunedFluid, Path.of("PVT.INC"));
   well.exportVFPToEclipse("VFP.INC");
   ```

2. **Full production scheduling**
   ```java
   FieldProductionScheduler scheduler = new FieldProductionScheduler();
   scheduler.runScheduling(2027, 2052);
   ```

3. **Monte Carlo economics**
   ```java
   MonteCarloResult mc = analyzer.monteCarloAnalysis(10000);
   double probPositiveNPV = mc.getProbabilityPositiveNpv();
   ```

---

## Future Enhancements

### Near-term (Priority)

1. **Network solver** - Multi-well gathering network pressure balance
2. **Transient well model** - Time-dependent IPR with pressure buildup/drawdown
3. **Water injection support** - Full injection well modeling
4. **Gas lift optimization** - Optimal gas allocation across wells

### Medium-term

1. **Eclipse/OPM coupling** - Direct reservoir simulator integration
2. **Real-time data integration** - OSDU/WITSML connection
3. **Machine learning** - Decline curve prediction from analogs
4. **Optimization** - Portfolio optimization across multiple fields

### Long-term

1. **Digital twin** - Live field model with data assimilation
2. **Carbon storage** - CO2 injection field development
3. **Hydrogen/ammonia** - Energy transition applications

---

## See Also

- [Economics Module](../process/economics/) - NPV, tax models, cash flow
- [Well Simulation Guide](../simulation/well_simulation_guide) - IPR/VLP details
- [PVT Workflow](../pvtsimulation/pvt_workflow) - EOS tuning guide
- [Reservoir Modeling](../process/equipment/reservoirs) - SimpleReservoir API
- [Field Development Strategy](FIELD_DEVELOPMENT_STRATEGY) - Architecture overview
