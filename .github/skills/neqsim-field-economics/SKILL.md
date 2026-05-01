---
name: neqsim-field-economics
description: "Oil & gas field economics, NPV, IRR, cash flow, and fiscal regime modeling with NeqSim. USE WHEN: calculating project economics (NPV, IRR, payback), evaluating tax regimes (Norwegian NCS, UK, generic), building cost estimates (CAPEX/OPEX), or running Monte Carlo sensitivity analysis on economic outcomes."
last_verified: "2026-07-04"
---

# NeqSim Field Economics Skill

Reference for petroleum project economics — cost estimation, cash flow modeling,
tax regimes, NPV/IRR calculations, breakeven analysis, and uncertainty.

---

## Economics Workflow Overview

```
Reservoir Volumetrics → Production Profile → Revenue Forecast
                                                    ↓
CAPEX Estimate → Cash Flow Engine ← OPEX Estimate
                       ↓
              Tax Model (NCS/UK/Generic)
                       ↓
              NPV / IRR / Payback / Breakeven
                       ↓
              Monte Carlo Uncertainty (P10/P50/P90)
```

---

## Cost Estimation

### CAPEX Components (Typical Offshore NCS)

| Category | Typical Range | NeqSim Class |
|----------|--------------|--------------|
| Wells (drilling + completion) | 200-800 MNOK/well | `WellCostEstimator`, `WellMechanicalDesign` |
| SURF (subsea, umbilicals, risers, flowlines) | 500-5000 MNOK | `SURFCostEstimator` |
| Topsides / Processing | 2000-15000 MNOK | `FacilityBuilder` + `MechanicalDesign` |
| Subsea equipment (trees, manifolds) | 100-500 MNOK/well | `SubseaProductionSystem` |
| Pipeline / Export | 50-300 MNOK/km | `PipelineMechanicalDesign` |
| FPSO hull + mooring | 5000-20000 MNOK | Parametric estimate |
| Decommissioning (ABEX) | 10-30% of CAPEX | `DecommissioningEstimator` |

### NeqSim Cost Estimation Classes

```java
// Well cost
WellCostEstimator wellCost = new WellCostEstimator();
wellCost.setRegion(SubseaCostEstimator.Region.NORWAY);
wellCost.setWaterDepth(350.0);
wellCost.setTotalDepth(3800.0);
wellCost.setDrillingDays(45);
wellCost.setCompletionDays(25);
wellCost.setRigDayRate(540000.0);  // USD/day
double wellCapex = wellCost.estimate();

// SURF cost
SURFCostEstimator surfCost = new SURFCostEstimator();
surfCost.setRegion(SubseaCostEstimator.Region.NORWAY);
surfCost.setNumberOfWells(4);
surfCost.setFlowlineLength(25.0);      // km
surfCost.setUmbilicalLength(27.0);     // km
surfCost.setWaterDepth(350.0);
surfCost.setTreeType("vertical");
surfCost.setHasManifold(true);
double surfCapex = surfCost.estimate();

// Regional cost factors
RegionalCostFactors factors = new RegionalCostFactors("Norway");
double adjustedCost = baseCost * factors.getCostMultiplier();
```

### OPEX Components

| Category | Typical Range | Estimation Basis |
|----------|--------------|-----------------|
| Fixed OPEX | 3-6% of CAPEX/year | Insurance, manning, maintenance |
| Variable OPEX | 2-8 USD/boe | Chemicals, power, logistics |
| Well intervention | 50-200 MNOK/event | Frequency-based estimate |
| Pipeline inspection | 10-50 MNOK/year | IMR schedule |

---

## Cash Flow Engine

### Basic Usage

```java
CashFlowEngine engine = new CashFlowEngine("NO");  // Norwegian tax regime
engine.setCapex(5000.0, 2025);       // MUSD, year
engine.setOpexPercentOfCapex(0.04);  // 4% of CAPEX/year
engine.setOilPrice(70.0);           // USD/bbl
engine.setGasPrice(0.30);           // USD/Sm3

// Add production year by year
for (int year = 2027; year <= 2045; year++) {
    engine.addAnnualProduction(year, oilSm3[year], gasSm3[year], waterSm3[year]);
}

CashFlowResult result = engine.calculate(0.08);  // 8% discount rate

double npv = result.getNpv();           // MUSD
double irr = result.getIrr();           // fraction (e.g., 0.15 = 15%)
double payback = result.getPaybackYears();
double pi = result.getProfitabilityIndex();
```

### DCF Calculator (Low-Level)

```java
DCFCalculator dcf = new DCFCalculator();
dcf.setDiscountRate(0.08);
double[] cashFlows = {-500, -300, 100, 200, 300, 250, 200, 150, 100};
double npv = dcf.calculateNPV(cashFlows);
double irr = dcf.calculateIRR(cashFlows);
```

---

## Tax Models

### Norwegian Continental Shelf (NCS)

The Norwegian petroleum tax regime has three key components:

| Component | Rate | Base |
|-----------|------|------|
| Corporate tax | 22% | Revenue - OPEX - Depreciation |
| Special petroleum tax | 56% | Revenue - OPEX - Depreciation - Uplift |
| **Total marginal rate** | **78%** | — |

Additional features:
- **Uplift**: 5.5% of investment per year for 4 years (deductible only against special tax)
- **Depreciation**: 6-year straight-line for offshore investments
- **Loss carry-forward**: Losses can be carried forward indefinitely (with interest)
- **Exploration refund**: 78% of exploration costs refunded

```java
NorwegianTaxModel taxModel = new NorwegianTaxModel();
// Automatically applied when CashFlowEngine("NO") is used

// Direct tax calculation
TaxResult tax = taxModel.calculateTax(
    grossRevenue,    // NOK
    opex,            // NOK
    depreciation,    // NOK
    uplift           // NOK
);
double corporateTax = tax.getCorporateTax();
double specialTax = tax.getSpecialTax();
double totalTax = tax.getTotalTax();
double effectiveRate = tax.getEffectiveRate();
```

### UK Continental Shelf (UKCS)

| Component | Rate | Notes |
|-----------|------|-------|
| Ring Fence Corporation Tax (RFCT) | 30% | Ring-fenced profits |
| Supplementary Charge (SC) | 10% | On ring-fenced profits |
| **Total marginal rate** | **40%** | Investment allowance applies |

### Generic Tax Model

```java
GenericTaxModel generic = new GenericTaxModel();
generic.setCorporateTaxRate(0.25);
generic.setRoyaltyRate(0.10);
generic.setDepreciationYears(10);
```

### Tax Model Registry

```java
// List available models
TaxModelRegistry.getAvailableModels();  // ["NO", "UK", "BR", "GENERIC"]

// Get model by country code
TaxModel model = TaxModelRegistry.getModel("NO");
```

---

## Production Profile Generator

```java
ProductionProfileGenerator gen = new ProductionProfileGenerator();
gen.setResourceVolume(100.0);    // MMboe
gen.setRecoveryFactor(0.55);
gen.setPeakRate(25000.0);        // boe/d
gen.setBuildUpYears(2);
gen.setPlateauYears(5);
gen.setDeclineType("exponential");
gen.setDeclineRate(0.12);        // 12%/year
gen.setProjectLife(25);          // years

double[] profile = gen.generate();  // Annual production (boe)
```

---

## Sensitivity & Uncertainty Analysis

### Tornado Diagram (One-at-a-Time)

```java
SensitivityAnalyzer sensitivity = new SensitivityAnalyzer(engine);
sensitivity.addParameter("oilPrice", 50.0, 70.0, 90.0);     // low, base, high
sensitivity.addParameter("capex", 4000.0, 5000.0, 7000.0);
sensitivity.addParameter("recoveryFactor", 0.45, 0.55, 0.65);
sensitivity.addParameter("opexRate", 0.03, 0.04, 0.06);

Map<String, double[]> tornado = sensitivity.runTornado();
// Returns: {"oilPrice": [npv_low, npv_base, npv_high], ...}
```

### Monte Carlo

```java
MonteCarloRunner mc = new MonteCarloRunner(engine);
mc.addTriangularInput("oilPrice", 50.0, 70.0, 90.0);
mc.addTriangularInput("capex", 4000.0, 5000.0, 7000.0);
mc.addTriangularInput("recoveryFactor", 0.45, 0.55, 0.65);

MonteCarloResult result = mc.run(1000);
double p10 = result.getPercentile(10);
double p50 = result.getPercentile(50);
double p90 = result.getPercentile(90);
double probNegative = result.getProbabilityBelow(0.0);
```

---

## Breakeven Analysis

```java
// Breakeven oil price (NPV = 0)
double breakevenPrice = engine.calculateBreakevenPrice(0.0);

// Breakeven at different discount rates
for (double rate : new double[]{0.05, 0.08, 0.10, 0.12}) {
    double be = engine.calculateBreakevenPrice(rate);
    System.out.println("Breakeven at " + (rate*100) + "%: " + be + " USD/bbl");
}
```

---

## Decommissioning Cost Estimation

```java
DecommissioningEstimator decom = new DecommissioningEstimator();
decom.setNumberOfWells(6);
decom.setWellAbandonment(true);
decom.setSubseaRemoval(true);
decom.setPlatformRemoval(false);    // tieback — no platform
decom.setPipelineDecommissioning(true);
decom.setWaterDepth(350.0);
decom.setRegion("Norway");

double decomCost = decom.estimate();  // MUSD
```

---

## Common Economic Pitfalls

| Pitfall | Impact | Prevention |
|---------|--------|------------|
| Double-counting depreciation in tax bases | Overstates tax, understates NPV | Norwegian model: depreciation deducted from BOTH bases independently |
| Wrong CAPEX timing | Wrong NPV (time value) | CAPEX in year 0, first production year 2-3, match reality |
| Ignoring loss carry-forward | Understates early-year cash flow | Norwegian model carries losses with interest adjustment |
| Using nominal discount rate with real cash flows | Wrong NPV | Be consistent: real-real or nominal-nominal |
| Ignoring decommissioning | Missing 10-30% lifecycle cost | Always include ABEX in project economics |
| Oil price in wrong currency | Wrong revenue | NOK on NCS, USD internationally; use consistent FX |

---

## Key Conversion Factors

| From | To | Factor |
|------|-----|--------|
| 1 Sm3 oil | boe | 1.0 (by definition) |
| 1 Sm3 gas | boe | ~0.001 (varies, typically 1000 Sm3 gas = 1 boe) |
| 1 bbl oil | Sm3 oil | 0.159 |
| 1 Sm3 oil | bbl | 6.29 |
| 1 tonne oil | bbl | ~7.33 (depends on API gravity) |
| 1 BCF gas | Sm3 | 28.3 × 10⁶ |
