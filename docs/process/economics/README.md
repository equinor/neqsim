---
title: Field Development Economics Module
description: The NeqSim field development economics module provides comprehensive tools for economic analysis
---

# Field Development Economics Module

The NeqSim field development economics module provides comprehensive tools for economic analysis
of oil and gas field developments. This includes cash flow modeling, tax calculations for multiple
jurisdictions, production forecasting, and uncertainty analysis.

## Table of Contents

- [Overview](#overview)
- [Tax Models](#tax-models)
- [Cash Flow Engine](#cash-flow-engine)
- [Production Profile Generator](#production-profile-generator)
- [Sensitivity Analysis](#sensitivity-analysis)
- [Regional Cost Factors](#regional-cost-factors)
- [Integration Examples](#integration-examples)

## Overview

The economics module is located in `neqsim.process.fielddevelopment.economics` and provides:

| Class | Purpose |
|-------|---------|
| `CashFlowEngine` | Full-lifecycle cash flow projections with NPV, IRR, payback |
| `TaxModel` | Interface for country-specific tax calculations |
| `GenericTaxModel` | Parameter-driven tax model for any fiscal regime |
| `TaxModelRegistry` | Database of 16+ country tax parameters |
| `FiscalParameters` | Data class for fiscal regime configuration |
| `ProductionProfileGenerator` | Arps decline curve production forecasts |
| `SensitivityAnalyzer` | Monte Carlo and tornado chart analysis |
| `NorwegianTaxModel` | Legacy Norwegian petroleum tax model |

## Tax Models

### Country-Independent Tax Framework

The module supports any country's fiscal regime through the `TaxModel` interface:

```java
// Get tax model for any registered country
TaxModel norwayModel = TaxModelRegistry.createModel("NO");
TaxModel brazilModel = TaxModelRegistry.createModel("BR-PSA");
TaxModel ukModel = TaxModelRegistry.createModel("UK");

// Calculate tax for a year
TaxModel.TaxResult result = model.calculateTax(
    500.0,    // gross revenue (MUSD)
    100.0,    // OPEX (MUSD)
    80.0,     // depreciation (MUSD)
    44.0      // uplift (MUSD)
);

System.out.println("Total tax: " + result.getTotalTax());
System.out.println("After-tax income: " + result.getAfterTaxIncome());
System.out.println("Effective rate: " + result.getEffectiveTaxRate() * 100 + "%");
```

### Registered Countries

The following countries are available in `TaxModelRegistry`:

| Code | Country | Fiscal System | Marginal Rate |
|------|---------|---------------|---------------|
| NO | Norway | Concessionary | 78% (22% corp + 56% resource) |
| UK | United Kingdom | Concessionary | 40% (30% corp + 10% resource) |
| US-GOM | Gulf of Mexico | Concessionary + Royalty | 21% + 18.75% royalty |
| BR | Brazil (Concession) | Concessionary + Royalty | 34% + 10% royalty |
| BR-PSA | Brazil Pre-Salt | Production Sharing | 34% + profit share |
| BR-DW | Brazil Deep Water | Special Participation | 34% + special tax |
| AO | Angola | PSC | 30% + 60% profit share |
| NG | Nigeria | PSC | 30% + 51% profit share |
| AU | Australia | PRRT | 70% (30% corp + 40% PRRT) |
| MY | Malaysia | PSC | 24% + 70% profit share |
| ID | Indonesia | Cost Recovery PSC | 35% |
| AE | UAE | Concessionary | 55% |
| CA-AB | Canada - Alberta | Concessionary + Royalty | 27% |
| GY | Guyana | PSC | 25% |
| EG | Egypt | PSC | 40.5% |
| KZ | Kazakhstan | Concessionary | 37.5% |

### Custom Tax Parameters

Create custom fiscal regimes using `FiscalParameters.Builder`:

```java
FiscalParameters custom = FiscalParameters.builder("MY-CUSTOM")
    .countryName("Malaysia Custom Block")
    .fiscalSystemType(FiscalSystemType.PSC)
    .corporateTaxRate(0.24)
    .costRecoveryLimit(0.70)
    .profitSharing(0.65, 0.35)  // 65% government, 35% contractor
    .depreciation(DepreciationMethod.DECLINING_BALANCE, 5)
    .build();

TaxModel customModel = new GenericTaxModel(custom);
TaxModelRegistry.register(custom);  // Optional: add to registry
```

### Fiscal Parameters JSON Database

Parameters are loaded from `data/fiscal/fiscal_parameters.json`. To add new countries:

```json
{
  "countryCode": "XX",
  "countryName": "New Country",
  "description": "Description of fiscal regime",
  "fiscalSystemType": "CONCESSIONARY",
  "corporateTaxRate": 0.25,
  "resourceTaxRate": 0.10,
  "royaltyRate": 0.05,
  "depreciationMethod": "STRAIGHT_LINE",
  "depreciationYears": 6
}
```

## Cash Flow Engine

### Basic Usage

```java
// Create engine for a specific country
CashFlowEngine engine = new CashFlowEngine("BR");  // Brazil

// Set project parameters
engine.setCapex(800, 2025);           // 800 MUSD in 2025
engine.addCapex(200, 2026);           // Additional 200 MUSD in 2026
engine.setOpexPercentOfCapex(0.04);   // 4% of CAPEX per year

// Set commodity prices
engine.setOilPrice(75.0);             // USD/bbl
engine.setGasPrice(0.25);             // USD/Sm3
engine.setGasTariff(0.02);            // USD/Sm3 transport

// Add production profile
engine.addAnnualProduction(2027, 0, 5.0e6, 0);   // 5 MSm3 gas
engine.addAnnualProduction(2028, 0, 10.0e6, 0);  // 10 MSm3 gas
// ... more years

// Calculate
CashFlowResult result = engine.calculate(0.08);  // 8% discount rate

// Results
System.out.println("NPV: " + result.getNpv() + " MUSD");
System.out.println("IRR: " + result.getIrr() * 100 + "%");
System.out.println("Payback: " + result.getPaybackYears() + " years");
System.out.println(result.toMarkdownTable());
```

### Switching Tax Models

```java
CashFlowEngine engine = new CashFlowEngine();

// Use any registered country
engine.setTaxModel("UK");           // By country code
engine.setTaxModel("BR-PSA");       // Brazil Pre-Salt

// Or use custom model
TaxModel custom = new GenericTaxModel(customParams);
engine.setTaxModel(custom);

// Check current model
System.out.println("Country: " + engine.getCountryName());
System.out.println("Marginal rate: " + engine.getTaxModel().getTotalMarginalTaxRate() * 100 + "%");
```

### Breakeven Analysis

```java
double breakevenOil = engine.calculateBreakevenOilPrice(0.08);
double breakevenGas = engine.calculateBreakevenGasPrice(0.08);

System.out.println("Breakeven oil price: " + breakevenOil + " USD/bbl");
System.out.println("Breakeven gas price: " + breakevenGas + " USD/Sm3");
```

## Production Profile Generator

Generate decline curve forecasts using Arps equations:

### Exponential Decline

```java
ProductionProfileGenerator generator = new ProductionProfileGenerator();

// Gas well with 15% annual decline
Map<Integer, Double> gasProfile = generator.generateExponentialDecline(
    10.0e6,    // Initial rate: 10 MSm3/d
    0.15,      // 15% annual decline
    2026,      // Start year
    20,        // 20 years maximum
    0.5e6      // Economic limit: 0.5 MSm3/d
);
```

### Hyperbolic Decline

```java
// Oil well with b-factor = 0.5
Map<Integer, Double> oilProfile = generator.generateHyperbolicDecline(
    15000,     // Initial rate: 15,000 bbl/d
    0.20,      // 20% initial decline
    0.5,       // b-factor (0 < b < 1)
    2026,      // Start year
    25,        // 25 years
    100        // Economic limit: 100 bbl/d
);
```

### Full Profile with Ramp-up

```java
// Realistic profile: 2-year ramp, 3-year plateau, exponential decline
Map<Integer, Double> profile = generator.generateFullProfile(
    20000,                        // Peak rate: 20,000 bbl/d
    2,                            // 2 years ramp-up
    3,                            // 3 years plateau
    0.12,                         // 12% decline rate
    DeclineType.EXPONENTIAL,
    2026,                         // Start year
    25                            // Total years
);

// Get summary
System.out.println(ProductionProfileGenerator.getProfileSummary(profile));
```

### Combining Profiles

```java
// Multiple wells or phases
Map<Integer, Double> phase1 = generator.generateExponentialDecline(...);
Map<Integer, Double> phase2 = ProductionProfileGenerator.shiftProfile(
    generator.generateExponentialDecline(...), 
    3  // Start 3 years later
);

Map<Integer, Double> combined = ProductionProfileGenerator.combineProfiles(phase1, phase2);
```

## Sensitivity Analysis

### Tornado Analysis

```java
SensitivityAnalyzer analyzer = new SensitivityAnalyzer(engine, 0.08);

// Vary each parameter by ±20%
TornadoResult tornado = analyzer.tornadoAnalysis(0.20);

// Output as markdown table
System.out.println(tornado.toMarkdownTable());

// Get most sensitive parameter
TornadoItem mostSensitive = tornado.getMostSensitiveParameter();
System.out.println("Most sensitive: " + mostSensitive.getParameterName());
System.out.println("NPV swing: " + mostSensitive.getSwing() + " MUSD");
```

### Monte Carlo Simulation

```java
SensitivityAnalyzer analyzer = new SensitivityAnalyzer(engine, 0.08);

// Set parameter distributions
analyzer.setOilPriceDistribution(60.0, 90.0);   // Uniform: $60-90/bbl
analyzer.setGasPriceDistribution(0.20, 0.35);   // Uniform: $0.20-0.35/Sm3
analyzer.setCapexDistribution(700, 900);        // Uniform: 700-900 MUSD
analyzer.setOpexFactorDistribution(0.8, 1.2);   // ±20% OPEX

// Set seed for reproducibility
analyzer.setRandomSeed(42);

// Run simulation
MonteCarloResult mc = analyzer.monteCarloAnalysis(10000);

// Results
System.out.println("NPV P10: " + mc.getNpvP10() + " MUSD");
System.out.println("NPV P50: " + mc.getNpvP50() + " MUSD");
System.out.println("NPV P90: " + mc.getNpvP90() + " MUSD");
System.out.println("P(NPV > 0): " + mc.getProbabilityPositiveNpv() * 100 + "%");
```

### Scenario Analysis

```java
ScenarioResult scenarios = analyzer.scenarioAnalysis(
    55.0,   // Low oil price
    95.0,   // High oil price
    0.18,   // Low gas price
    0.35,   // High gas price
    0.20    // 20% CAPEX contingency for low case
);

System.out.println(scenarios);
```

## Regional Cost Factors

Adjust NCS-baseline costs for different regions:

### Using Regional Factors

```java
// Create estimator for specific region
EconomicsEstimator estimator = new EconomicsEstimator("BR");

// Or set region after construction
EconomicsEstimator estimator = new EconomicsEstimator();
estimator.setRegion("US-GOM");

// Get estimate (automatically adjusted)
EconomicsReport report = estimator.estimate(concept, facility);
System.out.println("Region: " + estimator.getRegionName());
System.out.println("CAPEX: " + report.getTotalCapexMUSD() + " MUSD");
```

### Available Regions

```java
// List all regions
System.out.println(RegionalCostFactors.getSummaryTable());

// Get specific region factors
RegionalCostFactors brazil = RegionalCostFactors.forRegion("BR");
System.out.println("Brazil CAPEX factor: " + brazil.getCapexFactor());
System.out.println("Brazil well factor: " + brazil.getWellCostFactor());
```

| Code | Region | CAPEX | OPEX | Wells |
|------|--------|-------|------|-------|
| NO | Norwegian Continental Shelf | 1.00 | 1.00 | 1.00 |
| UK | UK Continental Shelf | 0.95 | 0.90 | 0.90 |
| US-GOM | Gulf of Mexico | 0.85 | 0.80 | 0.75 |
| US-PERMIAN | Permian Basin | 0.60 | 0.55 | 0.50 |
| BR | Brazil Offshore | 1.10 | 1.05 | 1.15 |
| BR-PS | Brazil Pre-Salt | 1.20 | 1.10 | 1.25 |
| MY | Malaysia | 0.70 | 0.65 | 0.70 |
| AU | Australia Offshore | 1.15 | 1.10 | 1.10 |

### Custom Regions

```java
RegionalCostFactors custom = new RegionalCostFactors(
    "CUSTOM",           // Code
    "Custom Region",    // Name
    0.90,              // CAPEX factor
    0.85,              // OPEX factor
    0.95,              // Well cost factor
    0.80,              // Labor factor
    "Custom notes"
);

RegionalCostFactors.register(custom);
```

## Integration Examples

### Complete Field Economics Workflow

```java
// 1. Define production profile
ProductionProfileGenerator gen = new ProductionProfileGenerator();
Map<Integer, Double> gasProfile = gen.generateFullProfile(
    15.0e6,                       // 15 MSm3/d peak
    1,                            // 1 year ramp
    4,                            // 4 years plateau
    0.10,                         // 10% decline
    DeclineType.EXPONENTIAL,
    2027,                         // Start
    20                            // Total years
);

// 2. Configure cash flow engine
CashFlowEngine engine = new CashFlowEngine("NO");
engine.setCapex(1200, 2025);
engine.setCapex(400, 2026);
engine.setOilPrice(75.0);
engine.setGasPrice(0.28);
engine.setProductionProfile(null, gasProfile, null);

// 3. Calculate base case
CashFlowResult result = engine.calculate(0.08);
System.out.println(result.getSummary());

// 4. Sensitivity analysis
SensitivityAnalyzer analyzer = new SensitivityAnalyzer(engine, 0.08);
analyzer.setGasPriceDistribution(0.20, 0.40);
analyzer.setCapexDistribution(1400, 1800);
MonteCarloResult mc = analyzer.monteCarloAnalysis(5000);
System.out.println(mc);

// 5. Compare regions
for (String region : Arrays.asList("NO", "UK", "BR", "AU")) {
    engine.setTaxModel(region);
    double npv = engine.calculateNPV(0.08);
    System.out.printf("%s: NPV = %.1f MUSD%n", region, npv);
}
```

### Screening Multiple Concepts

```java
List<FieldConcept> concepts = loadConcepts();
EconomicsEstimator estimator = new EconomicsEstimator("US-GOM");

for (FieldConcept concept : concepts) {
    EconomicsReport report = estimator.quickEstimate(concept);
    System.out.printf("%s: CAPEX=%.0f MUSD, $/boe=%.1f%n",
        concept.getName(),
        report.getTotalCapexMUSD(),
        report.getCapexPerBoeUSD());
}
```

## See Also

- [Field Development Framework](../../fielddevelopment/README)

## References

- Arps, J.J. (1945). "Analysis of Decline Curves". SPE.
- Norwegian Petroleum Tax Act (Petroleumsskatteloven)
- Brazilian ANP Petroleum Law
- UK HMRC Oil Taxation Manual
