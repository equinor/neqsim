---
name: neqsim-field-economics
description: "Oil & gas field economics, NPV, IRR, cash flow, and fiscal regime modeling with NeqSim. USE WHEN: calculating project economics (NPV, IRR, payback), evaluating tax regimes (Norwegian NCS, UK, generic), building cost estimates (CAPEX/OPEX), or running Monte Carlo sensitivity analysis on economic outcomes."
last_verified: "2026-07-19"
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

### Direct Lifecycle Integration

`FieldLifecycleSimulator` aggregates live NeqSim reservoir/process results by calendar year and feeds oil and export
gas volumes directly to `CashFlowEngine("NO")`. `FieldLifecycleResult` exposes after-tax NPV, IRR, payback and
break-even oil/gas prices together with the technical production, injection, energy and emissions profiles. Use this
route when economics must reflect process capacity and reservoir response rather than an independent decline curve.
For a host tieback, only the new field's attributed oil and gas enter project revenue; existing-host production is a
capacity load, not project revenue. Configure tieback tariff/OPEX and modification CAPEX explicitly. The result's
holdback, capacity-deferred oil, peak operating/requested utilization and annual bottleneck fields explain the economic
difference between standalone greenfield, direct tieback through existing SURF, managed allocation and debottleneck
cases. A user-supplied `ProcessModel` keeps the economic result coupled to all SURF and host process areas.
For area studies, use `AreaDevelopmentEvaluator` to compare multiple producing hosts and greenfield routes on the same
economic basis. A `REJECT_OPTION` product-quality policy makes an off-spec route ineligible before NPV ranking;
`REPORT_ONLY` preserves the route for sensitivity and debottleneck diagnosis.

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

**Regime gotcha (verified in task 2026-10-09 Havis screening):** the registry `"NO"` model is the
pre-2022 uplift regime (6-year depreciation, uplift, loss carry-forward). Projects approved under
the current 2022 cash-flow tax (78 % on net cash flow, immediate expensing, immediate loss refund,
so after-tax cash flow = 22 % of pre-tax) must use
`new GenericTaxModel(FiscalParameters.norwegianCashFlowTax2022())` (`lossRefund(true)`, IMMEDIATE
depreciation). `CashFlowEngine` discounts at year end; state the convention (mid-year raises NPV by
about half a year of discounting) or discount outside the engine.

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

## Exploration commitment and relinquishment (EMV, break-even Pg)

A licence decision (keep and drill, or relinquish) is an option valuation, not a project NPV. With the NCS cash-flow tax (tau = 0.78, dry well refunded when a tax position exists):

```text
EMV = Pg * V_dev - (1 - tau) * C_expl          (V_dev = expected post-tax development value if discovered, > 0 only)
Pg* = (1 - tau) C_expl / (V_dev + (1 - tau) C_expl)   (break-even chance of success)
```

- Compute `V_dev` as max(best concept NPV, 0) per Monte Carlo sample so a sub-commercial discovery is not counted; report P(commercial | discovery) separately.
- A rig-of-opportunity well completed as a producer gets a development credit (30-80 % of the well cost) in the success branch and has no mobilisation cost.
- Chance of success: Beta posterior from the area wildcat record (discoveries + 1, dry + 1) times a prospect-quality factor; it is an area statistic, not a prospect Pg, and the report must say so.
- Typical result: break-even Pg of 5-6 % because the dry-well cost after tax is about 22 % of the gross cost. State the refund assumption (partner tax positions) and the break-even discovery size at the used Pg.
- Report the Equinor-share EMV (equity from the licence record) next to the 100 % value.
- Compare several routes on the same Monte Carlo draws (standalone well then tie-back, well from a host-template slot, pre-investment in a host slot, direct route to the main host) and put the retention cost in every non-zero strategy; when the best routes are within about 5 % of one another the retention decision does not depend on the route, and the weak route (usually the direct one) is the only one with a negative tail.
- Cross-check the lognormal Monte Carlo EMV with the Swanson 30/40/30 weights on P90/P50/P10 (`enterprise_prospect_risking.risked_metrics`): a 10-15 % gap (Swanson higher for a right-skewed prize) is normal and should be stated, not tuned away.
- Value of information of a G&G study with `voi_imperfect` (states: good/poor prospect, Pg +/- 0.15) is zero when the go decision survives the poor signal; say so and justify the study by sizing and phasing, not by the retention decision.
- Also read the fixed-cost side of the gate: licence work obligations and area fees can dominate a 15-60 MNOK retention assumption; they are a data gap, not a model input to invent.
- **Host life is a scenario parameter, not a constant.** When a host has a hull or licence limit inside the tie-back window (e.g. first oil 2032-33 against a 2030 limit), sample the host end year (or run 2030/2038/2045 cases on shared draws) and report EMV per case; it flipped the sign of the exploration case in the Trestakk screening and out-ranked every cost item in the tornado.
- **Choose the concept after the discovery.** Per Monte Carlo sample take `V_dev = max(0, NPV of each concept)` (satellite / template / hub), so the EMV is the option value of design-to-cost; report how often each concept wins and the share of discoveries that support none. A shared hub wins only in the both-succeed branch; evaluate it there.
- **Value of information = EMV(explore first) - EMV(commit blind)** on the same draws, with the blind case valued as a loss of the committed capex when the prospect fails.
- **Sanity-check unit costs in NOK/km before running.** A tie-back flowline plus service line/umbilical at 220 MNOK/km (about 21 MUSD/km) made every prospect uneconomic; 70/110/180 MNOK/km all-in is a defensible Class 5 range. Print capex per concept next to oil volume and compare with area analogues first.
- **Reconcile the brief's own KPIs before recomputing them.** Pc/Pg is P(commercial | discovery); invert the lognormal fitted to P90/P10 for the implied minimum commercial volume (`v_min = median * exp(sigma * z(1 - Pc/Pg))`) and compare the truncated mean with the quoted "mean commercial volume" (MG: 14.4 MMboe and 41.9 against 42.6, so that figure is a conditional, not an unconditional, mean). A quoted EV far below your EMV is usually the exploration cost carried gross (no 78 % refund) or an equity share: compute both variants and the implied share, and tabulate the EBE definitions (EMV = 0 with/without refund, success-case with/without refund) rather than asserting which one the brief used.
- Related skills: `neqsim-psc-bid-economics-screening` (community, bid-round and PSC fiscal screening), `enterprise-host-ullage-allocation` (host ullage by constraint for tie-in value) and `neqsim-capacity-increase-screening` (plan-consistent capacity and displaced barrels).

**Appraisal-first versus go-now strategies and design-to-cost (Ragnfrid Sor VPbo pattern).**

- Compare strategies on the same Monte Carlo draws: S0 hold, S1 go now plus a separate exploration well, S2 appraise the discovery only (delay 1-3 years, information quality 0.5-0.9, pressure class revealed), S3 dual-target well (exploration plus appraisal leg), then decide. Payoff of S1 is `go_now + exploration EMV`; take care that every strategy carries its well cost and delay.
- VOI of an appraisal well is usually negative when the base case is already positive and the avoided mistake is cheap (a HIPPS of 0.6 bn NOK); the dual-target well can still win because the exploration well is worth drilling by itself. Report VOI against the pressure-class prior (0.2-0.7) to show it does not flip.
- Design-to-cost: scale all capex items (0.9/0.8/0.7) inside the Monte Carlo and report P(NPV<0), break-even volume and the capex that gives P(NPV<0) < 10 %; list levers (pressure class, one-well-first, line unit cost, well days, rig synergy, add-on volume) with their post-tax value, not just capex.
- Post-tax with immediate expensing and loss refund is `0.22 x` the pre-tax cash flow; state that this is an assumption (partner tax positions) and show pre-tax next to it, because a delay or cost effect shrinks to 22 % post-tax and can look unimportant (one year of delay was 100 MNOK pre-tax and 21 MNOK post-tax).

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
| `runFieldEconomics`/`FiscalRegime` `NO` is the pre-2022 uplift model | A post-2022 NCS project (22 % ordinary, 56 % special tax on cash flow, immediate expensing, refunded losses) gave NPV 0.09 vs 6.3 MUSD in a cross-check (NIP-1, BRP South Breidablikk) | For post-2022 NCS incremental projects build the annual cash flow yourself (7 % real, 6-year straight-line ordinary depreciation, special tax on cash flow) and use NeqSim only as a cross-check; state the regime used |

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
