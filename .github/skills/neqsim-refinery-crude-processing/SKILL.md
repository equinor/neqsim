---
name: neqsim-refinery-crude-processing
description: "Assay characterization, crude-blend planning, crude-column reference cases, ASTM D86 and hydrotreating balance receipts (OilAssayCharacterisation, RefineryAssayBlend, RefineryLinearBlendOptimizer, RefineryHydrotreatingSulfurNitrogenBalance, Standard_ASTM_D86). USE WHEN: asked to characterize a crude assay, blend refinery feeds, screen sulfur or nitrogen removal, estimate hydrogen/utility/economics receipts or compare crude fractionation products."
last_verified: "2026-10-03"
---

# Refinery Crude Processing

## When to use this

Use this skill for NeqSim's existing crude-assay, petroleum-cut, refinery blend, crude-fractionation reference-case, product-boiling-range, and hydrotreating balance APIs. Treat the APIs as connected building blocks, not as one turnkey refinery simulator.

The useful data flow is:

`assay data -> OilAssayCharacterisation -> pseudo-component fluid -> blend plan/batch -> equilibrium column or hydrotreating balances -> operating/economics receipts`

Use `neqsim-distillation-design` for equilibrium-column setup and convergence details. Use `neqsim-eos-regression` when the job is to fit an EOS to measured PVT rather than convert an assay. The Doe Big Hill and Sarir classes are case-specific reference scenarios; reuse their construction pattern with a user-provided assay and operating basis instead of treating their constants as a general crude-unit design.

## Class map

| Class | Package | What it does | Verified API surface |
|---|---|---|---|
| `OilAssayCharacterisation` / nested `AssayCut` | `thermo.characterization` | Holds mass- or volume-yield cuts and applies them as petroleum pseudo-components. | `system.getOilAssayCharacterisation()`, `clearCuts()`, `addCut(AssayCut)`, `getCuts()`, `getResolvedMassFractions()`, `apply()`; `AssayCut(String)`, fluent `withMassFraction(double)`, `withWeightPercent(double)`, `withVolumeFraction(double)`, `withSpecificGravity(double)`, `withApiGravity(double)`, `withAverageBoilingPointCelsius(double)`, `withMolarMassKgPerMol(double)`, `withSulfurMassFraction(double)`, `withNitrogenMassFraction(double)`. |
| `DoeBigHillSweetAssay` | `thermo.characterization` | Supplies a named, fixed sweet-crude assay and a qualified vacuum-screening subset. | `create(SystemInterface)`, `create(SystemInterface, double)`, `createVacuumScreeningFeed(SystemInterface, double)`. |
| `AlDiwiniyaAtmosphericReference` | `thermo.characterization` | Stores published/reference atmospheric crude feed, products, TBP points, and measured reference properties. | `getTbpCumulativeVolumePercent()`, `getTbpTemperatureCelsius()`, `getProducts()`, `getCrudeFeedRateM3PerHour()`, `getCrudeApiGravityAt15C()`. |
| `RefineryAssayBlend` / `RefineryViscosityBlend` | `thermo.characterization` | Reconstructs bulk density/API/sulfur/nitrogen and viscosity blend properties without mutating source systems. | `fromAssays(OilAssayCharacterisation[], double[])`, `fromBulkProperties(double[], double[])` or the four-array quality overload; getters include `getMassFractions()`, `getSpecificGravity()`, `getApiGravity()`, `getSulfurMassFraction()`, `getNitrogenMassFraction()`. |
| `RefineryLinearBlendOptimizer` | `thermo.characterization` | Minimizes source cost subject to API, sulfur, nitrogen, and viscosity bounds. | `optimizeMinimumCost(double[] costs, double[] specificGravities, double[] sulfurFractions, double[] nitrogenFractions, double[] viscositiesCSt, double temperatureCelsius, double minimumApi, double maximumApi, double maximumSulfur, double maximumNitrogen, double minimumViscosityCSt, double maximumViscosityCSt)`; result getters include `getSourceMassFractions()`, `getAssayBlend()`, `getViscosityBlend()`, `getUnitCostPerMass()`, `getQualityConstraintReceipt()`. |
| `RefineryBinaryBlendEnvelope` | `thermo.characterization` | Evaluates feasible first-source fractions for two-source quality constraints. | `fromQualityConstraints(...)`, `getMinimumFirstSourceMassFraction()`, `getMaximumFirstSourceMassFraction()`, `evaluateAtFirstSourceMassFraction(...)`. |
| `RefineryBlendBatch`, `RefineryBlendOptimizationPlan`, `RefineryBlendSourceLedger` | `thermo.characterization` | Turns blend fractions into a mass/volume batch, links optimizer outputs to source identifiers, and preserves a source receipt ledger. | `fromMassFractions(double, double[], double[])`, batch `fromOptimization(double, RefineryLinearBlendOptimizer.Result, double[])`, plan `fromOptimization(String[], double, double[], double[], RefineryLinearBlendOptimizer.Result)`, and ledger `fromBatch(String[], RefineryBlendBatch)`; plan getters `getOptimization()`, `getBatch()`, `getSourceLedger()`, `getSourceCostReceipts()`. |
| `RefineryHydrotreatingSulfurNitrogenBalance` | `thermo.characterization` | Closes screening-level sulfur/nitrogen removal, hydrogen consumption, H2S/NH3 production, and product mass. | `calculate(double, double, double, double, double, double, double)` or `calculateForAssay(double, OilAssayCharacterisation, double, double, double, double)`; getters include `getFeedMassKg()`, `getSulfurRemovedMassKg()`, `getNitrogenRemovedMassKg()`, `getProductMassKg()`, `getTotalMassBalanceResidualKg()`. |
| `RefineryHydrotreating*Balance` family | `thermo.characterization` | Carries staged hydrogen supply/recycle, utility, fired-heater, emissions, thermal-duty, throughput, sulfur, and nitrogen balance calculations. | Read each immutable result object's `get...` API; examples include `RefineryHydrotreatingSulfurNitrogenThermalDutyBalance.calculate(...)` and `getSensibleHeatingDutyMegaWatt()`. |
| `RefineryHydrotreating*Receipt` family | `thermo.characterization` | Builds operating, product-distribution, net-intensity, and scenario-economics receipts from upstream balances. | `RefineryHydrotreatingSulfurNitrogenOperatingReceipt.calculate(emissionsBalance)`; `getFeedMassFlowKgPerHour()`, `getFreshHydrogenMassFlowKgPerHour()`, `getFreshHydrogenEnergyMWhPerTonneFeed()`, `getTotalScenarioCostPerHour()`. |
| `DoeBigHillVacuumFractionationCase`, `SarirAtmosphericFractionationCase` | `process.equipment.distillation` | Reproducible, data-bounded vacuum/atmospheric reference cases that assemble a `DistillationColumn`. | Doe `create(String, double, OperatingInputs)`; Sarir `create(String, double[], double[], OperatingInputs)` or `createFromHeatingCase(String, SarirAtmosphericCrudeHeatingCase, OperatingInputs)`; case getters include `getFeedStream()`, `getColumn()`, `getOperatingInputs()`, and Sarir `run(UUID)`. Inputs are case-specific. |
| `SarirAtmosphericPumparoundScreen`, `SarirAtmosphericSideStripperContactScreen` | `process.equipment.distillation` | Evaluates explicitly mapped Sarir pumparound settings and a bounded side-stripper contact screen. | Pumparound `configure(...)`; side-stripper class JavaDoc describes a single-contact screen, not a multistage column model. |
| `ProductBoilingPointDistribution` | `process.equipment.distillation` | Reports a discrete mole-based normal-boiling-point distribution from a product stream's pseudo-components. | `from(StreamInterface)`, `getBoilingPointTemperaturesKelvin()`, `getCumulativeMoleFractions()`, `getMeanNormalBoilingPointKelvin()`, `getNormalBoilingPointQuantileKelvin(...)`. |
| `Standard_ASTM_D86`, `RiaziDaubertDistillationConversion` | `standards.oilquality` | Calculates NeqSim's ASTM D86-style curve and exposes a published-point D86/TBP conversion. | `new Standard_ASTM_D86(SystemInterface)`, `calculate()`, `getDistillationCurve()`, `getTBPCurve()`, `getQualifiedD86Temperature(...)`; conversion methods `convertD86ToTbpC(...)`, `convertTbpToD86C(...)`. |
| `AsphalteneCharacterization`, `PedersenAsphalteneCharacterization`, `PedersenPlusModelSolver` | `thermo.characterization` | Provides SARA/pseudocomponent or Pedersen-style heavy-end/asphaltene characterization and stability screening. | `setSARAFractions(...)`, `setC7plusProperties(...)`, `evaluateStability()`; Pedersen API includes `characterize()`, `addAsphalteneToSystem(...)`, `calculateOnsetPressure(...)`. |
| `BioFeedstock` | `thermo.characterization` | Describes solids, elemental, lignocellulosic, digestion, and handling properties for biomass feedstocks; this is adjacent to refinery work, not a petroleum assay. | `library(String)`, `setElementalAnalysis(...)`, `setSolidsAnalysis(...)`, `validate()`, `toMap()`. |
| `CharacterizationOptions`, `CharacterizationValidationReport` | `thermo.characterization` | Configures supported characterization/recharacterization behavior and compares source/reference characterization results. | `CharacterizationOptions.builder()`, `getNamingScheme()`, `isNormalizeComposition()`, `CharacterizationValidationReport.generate(...)`, `isValid()`, `getWarnings()`, `getMassDifferencePercent()`. |

## Build pattern

This minimal path makes a two-cut assay, applies its pseudo-components to an EOS fluid, feeds a small equilibrium column, and independently computes the assay-based sulfur/nitrogen material balance. The balance is not a reacting hydrotreater model.

```java
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.characterization.OilAssayCharacterisation;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.characterization.RefineryHydrotreatingSulfurNitrogenBalance;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface crude = new SystemSrkEos(298.15, 1.01325);
crude.setMixingRule("classic");
OilAssayCharacterisation assay = crude.getOilAssayCharacterisation();
assay.clearCuts();
assay.addCut(new AssayCut("Light").withMassFraction(0.40).withSpecificGravity(0.75)
    .withAverageBoilingPointCelsius(200.0).withSulfurMassFraction(0.002).withNitrogenMassFraction(0.0005));
assay.addCut(new AssayCut("Heavy").withMassFraction(0.60).withSpecificGravity(0.90)
    .withAverageBoilingPointCelsius(400.0).withSulfurMassFraction(0.012).withNitrogenMassFraction(0.002));
assay.setTotalAssayMass(1.0);
assay.apply();

Stream feed = new Stream("crude assay feed", crude);
feed.setFlowRate(10000.0, "kg/hr");
feed.setTemperature(100.0, "C");
feed.setPressure(3.0, "bara");
feed.run();
feed.getThermoSystem().initProperties();
DistillationColumn column = new DistillationColumn("screening crude column", 8, true, true);
column.addFeedStream(feed, 4);
column.setTopPressure(1.5);
column.setBottomPressure(2.0);
column.getReboiler().setOutletTemperature(650.0);
column.setCondenserRefluxRatio(0.5);
column.run();

RefineryHydrotreatingSulfurNitrogenBalance balance =
    RefineryHydrotreatingSulfurNitrogenBalance.calculateForAssay(
        1000.0, assay, 0.0001, 0.00005, 2.0, 4.0);
double productMassKg = balance.getProductMassKg();
double massClosureKg = balance.getTotalMassBalanceResidualKg();
```

Equivalent Python/JVM access pattern:

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
OilAssayCharacterisation = jneqsim.thermo.characterization.OilAssayCharacterisation
AssayCut = OilAssayCharacterisation.AssayCut
DistillationColumn = jneqsim.process.equipment.distillation.DistillationColumn
Stream = jneqsim.process.equipment.stream.Stream
SulfurNitrogenBalance = jneqsim.thermo.characterization.RefineryHydrotreatingSulfurNitrogenBalance

crude = SystemSrkEos(298.15, 1.01325)
crude.setMixingRule("classic")
assay = crude.getOilAssayCharacterisation()
assay.clearCuts()
assay.addCut(AssayCut("Light").withMassFraction(0.40).withSpecificGravity(0.75)
             .withAverageBoilingPointCelsius(200.0).withSulfurMassFraction(0.002)
             .withNitrogenMassFraction(0.0005))
assay.addCut(AssayCut("Heavy").withMassFraction(0.60).withSpecificGravity(0.90)
             .withAverageBoilingPointCelsius(400.0).withSulfurMassFraction(0.012)
             .withNitrogenMassFraction(0.002))
assay.setTotalAssayMass(1.0)
assay.apply()

feed = Stream("crude assay feed", crude)
feed.setFlowRate(10000.0, "kg/hr")
feed.setTemperature(100.0, "C")
feed.setPressure(3.0, "bara")
feed.run()
feed.getThermoSystem().initProperties()
column = DistillationColumn("screening crude column", 8, True, True)
column.addFeedStream(feed, 4)
column.setTopPressure(1.5)
column.setBottomPressure(2.0)
column.getReboiler().setOutletTemperature(650.0)
column.setCondenserRefluxRatio(0.5)
column.run()

balance = SulfurNitrogenBalance.calculateForAssay(1000.0, assay, 0.0001, 0.00005, 2.0, 4.0)
product_mass_kg = balance.getProductMassKg()
mass_closure_kg = balance.getTotalMassBalanceResidualKg()
```

For feed data supplied as cumulative volume-yield / boiling-point boundaries, `addTBPCutBoundariesCelsius(...)` accepts boundaries in degC and per-cut specific gravities. It requires 0 and 100 vol% endpoints and matching array lengths. Prefer explicit assay yields and lab properties over the bundled reference assay when building a new case.

## Result extraction

| Result | API | Units / interpretation |
|---|---|---|
| Assay cut yields | `getResolvedMassFractions()` | Dimensionless mass fractions in `getCuts()` order; returned as a defensive copy. |
| Bulk assay properties | `getBulkSpecificGravity()`, `getBulkApiGravity()`, `getBulkDensityKgPerCubicMetreAt60F()`, `getBulkSulfurMassFraction()`, `getBulkNitrogenMassFraction()` | Specific gravity is dimensionless; API is degrees API; density is kg/m3 at 60 F; sulfur/nitrogen are mass fractions. |
| Applied assay amount | `getTotalAssayMass()` | kg used to generate component mole inventories; set with `setTotalAssayMass(double)`. |
| Blend properties | `RefineryAssayBlend` getters | Mass fractions, specific gravity, API gravity and sulfur/nitrogen mass fraction; sulfur/nitrogen getters are unavailable for bulk-property inputs that omitted those data. |
| Balance outputs | `RefineryHydrotreatingSulfurNitrogenBalance` getters | Batch mass kg, removed sulfur/nitrogen kg, produced H2S/NH3 kg, hydrogen consumed kg and mass-balance residual kg. |
| Operating receipt | `RefineryHydrotreatingSulfurNitrogenOperatingReceipt` getters | Feed/H2 kg/h; H2 energy MWh/t feed; emissions kg CO2e/h or kg CO2e/t feed; caller-priced scenario currency/h or currency/t. |
| D86 curve | `Standard_ASTM_D86.getDistillationCurve()` | Two columns: liquid volume percent and temperature in degC on the active reporting basis. `getDistillationCurveKelvin()` uses K. |
| Discrete product boiling points | `ProductBoilingPointDistribution` getters | Normal boiling points in K, cumulative mole fractions, and mean normal boiling point in K or degC. Not an ASTM curve. |

`Standard_ASTM_D86.calculate()` must run before reading its result. The qualified conversion `getQualifiedD86Temperature(...)` accepts only 0, 10, 30, 50, 70, 90, or 95 vol% and defaults to degC; it rejects unsupported points and temperatures outside its qualified reference domain.

## Gotchas

- `OilAssayCharacterisation.apply()` mutates the attached thermodynamic system by adding pseudo-components. Validate the cuts first, apply once, and do not reapply over existing generated names. The method rejects duplicates rather than silently doubling the assay.
- Assay cuts must use a consistent yield basis. Volume fractions are converted to mass fractions using cut specific gravities; a mixed or incomplete basis fails. `getBulkSpecificGravity()` uses ideal additive liquid volumes and does not model temperature correction or blend contraction.
- For petroleum cuts, provide a boiling point or explicit molecular mass as required by the cut; tests verify that missing average boiling-point data fails before components are applied. `withMolarMassKgPerMol` expects kg/mol. Temperatures are K unless a method explicitly says Celsius; process pressure conventions are bara.
- Always set the EOS mixing rule before running a flash/column. After a flash, call `initProperties()` before reading transport properties. Clone the characterized fluid before independent scenario modifications.
- Blend optimization is a linear, source-property blend screen. It is not a full thermodynamic mixture calculation, assay blending laboratory model, or refinery planning optimizer. It fails closed when no feasible blend satisfies the supplied bounds.
- The hydrotreating balance and its hydrogen/utility/emissions/economics receipts are algebraic scenario balances. They do not solve reactor kinetics, catalyst activity, equilibrium, reactor temperature profiles, or a complete H2 recycle process. State assumed stoichiometric H2-per-removed-heteroatom and caller-supplied prices/emission factors.
- `DoeBigHillSweetAssay`, `DoeBigHillVacuumFractionationCase`, `SarirAtmosphericFractionationCase`, and `AlDiwiniyaAtmosphericReference` contain case-specific data and evidence boundaries. They are examples/reference cases, not general-purpose crude-unit models. Sarir's side-stripper contact screen is not a multistage side stripper; generic column side draws/pumparounds are separate features.
- NeqSim provides `Standard_ASTM_D86` and a literature conversion from D86 to TBP. No `Standard_ASTM_D2887` class was found in `standards.oilquality`; do not label `ProductBoilingPointDistribution` or a TBP curve as ASTM D2887 or as a lab test.
- Characterization and balance APIs generally validate inputs with exceptions rather than returning an equipment `validateSetup()` report. Record source assay values, units, missing cuts, and data gaps; do not replace absent assay measurements with the built-in example constants.

## Validation / benchmarks

- Check that resolved assay mass fractions sum to 1.0 within the input precision and that applying the assay reconstructs the requested mass inventory. The source tests use tight mass closure around `1e-10` for their controlled examples.
- Reconstruct bulk API from specific gravity as `API = 141.5 / SG - 131.5`. Treat this and reciprocal-volume specific-gravity blending as the documented ideal blend screen, not as a non-ideal density model.
- Check total mass, sulfur, and nitrogen residuals from the corresponding balance getters; require finite non-negative hydrogen and products and achieved sulfur/nitrogen targets no greater than their feed fractions.
- For D86/TBP comparison, cite Riazi and Daubert, “Analytical correlations interconvert distillation-curve types” (Oil & Gas Journal, 1986). The implementation uses seven discrete recovery points and documented temperature ranges. It is a correlation/NeqSim prediction check, not an ASTM apparatus test or D2887 compliance claim.
- For an equilibrium crude tower, inspect convergence diagnostics, feed/product component balance, product yields, top/bottom pressure, and realistic cut ordering. Fenske–Underwood–Gilliland is an independent shortcut reasonableness check only where light/heavy keys and relative volatility are meaningful; it is not a rigorous crude assay validation.
- Compare named reference scenarios only against the same published/reference inputs and declared specification-vs-validation status. Preserve the data boundary: product rates that were imposed as specifications are not independent yield-validation evidence.

## Related skills

- `neqsim-distillation-design` for rigorous equilibrium-column setup, solver selection, convergence and sizing.
- `neqsim-api-patterns` for EOS creation, oil characterization, units, mixing rules and property initialization.
- `neqsim-process-modeling` for connecting the column into a process flowsheet.
- `neqsim-eos-regression` for measured-PVT regression and C7+ parameter fitting.
- `neqsim-benchmark-reference-data` for independent reference-data gates and provenance.
- `neqsim-professional-reporting` for assumptions, evidence, uncertainty and engineering-report structure.