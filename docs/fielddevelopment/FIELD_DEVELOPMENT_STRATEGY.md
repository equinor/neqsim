---
title: NeqSim Field Development Strategy
description: This document outlines a comprehensive plan to transform NeqSim into a premier tool for **field development screening**, **production scheduling**, **tie-back analysis**, and **new development plannin...
---

# NeqSim Field Development Strategy

## Executive Summary

This document outlines a comprehensive plan to transform NeqSim into a premier tool for **field development screening**, **production scheduling**, **tie-back analysis**, and **new development planning**. The strategy builds on existing NeqSim strengths (thermodynamics, process simulation) while adding high-level orchestration capabilities.

---

## Current State Analysis

### Existing Building Blocks ✅

NeqSim already has substantial infrastructure for field development:

| Package | Class | Purpose | Maturity |
|---------|-------|---------|----------|
| `process.equipment.reservoir` | `SimpleReservoir` | Tank-type material balance model | ✅ Stable |
| `process.equipment.reservoir` | `WellFlow` | IPR (inflow performance) modeling | ✅ Stable |
| `process.equipment.reservoir` | `WellSystem` | Combined IPR + VLP (tubing) model | ✅ Stable |
| `process.equipment.reservoir` | `TubingPerformance` | Vertical lift performance | ✅ Stable |
| `process.fielddevelopment.concept` | `FieldConcept` | High-level concept definition | ✅ New |
| `process.fielddevelopment.concept` | `ReservoirInput` | Reservoir characterization | ✅ New |
| `process.fielddevelopment.concept` | `WellsInput` | Well configuration | ✅ New |
| `process.fielddevelopment.concept` | `InfrastructureInput` | Infrastructure definition | ✅ New |
| `process.fielddevelopment.screening` | `FlowAssuranceScreener` | Hydrate/wax/corrosion screening | ✅ New |
| `process.fielddevelopment.screening` | `EconomicsEstimator` | CAPEX/OPEX estimation | ✅ New |
| `process.fielddevelopment.screening` | `SafetyScreener` | Safety screening | ✅ New |
| `process.fielddevelopment.screening` | `EmissionsTracker` | CO2 emissions estimation | ✅ New |
| `process.fielddevelopment.evaluation` | `ConceptEvaluator` | Concept orchestration | ✅ New |
| `process.fielddevelopment.evaluation` | `BatchConceptRunner` | Multi-concept comparison | ✅ New |
| `process.fielddevelopment.facility` | `FacilityBuilder` | Modular facility configuration | ✅ New |
| `process.util.fielddevelopment` | `ProductionProfile` | Decline curve modeling | ✅ Stable |
| `process.util.fielddevelopment` | `WellScheduler` | Well intervention scheduling | ✅ Stable |
| `process.util.fielddevelopment` | `FacilityCapacity` | Bottleneck analysis | ✅ Stable |
| `process.util.fielddevelopment` | `SensitivityAnalysis` | Monte Carlo analysis | ✅ Stable |
| `process.util.fielddevelopment` | `FieldProductionScheduler` | Production scheduling | 🔄 New (basic) |
| `process.util.optimization` | `ProductionOptimizer` | Production optimization | ✅ Stable |

### Gaps Identified 🔴

1. **Tie-back Analysis Engine** - No dedicated tie-back screening tool
2. **Multi-Field Portfolio** - No portfolio optimization across fields
3. **Norwegian Petroleum Economics** - No tax model (22% corp + 56% special)
4. **Facilities Integration** - Limited connection between concept and process
5. **Time-Series Export** - No E300/ECLIPSE integration for reservoir coupling
6. **Decision Support** - No ranking/scoring for development options
7. **Pipeline Hydraulics Integration** - Loose coupling with multiphase flow

---

## Strategic Architecture

### Proposed Module Structure

```
neqsim.process.fielddevelopment
├── concept/                     # EXISTING - Concept definition
│   ├── FieldConcept.java
│   ├── ReservoirInput.java
│   ├── WellsInput.java
│   └── InfrastructureInput.java
│
├── evaluation/                  # EXISTING - Concept evaluation
│   ├── ConceptEvaluator.java
│   ├── ConceptKPIs.java
│   └── BatchConceptRunner.java
│
├── screening/                   # EXISTING - Screening tools
│   ├── FlowAssuranceScreener.java
│   ├── EconomicsEstimator.java
│   ├── SafetyScreener.java
│   └── EmissionsTracker.java
│
├── facility/                    # EXISTING - Facility blocks
│   ├── FacilityBuilder.java
│   ├── FacilityConfig.java
│   ├── BlockType.java
│   └── BlockConfig.java
│
├── tieback/                     # NEW - Tie-back analysis
│   ├── TiebackAnalyzer.java
│   ├── TiebackOption.java
│   ├── TiebackReport.java
│   └── HostFacility.java
│
├── portfolio/                   # NEW - Multi-field portfolio
│   ├── PortfolioOptimizer.java
│   ├── FieldAsset.java
│   ├── InvestmentSchedule.java
│   └── PortfolioReport.java
│
├── economics/                   # NEW - Advanced economics
│   ├── NorwegianTaxModel.java
│   ├── CashFlowEngine.java
│   ├── NPVCalculator.java
│   ├── BreakevenAnalyzer.java
│   └── TariffModel.java
│
└── scheduling/                  # NEW - Production scheduling
    ├── FieldScheduler.java
    ├── ProductionForecast.java
    ├── DrillSchedule.java
    └── FacilitiesSchedule.java
```

### Proposed Utility Location

```
neqsim.process.util.fielddevelopment
├── ProductionProfile.java       # EXISTING
├── WellScheduler.java           # EXISTING
├── FacilityCapacity.java        # EXISTING
├── SensitivityAnalysis.java     # EXISTING
├── FieldProductionScheduler.java # EXISTING - Enhance
└── PipelineNetwork.java         # NEW - Multi-segment pipeline
```

---

## Implementation Plan

### Phase 1: Core Economics (Priority: HIGH) 🎯

**Goal**: Enable accurate NPV and decision-support calculations

#### 1.1 Norwegian Petroleum Tax Model

```java
package neqsim.process.fielddevelopment.economics;

/**
 * Norwegian Continental Shelf petroleum tax model.
 * 
 * Implements:
 * - 22% corporate tax
 * - 56% special petroleum tax  
 * - Uplift deductions
 * - Loss carry-forward
 */
public class NorwegianTaxModel {
    private static final double CORPORATE_TAX_RATE = 0.22;
    private static final double PETROLEUM_TAX_RATE = 0.56;
    private static final double TOTAL_MARGINAL_RATE = 0.78;
    
    private double upliftRate = 0.055; // 5.5% per year for 4 years
    private int upliftYears = 4;
    
    public TaxResult calculateTax(double grossRevenue, double opex, 
                                   double depreciation, double uplift) {
        // Corporate tax base
        double corporateTaxBase = grossRevenue - opex - depreciation;
        double corporateTax = Math.max(0, corporateTaxBase * CORPORATE_TAX_RATE);
        
        // Special petroleum tax base (with uplift)
        double specialTaxBase = grossRevenue - opex - depreciation - uplift;
        double specialTax = Math.max(0, specialTaxBase * PETROLEUM_TAX_RATE);
        
        return new TaxResult(corporateTax, specialTax, 
                            corporateTax + specialTax);
    }
}
```

#### 1.2 Cash Flow Engine

```java
package neqsim.process.fielddevelopment.economics;

/**
 * Full-lifecycle cash flow engine for field development.
 */
public class CashFlowEngine {
    private NorwegianTaxModel taxModel;
    private TariffModel tariffModel;
    
    public CashFlowResult generateCashFlow(
        ProductionForecast production,
        CapexSchedule capex,
        OpexProfile opex,
        PriceScenario prices,
        int forecastYears
    ) {
        // Year-by-year cash flow with tax
    }
    
    public double calculateNPV(CashFlowResult cashFlow, double discountRate);
    public double calculateIRR(CashFlowResult cashFlow);
    public double calculateBreakevenPrice(CashFlowResult cashFlow, 
                                          double targetNPV);
    public double calculatePaybackPeriod(CashFlowResult cashFlow);
}
```

### Phase 2: Tie-back Analysis Engine (Priority: HIGH) 🎯

**Goal**: Screen and compare tie-back options to existing infrastructure

#### 2.1 Tie-back Analyzer

```java
package neqsim.process.fielddevelopment.tieback;

/**
 * Analyzes tie-back options for marginal field development.
 * 
 * Considers:
 * - Distance to host
 * - Host spare capacity (gas, oil, water handling)
 * - Pipeline hydraulics (pressure drop, flow assurance)
 * - Cost comparison
 */
public class TiebackAnalyzer {
    
    public TiebackReport analyze(FieldConcept discovery, 
                                  List<HostFacility> hosts) {
        List<TiebackOption> options = new ArrayList<>();
        
        for (HostFacility host : hosts) {
            TiebackOption option = evaluateTieback(discovery, host);
            if (option.isFeasible()) {
                options.add(option);
            }
        }
        
        // Rank by NPV
        options.sort(Comparator.comparing(TiebackOption::getNpv).reversed());
        
        return new TiebackReport(discovery, options);
    }
    
    private TiebackOption evaluateTieback(FieldConcept discovery, 
                                           HostFacility host) {
        // 1. Check distance and water depth
        // 2. Screen flow assurance (hydrate, wax in flowline)
        // 3. Check host capacity constraints
        // 4. Estimate CAPEX (pipeline, umbilical, subsea)
        // 5. Calculate production profile (constrained by host)
        // 6. Calculate NPV
    }
}
```

#### 2.2 Host Facility Model

```java
package neqsim.process.fielddevelopment.tieback;

/**
 * Represents an existing host facility with spare capacity.
 */
public class HostFacility {
    private String name;
    private double latitude;
    private double longitude;
    private double waterDepth;
    
    // Capacity constraints
    private double gasCapacityMSm3d;
    private double oilCapacityBopd;
    private double waterCapacityM3d;
    private double liquidCapacityM3d;
    
    // Current utilization
    private double gasUtilization;
    private double oilUtilization;
    private double waterUtilization;
    
    // Tie-in points
    private double minTieInPressureBara;
    private double maxTieInPressureBara;
    
    // Associated process system (optional)
    private ProcessSystem facility;
    
    public double getSpareGasCapacity() {
        return gasCapacityMSm3d * (1.0 - gasUtilization);
    }
    
    public boolean canAccept(FieldConcept discovery) {
        // Check if host has capacity for new tieback
    }
}
```

### Phase 3: Enhanced FieldProductionScheduler (Priority: HIGH) 🎯

**Goal**: Transform into full-featured production scheduler

#### 3.1 Enhance Existing FieldProductionScheduler

Add to [FieldProductionScheduler.java](src/main/java/neqsim/process/util/fielddevelopment/FieldProductionScheduler.java):

```java
// Norwegian Tax Integration
private NorwegianTaxModel taxModel = new NorwegianTaxModel();
private TariffModel tariffModel;
private double corporateTaxRate = 0.22;
private double petroleumTaxRate = 0.56;

// Enhanced Economics
public void setTariffModel(TariffModel tariff);
public void setTaxModel(NorwegianTaxModel taxModel);
public double calculateAfterTaxNPV(double discountRate);
public double calculateBreakevenOilPrice();
public double calculateBreakevenGasPrice();

// Transient Sub-stepping (from notebook patterns)
public void setTransientSubSteps(int subSteps); // e.g., 10 per time step
private void runReservoirTransient(double timestepDays, int subSteps);

// Pipeline Pressure Constraints
public void setPipelinePressureConstraint(double minPressureBara);
public void useAdjusterForRateOptimization(boolean enable);

// Drilling Schedule Integration
public void setDrillSchedule(DrillSchedule schedule);
public void addWellOnlineDate(String wellName, LocalDate date);

// Enhanced Reporting
public CashFlowResult getCashFlow();
public Map<String, Double> getSensitivityToOilPrice(double[] prices);
public String exportToExcel();
```

### Phase 4: Portfolio Optimization (Priority: MEDIUM)

**Goal**: Optimize investment across multiple fields/opportunities

#### 4.1 Portfolio Optimizer

```java
package neqsim.process.fielddevelopment.portfolio;

/**
 * Optimizes capital allocation across a portfolio of opportunities.
 * 
 * Considers:
 * - Capital budget constraints
 * - Risk diversification
 * - Synergies (shared infrastructure)
 * - Phasing and timing
 */
public class PortfolioOptimizer {
    private List<FieldAsset> assets;
    private double annualCapexBudget;
    private double maxPortfolioRisk;
    
    public InvestmentSchedule optimize(int planningHorizon) {
        // Mixed-integer programming for optimal phasing
    }
    
    public PortfolioReport analyze() {
        // Risk-return analysis
        // Efficient frontier
        // Sensitivity analysis
    }
}
```

### Phase 5: Pipeline Network (Priority: MEDIUM)

**Goal**: Multi-segment pipeline network for complex tie-backs

```java
package neqsim.process.util.fielddevelopment;

/**
 * Multi-segment pipeline network for tie-back analysis.
 */
public class PipelineNetwork {
    private List<PipelineSegment> segments;
    private List<Node> nodes;
    
    public void addSegment(String from, String to, 
                           double lengthKm, double diameterInches,
                           double roughness, boolean insulated);
    
    public void addNode(String name, NodeType type);
    
    public NetworkResult solve(Map<String, Double> sourceRates,
                               Map<String, Double> sinkPressures);
    
    public FlowAssuranceReport screenFlowAssurance(double seabedTempC);
}
```

---

## Use Case Workflows

### Use Case 1: Gas Tie-back Screening

```java
// 1. Define discovery
FieldConcept discovery = FieldConcept.builder("Marginal Gas Discovery")
    .reservoir(ReservoirInput.leanGas()
        .gor(15000)
        .co2Percent(2.5)
        .reservoirPressure(350)
        .reservoirTemperature(95)
        .build())
    .wells(WellsInput.builder()
        .producerCount(2)
        .tubeheadPressure(120)
        .ratePerWell(0.8e6, "Sm3/d")
        .build())
    .build();

// 2. Define potential hosts
List<HostFacility> hosts = Arrays.asList(
    HostFacility.builder("Platform A")
        .location(61.5, 2.3)
        .waterDepth(110)
        .spareGasCapacity(3.0, "MSm3/d")
        .minTieInPressure(80)
        .build(),
    HostFacility.builder("FPSO B")
        .location(61.8, 2.1)
        .waterDepth(350)
        .spareGasCapacity(5.0, "MSm3/d")
        .build()
);

// 3. Analyze options
TiebackAnalyzer analyzer = new TiebackAnalyzer();
TiebackReport report = analyzer.analyze(discovery, hosts);

// 4. Review results
System.out.println(report.getSummary());
TiebackOption best = report.getBestOption();
System.out.println("Best option: " + best.getHostName() + 
                   ", NPV: " + best.getNpvMUSD() + " MUSD");
```

### Use Case 2: Field Development with NPV

```java
// 1. Create reservoir model
SystemInterface gasFluid = new SystemSrkEos(273.15 + 90, 300);
gasFluid.addComponent("methane", 0.85);
gasFluid.addComponent("ethane", 0.08);
gasFluid.addComponent("propane", 0.04);
gasFluid.addComponent("CO2", 0.03);
gasFluid.setMixingRule("classic");

SimpleReservoir reservoir = new SimpleReservoir("Gas Field");
reservoir.setReservoirFluid(gasFluid, 5.0e9, 1.0, 1.0e8);
reservoir.addGasProducer("GP-1");
reservoir.addGasProducer("GP-2");

// 2. Create scheduler with economics
FieldProductionScheduler scheduler = new FieldProductionScheduler("Offshore Gas");
scheduler.addReservoir(reservoir);

// 3. Set production parameters
scheduler.setPlateauRate(10.0, "MSm3/day");
scheduler.setPlateauDuration(5, "years");
scheduler.setMinimumRate(1.0, "MSm3/day");

// 4. Set economics
scheduler.setGasPrice(0.25, "USD/Sm3");
scheduler.setDiscountRate(0.08);
scheduler.setCapex(800, "MUSD");
scheduler.setOpexRate(0.04); // 4% of CAPEX per year
scheduler.setTaxModel(new NorwegianTaxModel());

// 5. Generate schedule
ProductionSchedule schedule = scheduler.generateSchedule(
    LocalDate.of(2026, 1, 1), 
    20.0,  // years
    365.0  // annual steps
);

// 6. Results
System.out.println("Cumulative Gas: " + schedule.getCumulativeGas("GSm3") + " GSm3");
System.out.println("Pre-tax NPV: " + schedule.getPreTaxNPV("MUSD") + " MUSD");
System.out.println("After-tax NPV: " + schedule.getAfterTaxNPV("MUSD") + " MUSD");
System.out.println("Breakeven gas price: " + 
    scheduler.calculateBreakevenGasPrice() + " USD/Sm3");
```

### Use Case 3: Portfolio Investment Planning

```java
// 1. Define portfolio
PortfolioOptimizer optimizer = new PortfolioOptimizer();

optimizer.addAsset(FieldAsset.builder("Gas Field A")
    .npv(500)
    .capex(800)
    .firstProduction(2026)
    .reserves(15, "GSm3")
    .build());

optimizer.addAsset(FieldAsset.builder("Oil Development B")
    .npv(300)
    .capex(1200)
    .firstProduction(2027)
    .reserves(50, "MMbbl")
    .build());

optimizer.addAsset(FieldAsset.builder("Tieback C")
    .npv(150)
    .capex(200)
    .firstProduction(2025)
    .reserves(3, "GSm3")
    .build());

// 2. Set constraints
optimizer.setAnnualCapexBudget(500, "MUSD");
optimizer.setPlanningHorizon(10); // years

// 3. Optimize
InvestmentSchedule schedule = optimizer.optimize();

// 4. Results
System.out.println(schedule.getGanttChart());
System.out.println("Portfolio NPV: " + schedule.getTotalNPV());
System.out.println("Capital efficiency: " + schedule.getCapitalEfficiency());
```

---

## Integration Points

### With Existing NeqSim

| Component | Integration |
|-----------|-------------|
| `SystemInterface` | Fluid PVT for reservoir/flow assurance |
| `SimpleReservoir` | Material balance depletion |
| `WellFlow` / `WellSystem` | IPR/VLP for well modeling |
| `ProcessSystem` | Facility simulation |
| `AdiabaticTwoPhasePipe` | Pipeline hydraulics |
| `Adjuster` | Rate optimization to constraints |

### With External Tools

| Tool | Integration Method |
|------|-------------------|
| ECLIPSE/E300 | Export SCHEDULE section |
| Excel | Export time series / reports |
| Python/Jupyter | neqsim-python bindings |
| Power BI | CSV/JSON export |
| Spotfire | Data export APIs |

---

## Testing Strategy

### Unit Tests

```
src/test/java/neqsim/process/fielddevelopment/
├── economics/
│   ├── NorwegianTaxModelTest.java
│   ├── CashFlowEngineTest.java
│   └── NPVCalculatorTest.java
├── tieback/
│   ├── TiebackAnalyzerTest.java
│   └── HostFacilityTest.java
├── portfolio/
│   └── PortfolioOptimizerTest.java
└── scheduling/
    └── FieldSchedulerTest.java
```

### Integration Tests

- Volve field case study (public data)
- Synthetic gas tie-back scenarios
- Multi-reservoir commingled production

---

## Implementation Priority

| Phase | Component | Priority | Effort | Value |
|-------|-----------|----------|--------|-------|
| 1.1 | NorwegianTaxModel | HIGH | 2 days | HIGH |
| 1.2 | CashFlowEngine | HIGH | 3 days | HIGH |
| 2.1 | TiebackAnalyzer | HIGH | 5 days | VERY HIGH |
| 2.2 | HostFacility | HIGH | 2 days | HIGH |
| 3.1 | FieldProductionScheduler enhancements | HIGH | 3 days | HIGH |
| 4.1 | PortfolioOptimizer | MEDIUM | 5 days | MEDIUM |
| 5.1 | PipelineNetwork | MEDIUM | 4 days | MEDIUM |

---

## Success Metrics

1. **Screening Speed**: Evaluate tie-back option in < 5 seconds
2. **Accuracy**: NPV within ±20% of detailed engineering
3. **Usability**: Simple API for common workflows
4. **Integration**: Seamless connection to existing NeqSim
5. **Documentation**: Complete JavaDoc and examples

---

## Next Steps

1. **Immediate**: Implement NorwegianTaxModel and CashFlowEngine
2. **Week 1**: Enhance FieldProductionScheduler with tax integration
3. **Week 2**: Implement TiebackAnalyzer and HostFacility
4. **Week 3**: Create integration tests with Volve data
5. **Week 4**: Portfolio optimizer (if time permits)

---

## Appendix: Norwegian Petroleum Economics Reference

### Tax Rates (2024)
- Corporate tax: 22%
- Special petroleum tax: 56%
- **Total marginal rate: 78%**

### Deductions
- Depreciation: Straight-line over 6 years
- Uplift: 5.5% per year for 4 years (22% total)
- Exploration costs: 100% deductible

### Tariffs (Typical)
- Gas transport (Gassled): 0.10-0.15 NOK/Sm³
- Oil transport: Varies by system

### Price Assumptions (Planning)
- Oil: 70-80 USD/bbl
- Gas: 0.20-0.30 USD/Sm³
- Exchange rate: 10 NOK/USD
