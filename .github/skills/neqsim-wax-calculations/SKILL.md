---
name: neqsim-wax-calculations
version: "0.1.0"
description: "Use when: performing NeqSim wax calculations, wax appearance temperature (WAT), wax fraction versus temperature curves, wax precipitation, waxy-oil viscosity, wax model tuning, or wax inhibitor screening for flow assurance studies. Covers the verified Java and Python API setup for wax-forming oils with TBP and plus fractions."
last_verified: "2026-07-06"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# NeqSim Wax Calculations

Use this skill when a task needs a real NeqSim wax workflow rather than the simple wax-margin screening placeholder. Typical outputs are wax appearance temperature (WAT), wax weight fraction versus temperature, wax fraction along a pressure profile, waxy-oil viscosity sensitivity, and optional inhibitor dose-response screening.

## When to Use

- A user asks for wax appearance temperature, WAT, wax precipitation, or wax phase calculations.
- A flow-assurance study needs wax fraction as a function of temperature or pressure.
- A crude assay contains TBP or plus fractions that can be characterized into wax-forming pseudo-components.
- A wax inhibitor, pour-point depressant, or cold-restart discussion needs a NeqSim WAT basis.
- A task needs to compare an operating or pipeline temperature profile against wax onset.

For a quick margin calculation when WAT is already known, use `neqsim-wax-margin-check`. For broader pipeline and hydrate/wax/asphaltene context, also load `neqsim-flow-assurance`.

## Required Inputs

- Oil or condensate composition including wax-forming heavy fractions. Prefer measured TBP cuts and plus-fraction molar mass and density.
- Pressure in bara for the WAT or wax fraction calculation.
- Temperature range in Celsius for wax fraction curves.
- Wax model choice when specified: `Pedersen` default, `Won`, `Wilson`, or `Coutinho` via `setWaxModelType(String)` before adding the wax phase.
- Optional lab data: measured WAT, DSC wax fraction, pour point, yield stress, inhibitor dose-response, or viscosity versus shear rate.

## Wax Fluid Setup

Wax calculations need a characterized heavy-end oil and an explicit wax solid phase. The verified Java setup pattern is:

```java
SystemInterface oil = new SystemSrkEos(273.15 + 60.0, 50.0);
oil.addComponent("methane", 0.30);
oil.addComponent("ethane", 0.10);
oil.addTBPfraction("C10", 0.15, 134.0 / 1000.0, 0.78);
oil.addTBPfraction("C15", 0.15, 206.0 / 1000.0, 0.83);
oil.addTBPfraction("C19", 0.10, 270.0 / 1000.0, 0.814);
oil.addPlusFraction("C20", 0.20, 350.0 / 1000.0, 0.88);

oil.getCharacterization().characterisePlusFraction();
oil.getWaxModel().addTBPWax();
oil.createDatabase(true);
oil.setMixingRule(2);
oil.addSolidComplexPhase("wax");
oil.setMultiphaseWaxCheck(true);
oil.setMultiPhaseCheck(true);
oil.init(0);
oil.init(1);
```

Notes:

- Use plus-fraction names without `+`, for example `"C20"`, not `"C20+"`.
- Call `characterisePlusFraction()` before `getWaxModel().addTBPWax()`.
- Call `setWaxModelType("Won")`, `setWaxModelType("Wilson")`, or `setWaxModelType("Coutinho")` before `addSolidComplexPhase("wax")` if the default Pedersen model is not intended.
- Keep the heavy-end basis traceable. Wax predictions are very sensitive to molar mass, density, and n-paraffin distribution.

## Wax Appearance Temperature

Use `ThermodynamicOperations#calcWAT()` for a direct WAT. The operation changes the system temperature to the calculated wax appearance temperature.

```java
ThermodynamicOperations ops = new ThermodynamicOperations(oil);
ops.calcWAT();
double watC = oil.getTemperature() - 273.15;
```

Use this for a single pressure point when the fluid has already been configured for wax calculations.

## Wax Fraction Curve

Use `neqsim.pvtsimulation.flowassurance.WaxCurveCalculator` for a WAT and wax weight fraction curve with monotonicity enforcement.

```java
WaxCurveCalculator wax = new WaxCurveCalculator(oil);
wax.setPressure(50.0);
wax.setTemperatureRange(-10.0, 80.0, 2.0);
wax.calculate();

double watC = wax.getWaxAppearanceTemperatureC();
double[] temperaturesC = wax.getTemperaturesC();
double[] waxWeightFractions = wax.getWaxWeightFractions();
int failedFlashes = wax.getFailCount();
int corrections = wax.getMonotonicityCorrections();
```

Temperature values are stored from high to low temperature. Wax fraction should increase as temperature decreases. Record `failedFlashes` and `corrections` in the task results so reviewers can judge numerical quality.

## Pipeline Pressure Sensitivity

Use `calculateAtMultiplePressures(double[], double)` to evaluate wax fraction at one temperature across a pipeline pressure profile.

```java
double[] pressuresBara = new double[] {120.0, 90.0, 60.0, 30.0};
Map<Double, Double> waxByPressure = wax.calculateAtMultiplePressures(pressuresBara, 25.0);
```

For a full pipeline profile, combine this with the pipeline temperature profile from a NeqSim pipe model or measured operating data. Compare local temperature against WAT at local pressure, and report wax fraction at each profile point.

## PVT Wax Fraction Simulation

Use `WaxFractionSim` when the task follows the PVT simulation pattern or needs compatibility with existing PVT reports.

```java
WaxFractionSim waxSim = new WaxFractionSim(oil);
double[] temperaturesK = new double[] {333.15, 313.15, 293.15, 273.15};
double[] pressuresBara = new double[] {50.0, 50.0, 50.0, 50.0};
waxSim.setTemperaturesAndPressures(temperaturesK, pressuresBara);
waxSim.runCalc();
double[] waxFractions = waxSim.getWaxFraction();
```

The array returned by `getWaxFraction()` is the system weight fraction in the wax phase at each pressure and temperature point.

## Waxy-Oil Viscosity

Use `ViscosityWaxOilSim` when viscosity increase below WAT or shear-rate sensitivity matters. Configure the wax fluid as above, then set matching temperature, pressure, and shear-rate arrays. Treat results as model-sensitive and validate against lab viscosity data when available.

## Wax Inhibitor Screening

For inhibitor or pour-point-depressant screening, first calculate the untreated WAT using `WaxCurveCalculator` or `calcWAT()`. Then use `neqsim.process.chemistry.wax.WaxInhibitorPerformance` for a transparent dose-response estimate.

```java
WaxInhibitorPerformance inhibitor = new WaxInhibitorPerformance();
inhibitor.setBaseWaxAppearanceTemperatureC(watC);
inhibitor.setBasePourPointC(24.0);
inhibitor.setDoseMgL(200.0);
inhibitor.setMaxPourPointDepressionC(18.0);
inhibitor.setDoseAt50PctEfficacyMgL(150.0);
inhibitor.evaluate();

double inhibitedWatC = inhibitor.getInhibitedWaxAppearanceTemperatureC();
double efficacy = inhibitor.getEfficacyFraction();
List<String> warnings = inhibitor.getWarnings();
```

This inhibitor model is a screening relation. Use lab bottle tests, cold-finger data, and field-trial evidence for design decisions.

## Python Notebook Pattern

In repository task notebooks, use `devtools/neqsim_dev_setup.py` and direct Java classes through `ns` so the workspace classes are used.

```python
SystemSrkEos = ns.SystemSrkEos
ThermodynamicOperations = ns.ThermodynamicOperations
WaxCurveCalculator = ns.JClass("neqsim.pvtsimulation.flowassurance.WaxCurveCalculator")

oil = SystemSrkEos(273.15 + 60.0, 50.0)
oil.addComponent("methane", 0.30)
oil.addComponent("ethane", 0.10)
oil.addTBPfraction("C10", 0.15, 134.0 / 1000.0, 0.78)
oil.addTBPfraction("C15", 0.15, 206.0 / 1000.0, 0.83)
oil.addPlusFraction("C20", 0.20, 350.0 / 1000.0, 0.88)

oil.getCharacterization().characterisePlusFraction()
oil.getWaxModel().addTBPWax()
oil.createDatabase(True)
oil.setMixingRule(2)
oil.addSolidComplexPhase("wax")
oil.setMultiphaseWaxCheck(True)
oil.setMultiPhaseCheck(True)
oil.init(0)
oil.init(1)

wax = WaxCurveCalculator(oil)
wax.setPressure(50.0)
wax.setTemperatureRange(-10.0, 80.0, 2.0)
wax.calculate()

wat_c = wax.getWaxAppearanceTemperatureC()
temperatures_c = list(wax.getTemperaturesC())
wax_weight_fractions = list(wax.getWaxWeightFractions())
```

## Validation Checklist

- [ ] Heavy-end composition has measured or documented TBP and plus-fraction molar mass and density.
- [ ] Plus fraction was characterized before wax pseudo-components were added.
- [ ] A wax solid phase was added and `setMultiphaseWaxCheck(true)` was enabled.
- [ ] WAT was compared against at least one independent reference when possible: lab WAT, DSC data, pour point trend, or published example.
- [ ] Wax fraction curve has physically monotonic behavior after any documented corrections.
- [ ] Failed flash count and monotonicity corrections are recorded.
- [ ] Sensitivity to pressure, heavy-end characterization, and wax model type is discussed for design-grade work.
- [ ] Results are not used for pigging intervals, deposition growth, or restart design without deposition and rheology validation.

## Common Failure Modes

| Symptom | Likely Cause | Fix |
| --- | --- | --- |
| No wax phase appears | Wax phase not added or wax check disabled | Add `addSolidComplexPhase("wax")`, enable `setMultiphaseWaxCheck(true)`, and rerun |
| WAT is unrealistically low | Heavy-end characterization is too light or missing n-paraffins | Review TBP cuts, plus-fraction molar mass, density, and wax model parameters |
| WAT calculation fails | Search range or initial fluid state is poor | Use `WaxCurveCalculator` over a broad temperature range and inspect failed flashes |
| Wax fraction decreases when cooling | Numerical flash artifact | Use monotonicity-enforced `WaxCurveCalculator` output and record correction count |
| Python notebook sees old API | Installed neqsim package is stale | Use the devtools setup cell with workspace classes or rebuild/package the JAR |

## Reporting Requirements

For task reports, include:

- EOS and wax model type.
- Full heavy-end basis and units.
- WAT in Celsius at each pressure.
- Wax fraction curve table or figure with temperature in Celsius and wax weight fraction.
- Numerical quality indicators: failed flashes, monotonicity corrections, and sensitivity checks.
- Assumptions, validation source, and limits of use.

## Related NeqSim Classes

- `neqsim.thermodynamicoperations.ThermodynamicOperations#calcWAT()`
- `neqsim.pvtsimulation.flowassurance.WaxCurveCalculator`
- `neqsim.pvtsimulation.simulation.WaxFractionSim`
- `neqsim.pvtsimulation.simulation.ViscosityWaxOilSim`
- `neqsim.process.chemistry.wax.WaxInhibitorPerformance`
- `neqsim.thermo.characterization.WaxModelInterface`
- `neqsim.thermo.phase.PhaseWax`

## Related Skills

- `neqsim-flow-assurance`
- `neqsim-wax-margin-check`
- `neqsim-api-patterns`
- `neqsim-regression-baselines`
- `neqsim-professional-reporting`