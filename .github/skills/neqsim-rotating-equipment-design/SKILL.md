---
name: neqsim-rotating-equipment-design
description: "NeqSim rotating-equipment studies. USE WHEN: dry gas seal or seal gas conditioning troubleshooting, regulator JT condensation, primary vent leakage, buffer/separation gas, API 692/ISO 6368 scope, startup and standby margins, compressor vendor maps/trains, turboexpanders, pump NPSH/API 610 or mechanical screening. APIs: DryGasSealAnalyzer, SealGasSupplyConditioning, DryGasSealMonitor, LabyrinthSealFlowCalculator, CompressorThermalModel."
last_verified: "2026-11-27"
---

# Rotating Equipment Design

## When to use this

Use this skill when an agent needs to create, correct, apply or interpret compressor, turboexpander or pump performance maps, operating envelopes, operating profiles, or first-pass mechanical checks. Load `neqsim-compressor-antisurge-recycle` for recycle-control topology and anti-surge dynamics. This skill covers machine performance/design API and data validity, not a vendor-certified selection.

## Class map

| Class | Package | What it does | Verified API |
|---|---|---|---|
| `CompressorChart`, `CompressorChartInterface` | `neqsim.process.equipment.compressor` | Stores speed curves and retrieves head/efficiency at flow and speed | `addCurve(double,double[],double[],double[])`, `getPolytropicHead(double,double)`, `getPolytropicEfficiency(double,double)` |
| `CompressorChartReader`, `CompressorChartJsonReader` | `neqsim.process.equipment.compressor` | Loads curve data and attaches chart curves to a compressor | `setCurvesToCompressor(Compressor)` |
| `CompressorChartCalibrator`, `SafeSplineSurgeCurve`, `SafeSplineStoneWallCurve` | `neqsim.process.equipment.compressor` | Fits measured surge points and provides safe surge/stonewall curve implementations | `fitSurgeCurve(double[],double[])`, `molarMassHeadCorrectionFactor(double,double)` |
| `CompressorCurveCorrections`, `CompressorCurveTemplate`, `CompressorChartMWInterpolation` | `neqsim.process.equipment.compressor` | Applies declared curve corrections/templates or interpolates maps by gas molecular weight | `calculateReynoldsEfficiencyCorrection(double,double)`, `addMapAtMW(double,double[],double[][],double[][],double[][])` |
| `CompressorChartAlternativeMapLookup`, `CompressorChartAlternativeMapLookupExtrapolate`, `CompressorChartKhader2015` | `neqsim.process.equipment.compressor` | Alternative map lookup, explicit extrapolation variant and Khader chart form | `getPolytropicHead(...)`, `getPolytropicEfficiency(...)`, `setCurves(...)` |
| `Compressor`, `CompressorTrain` | `neqsim.process.equipment.compressor` | Core machine and a separator-compressor-aftercooler composite | `setCompressorChart(...)`, `getChartFlowStatus()`, `isChartExtrapolated()`, `getValidatedDistanceToSurge()`, `getCompressor()` |
| `OperatingEnvelope`, `CompressorThermalModel`, `StartupProfile`, `ShutdownProfile` | `neqsim.process.equipment.compressor` | Surge/stonewall boundaries, compressor heat network and speed-versus-time profiles | `getSurgeMargin(double,double,double)`, `getStonewallMargin(double,double,double)`, `getNodes()`, `getTargetSpeedAtTime(double,double)` |
| `CompressorOperatingHistory`, `CompressorWashing`, `CompressorMechanicalLosses`, `RecycleFlowCoordinator`, `AdvancedAntiSurgeControlSystem` | `neqsim.process.equipment.compressor` | History, degradation/wash, seal and bearing losses, recycle and advanced controller calculations | `getCurrentFoulingFactor()`, `getTotalMechanicalLoss()`, `getLastRecycleFlow()`, `calculateValveCommand(double,double,double)` |
| `TurboExpanderCompressor`, `MapTurboExpanderCompressor`, `Expander` | `neqsim.process.equipment.expander` | Expander or coupled expander/compressor machine and power-balance state | `getPowerExpander("kW")`, `getPowerCompressor("kW")`, `getPowerBalanceResidual()`, `getOperatingStatus()` |
| `TurboExpanderMapIngestion`, `RadialExpanderGeometryMap`, `TurboExpanderOperatingEnvelope`, `ExpanderChartKhader` | `neqsim.process.equipment.expander` | Map anchors, geometry-derived curves, operating grid and Khader chart lookup | `addAnchorPoint(String,double,double,double)`, `setReferenceFluid(SystemInterface)`, `setGrid(double[],double[])`, `getFeasibility()` |
| `Pump`, `PumpChart`, `SuckerRodPump` | `neqsim.process.equipment.pump` | Centrifugal pump thermodynamics/map; sucker-rod pump specialty model | `setOutletPressure(double)`, `getPower(String)`, `getPumpChart()`, chart `getHead(double,double)` and `getNPSHRequired(double,double)` |
| `PumpHydraulicsNpshCalculator`, `PumpApi610DesignCalculator` | `neqsim.process.mechanicaldesign.pump` | Hydraulic power/NPSH calculation and evidence-based API 610 screening | `setDutyPoint(double,double,double,double)`, `setSuctionConditions(double,double,double,double,double,double)`, `calcHydraulics()`, `getNpshAvailable()`, API 610 `calculate()`, `getChecks()` |
| `AxialThrustScreening`, `CompressorCasingDesignCalculator`, `RotorUnbalanceAssessment`, `CompressorDesignFeasibilityReport` | `neqsim.process.mechanicaldesign.compressor` | Compressor thrust, casing, unbalance and feasibility screens | `getPredictedThrust()`, `getRequiredWallThicknessMm()`, `getUnbalanceRatio()`, `getVerdict()` |
| `TurboExpanderSealGasEnvelope` | `neqsim.process.mechanicaldesign.expander` | Coupled expander/compressor seal-gas and thrust envelope | `getNetAxialThrust()`, `getThrustUtilisation()`, `getRequiredHeaterDuty()` |

### Map workflow

Couple the common compressor shaft to `CompressorPowerTrain` for direct turbine,
external motor/VFD or turbine-generator-motor supply. Use the
[integrated compression-power guide](../../../docs/process/equipment/integrated_compression_power.md)
and `neqsim-power-generation` for the exact matching API and evidence boundaries.
Power feasibility is separate from compressor chart validity, surge/stonewall and
the turbine minimum-load limit. A configured driver with zero capability cannot
be treated as unlimited. Use supplied OEM maps for actual machine qualification.

1. Fix a reference gas, speed, inlet basis, flow unit and head unit from the vendor source. Keep the raw points and their source alongside derived points.
2. Choose the chart class by interpolation/extrapolation behavior. Load measured curves through `CompressorChartReader`/`CompressorChartJsonReader` or use `CompressorChart.addCurve(...)`/`setCurves(...)` for explicit points.
3. Attach via `Compressor.setCompressorChart(...)`; evaluate head and efficiency at representative vendor points before using a full process train.
4. Apply only corrections supported by documented inputs. `CompressorChartCalibrator.fitSurgeCurve(...)` installs a measured surge curve; its molecular-weight method returns a correction factor, it does not alter a map by itself.
5. At every design, sweep and optimizer point, inspect `getChartFlowStatus()` and `isChartExtrapolated()`. Use `getValidatedDistanceToSurge()` where distance-to-surge is consumed by a safety/optimization decision.

Turboexpander map work is separate: create `TurboExpanderMapIngestion`, add vendor/test anchor points with velocity ratio, IGV opening and measured efficiency, configure the reference fluid and impeller diameters, then validate against the expander/compressor operating envelope. Geometry-derived maps and `ExpanderChartKhader` are correlations/models, not converted vendor data.

### Turboexpander capacity / IGV / JT bypass

`TurboExpanderCompressor` can model nozzle swallowing capacity m = k·s·sqrt(rho_in·P_in[Pa])·IGV^n (`setNozzleCapacityModel(k, n)` or a flow-function table, `setNozzleCapacityScale(s)` per train). In automatic IGV mode (default) the IGV opens as needed and `isCapacityLimited()` means fully open at capacity (`getRequiredIgvOpening() > 1`); with `setIgvControlMode(true)` the held `setIGVopening(...)` fixes the capacity. `setJtBypassEnabled(true)` throttles the surplus isenthalpically and mixes it with the wheel outlet (`getJtBypassFraction()`, `getExpanderWheelOutletTemperature(unit)`); without it the over-capacity flow still passes the wheel and only `getNozzleCapacityUtilisation() > 1` flags it. Shaft closure is `k_c·P_exp = P_comp + P_bearing` (`setShaftClosureFactor`, `setBearingLossPower`, `getShaftPowerResidual()`); the speed is solved from it inside `setSpeedLimits(min, max)` (`getSpeedStatus()` = ok/speed_max/speed_min, `getSpeedUtilisation()`). Absolute maps for fitted machines: `setExpanderEfficiencyParabola(etaMax, xOpt, a, floor)` in U/C0 and `setCompressorHeadPolynomial` / `setCompressorEfficiencyPolynomial` in phi = Q·60/N; `copyPerformanceMapFrom(other)` gives per-train variants. Defaults leave the legacy model unchanged. Reference: `TurboExpanderCapacityTest` (cross-checked to 1 % against an independent reference implementation of the same model); doc: `docs/process/equipment/expanders.md`.

**Calibrating the class to plant data (Oseberg D, benchmarked 2026-11, 17 500 half-hour points, 24 days out of sample).** (1) Never take the expander mass flow from a compressor "equivalent flow" tag (FI-0515/-0565 type): it contains the anti-surge recycle (+3 % at a closed valve, +47 % at 70 % force-open) and inflated the fitted fully open flow function by 21-27 %. Build an independent mass balance (inlet flow x composition) and fit `setNozzleCapacityModel(k, n)` on it, anchored at IGV >= 95 %; both trains then give the same k. (2) The class has no anti-surge recycle: if the compressor recycles 15-25 %, pass the TOTAL flow on the recompressor feed stream and do not use a closure factor `setShaftClosureFactor` above 1 to absorb it (fit a shaft-transfer efficiency polynomial instead: `m_c,tot H / W_exp` from measured temperatures of the expander, flow and pressure-based head). (3) Do not trust temperature-based compressor efficiency when a cold-gas bypass joins downstream of the suction thermometer (apparent efficiency up to 1.18); use the pressure-based head, which collapses on one curve against Q/N. (4) Validate on data that no calibration has seen: configured this way the class reproduced the measured speed to 0.8 % (MAE, 300 points) and the cold-separator temperature to 0.3 K. (5) Compare the fitted fully open capacity with the data sheet design flow before extrapolating to future years.

**Vendor data before assumptions (Oseberg D, 2026-11).** Read the data sheet and FAT curve as page images: the text layer of the STID PDFs keeps the labels and drops the filled-in values (max continuous / trip speed 8683 / 9117 rpm, bearings, design cases). (1) Speed limits come from the data sheet (and the SCD running permissive), not from the FAT speed range; an assumed limit of 7200 rpm had produced a false over-speed risk. (2) The surge line is a constant Q/N (1.170 Am3/h per rpm at all four design cases and in the FAT): compare it with the operating flow coefficient before treating the anti-surge setting as given; operation sat at 2.0 times the surge flow and a retune to surge + 10 % moved the two-train turndown from 2036 to 2039. (3) With the compressor affinity confirmed by the FAT, head coefficient against flow coefficient is one curve; a fitted map can be checked against it in a few lines (pressure ratio at the line ends gives the head, shaft power gives the efficiency). (4) Tag-based expander efficiency can be 0.08 low; close the shaft balance with the FAT compressor efficiency for a wheel estimate, and test the FAT efficiency-versus-speed shape (power at design flow against speed) against the fitted quadratic before extrapolating to low speed ratios. (5) Do not scale a revamp speed limit with 1/lambda if the shaft and bearing housing stay (Frame 60 options): keep the data-sheet maximum continuous speed.

## Dry gas seal systems, conditioning and troubleshooting

Read [the engineering review](../../../docs/process/equipment/dry_gas_seal_engineering_review.md)
for the primary literature, source audit, standards boundaries, failure hypotheses
and validation priorities. Use public/synthetic data unless private inputs are supplied
and authorized. Retain OEM limits and parameter provenance rather than transferring
another machine's rules of thumb.

Use these existing APIs after inspecting current constructors/methods:

- `DryGasSealVentStudy`: run each seal-end failure configuration with project leakage and rated vent curves. Use `Vent.fromGasPipe` for low-Mach horizontal line sensitivity and `evaluate` for cavity pressures, forward/reverse loading and process-origin release. Preserve unresolved cases. See [vent studies](../../../docs/process/equipment/dry_gas_seal_vent_study.md).
- `DryGasSealAnalyzer`: configure an owned gas clone with `setSealGas`, cavity/vent
  pressures, temperature, leakage and independent GCU supply throughput. Run
  `runFullAnalysis` and inspect complete/error evidence in `getResults`.
  `isSafeToOperate` is a legacy sampled-condensation screen, not an operating permissive.
- `SealGasSupplyConditioning`: `setSource`, `setSupplyPressure`, optional
  `setHeaterOutletTemperature`, then supply temperature, dewpoints, margin and heater
  search. The audit identifies single-liquid, failed/missing dewpoint, unit and caller
  ownership concerns. Validate evidence independently; do not equate a passing
  `isMarginMet` Boolean with demonstrated complete-path dryness. Use
  `impliedLiquidLoadKgPerMSm3` and `sumpFillTimeDays` only with explicit input bases.
- `DryGasSealMonitor`: caller-defined `Limits`, quality-aware `Sample`,
  `evaluate` and explicit simulator `reset`. Supply-process DP, primary vent,
  intermediate buffer and bearing-side separation signals are distinct.
- `LabyrinthSealFlowCalculator`: process-side straight-through labyrinth demand,
  choking and validity checks. This is not grooved dry-seal face leakage.
- `CompressorThermalModel`: calibrated lumped seal/shaft/metal temperatures;
  this does not resolve local seal-film thermohydrodynamics.

Freeze source/cavity/process/vent pressures, seal arrangement, startup/running/trip/
standstill mode, composition including water/heavy ends, gas/metal/ambient temperatures,
normal-flow conditions, timestamp/quality and OEM limits. Evaluate regulator JT
cooling, wet supply, heater/booster capacity, filters/coalescers, sump/drains, vent
backpressure and buffer/separation loss. Primary vent flow may include nitrogen;
reconcile branch balances and flowmeter gas basis before diagnosing face damage.

Use API 692 and ISO 6368:2021 for dry-seal-system scope. API 614 sixth edition
excludes dry gas seal systems. The existing 17 K/20 K defaults are screening inputs,
not universal API 692 requirements. Full licensed clauses, project edition and OEM
instructions are needed for a conformity assessment.

Keep failed/missing thermodynamics unresolved. Compare competing hypotheses and
changes after interventions; a high vent signal alone does not uniquely identify
seal failure. Hand historian data to `@plant-data`, fault hypotheses to `@root-cause`,
fluid validity to `@thermo-fluid`, and regulator/controller dynamics to `@control-system`.
Reuse #4144/#4145 and roadmap #4149; do not claim that source discovery adds a curated
MCP operation. Keep expensive diagnostics optional so ordinary compressor runs do
not gain extra flashes.

## Build pattern

This Java 8 example follows the `CompressorTrain` usage shown in source. It creates a simple gas feed, runs a scrubber-compressor-aftercooler unit and reads shaft power. Replace the illustrative conditions and efficiencies with a controlled design basis.

```java
import neqsim.process.equipment.compressor.CompressorTrain;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface gas = new SystemSrkEos(280.0, 30.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.08);
gas.addComponent("propane", 0.02);
gas.setMixingRule("classic");
Stream feed = new Stream("Feed", gas);
feed.setFlowRate(5000.0, "kg/hr");
CompressorTrain train = new CompressorTrain("HP Train", feed);
train.getCompressor().setOutletPressure(85.0);
train.getCompressor().setPolytropicEfficiency(0.76);
train.getCompressor().setUsePolytropicCalc(true);
train.getAftercooler().setOutletTemperature(308.15);
train.run();
double powerKW = train.getPower("kW");
```

Equivalent Python class lookup:

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
CompressorTrain = jneqsim.process.equipment.compressor.CompressorTrain
gas = SystemSrkEos(280.0, 30.0)
gas.addComponent("methane", 0.90)
gas.addComponent("ethane", 0.08)
gas.addComponent("propane", 0.02)
gas.setMixingRule("classic")
feed = Stream("Feed", gas)
feed.setFlowRate(5000.0, "kg/hr")
train = CompressorTrain("HP Train", feed)
train.getCompressor().setOutletPressure(85.0)
train.getCompressor().setPolytropicEfficiency(0.76)
train.getCompressor().setUsePolytropicCalc(True)
train.getAftercooler().setOutletTemperature(308.15)
train.run()
power_kw = train.getPower("kW")
```

## Result extraction

| Quantity | API / unit |
|---|---|
| Compressor chart head and efficiency | `getPolytropicHead(flow,speed)` in chart head unit; `getPolytropicEfficiency(flow,speed)` as chart value |
| Chart-range status | `getChartFlowStatus()` returns `IN_RANGE`, `EXTRAPOLATED_LOW_FLOW`, `EXTRAPOLATED_HIGH_FLOW` or `NO_CHART` |
| Validated surge distance | `getValidatedDistanceToSurge()` returns the normal distance or `Double.NaN` when the chart point is extrapolated |
| Train power | `CompressorTrain.getPower("kW")` |
| Pump map | `PumpChart.getHead(flow,speed)`, `getEfficiency(flow,speed)`, `getNPSHRequired(flow,speed)` |
| Pump NPSH/hydraulics | `PumpHydraulicsNpshCalculator.getNpshAvailable()`, `getNpshMargin()`, `getHydraulicPower()`, `getBrakePower()` |
| API 610 review | `PumpApi610DesignCalculator.getAssessmentStatus()`, `getChecks()` and per-check unit/limit/status |
| Expander/compressor power | `getPowerExpander("kW")`, `getPowerCompressor("kW")`; coupled machine residual from `getPowerBalanceResidual()` |
| Mechanical screens | Check units on each result class; examples include casing thickness mm/pressure MPa, thrust N, and unbalance g·mm |

## Compressor trains on a common shaft (power-limited driver)

Use `CompressorShaft` (not one `Compressor` with `setSolveSpeed(true)` per body) whenever stages share a driver, also
when the stages sit in different platform models. Full code pattern: `neqsim-compressor-antisurge-recycle`, section
"Common-Shaft Trains Across Platforms, Power-Limited Drivers and Ambient Derating". Quick map from the Python
`ShaftGroup` idiom (Oseberg `eot_ofc_common.shaft`) to Java:

| Need | Java |
|---|---|
| members with gear ratio | `addCompressor(c, ratio)`, `setGearRatio`, `getMemberSpeed` |
| controlling member sets speed | `solveSpeedToTarget(member, DISCHARGE_PRESSURE / SUCTION_PRESSURE / INLET_FLOW, value, unit, run)` |
| per-member margins, recycle demand, flags | `getOperatingReport()` (`toJson()`), `setMemberRequiredDischargePressure` |
| train off / decoupled stage | `setMemberActive(c, false)`, `removeCompressor(c)`, status `SHAFT_IDLE` |
| shared driver power with ambient | `setGasTurbineUnitDriver(unit)` + `setAmbientTemperature(T, "C")`, `setMaxShaftPower`, `setElectricSupplyLimit` |
| max speed / max throughput at the driver limit | `findMaxSpeedWithinPowerLimit(run)`, `DriverLimitedThroughputSolver` |
| fuel, exhaust, waste heat | `updateDriverOperatingPoint()` then the `GasTurbineUnit` getters |

Not in Java: the Python snapshot evaluation without re-running the flowsheet (Java always re-runs through the
callback).

Also in Java: `setAntiSurgeValve(member, valve)` / `setAntiSurgeController(member, controller)` +
`applyRequiredRecycle()` (idempotent, positions each member's recycle valve at its `requiredRecycleFraction`);
`setMotorDriverUnit(ElectricMotor)` / `setDriverCurve(curve)` derive the speed band from the VFD or fixed-speed motor;
`CompressorShaftCalculator` and `PlatformPowerSystemUnit` publish automation addresses (`<calc>.speed`,
`<calc>.member.<tag>.surgeMargin|recycleFraction|active`, `<unit>.availablePower|deficit|n1Margin|fuel|co2`,
`ambientTemperature` writable) via `AutomationAddressable`; `PlatformPowerSystemUnit.connectToBus(EnergyBus)` couples
the platform to `CoupledProcessEnergySolver`. Details: `docs/process/equipment/compressor_shaft.md`.

## Chart gas similarity, efficiency calibration and chart-speed basis

Lessons from a multi-platform compressor-limit study (generic; verify against the current classes before use).

- **Fan-law collapse is not gas similarity.** A chart evaluated with `phi = Q/N`, `psi = H/N^2` knows neither the gas
  nor the tip-speed Mach number. The first-order gas effect (molecular weight, kappa, Z) is already in the polytropic
  head the thermodynamic model requires. A Mach / sound-speed similarity (`CompressorMapSimilarity` gives the
  acoustic conversion `f = a_design / a`; equivalent state `N_eq = f N`, `Q_eq = f Q`, `H = H_chart / f^2`) changes a
  result only for a **multi-line vendor chart**, whose line-to-line spread of head coefficient with speed carries the
  Mach effect. For a chart with **one speed line**, a generic or anchored map, or any chart the fan laws collapse onto
  one curve, `H_chart(f Q, f N) / f^2 = H_chart(Q, N)` exactly: the similarity is the identity there, so a result that
  "moves" with it is a bug and one that does not move is correct. Treat a factor `f` outside about 0.75-1.25 as a
  validity flag, not a result, and say which charts are informative before presenting a gas sensitivity. Nothing in
  `CompressorMapSimilarity` changes a chart silently; connecting it to a chart lookup is an explicit, opt-in step.
- **Effective efficiency absorbs losses.** Calibrating a polytropic efficiency on a measured discharge temperature
  folds heat loss, instrument offset, seal/bearing losses and (for a turboexpander-driven compressor) shaft-closure
  error into one number. It is a calibrated item, not a machine property: never score the discharge temperature as
  independent validation, do not transfer the value to another year, gas or machine without a check, and flag
  efficiencies above the vendor value. Power from a chart is `m_dot Hp g / eta_p / eta_mech`; check modelled train
  power against driver load and a measured interstage pressure.
- **Chart-speed basis.** Digitised chart speed grids, datasheet rated speeds, driver (power-turbine) speed and the
  member speed through a gearbox are four different bases. Derive the effective ratio per machine from a measured
  window, keep the other window as a check, and state whether a chart speed line is in rpm or percent of a reference
  rpm. A model speed that differs systematically from measured speed at equal pressure ratio is a basis mismatch before
  it is an efficiency problem.
- **Decoupling dates are per-year modes.** When a stage is moved to its own driver on a known date, build each year
  from parts: before the date one `CompressorShaft` with the controlling member setting the speed, after it two
  shafts with the new pairing; a year containing the date is run as two time-weighted parts (for example H1/H2) and
  reported with the weight. Keep the date and pairing in a table, not in code branches.
- **Power turbine tied to the power module.** Drive the shaft power limit from the gas-turbine site rating at the
  year's ambient temperature, report utilisation against the ISO rating separately from the site rating, and mark years
  with shared shafts as the least certain.

## EOS choice for dense-gas compressors (gas injection, 200-720 bar)

Measured 2026-11-27 on the Bacalhau HP/gas-injection compressors (lean gas, C1 82 mol%, 84-720 bar, 35-135 C), with
GERG-2008 as reference (NeqSim GERG-2008 matched CoolProp GERG-2008 to 0.01 % density, 0.04 % Cp; pure methane at 720 bar
matched the Setzmann-Wagner equation to 0.03 % density and 0.13 % enthalpy rise).

- **Default `SystemSrkEos`/`SystemPrEos`/`SystemPrEos1978`/`SystemUMRPRUMCEos` translate density only.** Enthalpy, entropy,
  Cp, Cv and sound speed come from the untranslated EOS, so compressor work is wrong when the pressure rise is large:
  at equal flow, discharge pressure and polytropic efficiency the power is PR78 -1.9 / -4.1 / -6.4 % and UMR-PRU-MC
  -2.8 / -5.3 / -7.5 % for stage 1 (84-270 bar), stage 2 (270-510 bar) and gas injection (500-700 bar), although the UMR
  inlet density is within 0.2-1 %. The missing term is `c*dP` (about 5.7 kJ/kg of an 80 kJ/kg stage at 500 to 720 bar).
- **Reference choice:** `compressor.setUseGERG2008(true)` keeps the PR/UMR fluid for phase equilibrium and uses GERG-2008
  for single-phase compressor streams (pseudo-components are mapped by molar mass; run time about 3x). Power in the
  Bacalhau EOT model rose 2.6 / 5.0 / 7.3 % for the three stages at unchanged flow and map factors. GERG-2008 is single
  phase only, valid to 35 MPa (extended to 70 MPa): state that a 72 MPa discharge is at the edge of the extended range.
- **Best cubic when no GERG route exists:** `SystemPrEosvolcor` (translation inside the EOS): power +1.3 / +2.5 / +1.0 %,
  density -1.3 to -3.3 %, sound speed -2.7 to -4.3 %. `SystemSrkEos` is within +3.2 % on power
  (error cancellation, not a better translation). Avoid `SystemSrkPenelouxEos` (density +4.7 % at 500 bar), `SystemBWRSEos` (Cp 87 % high),
  `SystemGERG2004Eos` (native library missing) and `SystemEOSCGEos` (zero density for this fluid) here.
- Default PR/SRK `getSoundSpeed()` is also untranslated (PR78 -12 % at 500 bar, `SystemPrEosvolcor` -4 %).
- **Experimental check (693 open densities, 240-470 K, to 196 MPa; ThermoML: methane, ethane, N2, CO2, CH4+N2, CH4+C3H8;
  2026-11-27).** `SystemGERG2008Eos` is within 0.5 % of every point (AAD 0.00-0.14 % per fluid; 0.05 % at 35-72 MPa).
  35-72 MPa (48 points): PR78/UMR-PRU-MC 0.9 % low, SRK 1.3 %, SRK-Peneloux 4.3 % high; 20-35 MPa methane + propane
  (80 points): PR78 2.6 % low, UMR 2.1 % low. Isothermal enthalpy rise derived from the measured isotherms
  (integral of v - T dv/dT; methane, ethane, CH4+N2, 320-375 K), NeqSim minus experiment over 500-700 bar
  (experiment 7-18 kJ/kg, u 0.2-0.6): GERG-2008 -0.06 (max 0.36), `SystemPrEosvolcor` +0.03 (0.84), SRK -1.2 (2.2),
  SRK-Peneloux -2.9 (3.8), PR78 and UMR-PRU-MC -4.9 (6.6) kJ/kg; over 200-700 bar PR78/UMR are -10.5 (max -16) and
  `SystemPrEosvolcor` +1.8 (max 6.5). This confirms the compression-work bias experimentally. Not covered: a propane-
  containing mixture above 35 MPa (the 250-450 K lean synthetic natural gas data to 150 MPa, doi 10.1021/je200438t, is
  not open), speed of sound and heat capacity. Download ThermoML with `https://trc.nist.gov/ThermoML/<doi>.xml`
  (JCED/JCT/FPE/IJT papers, mainly 2003 onwards).
- The large as-found map factors (head 1.2-1.3, efficiency 1.2-1.25 on stage 2 and gas injection) stayed with every EOS
  including GERG-2008: they are not an EOS artefact. The EOS shifts the flow implied by measured motor power by about
  8 percentage points (13 % vs 22 % above the metered suction flow), so re-derive recycle and map factors when the
  EOS changes. A 1 K discharge-temperature error moves the specific work 3.8 %.

## Gotchas

- `AxialThrustScreening` rejects non-finite inputs/results and requires a positive fitted zero-crossing for a relative margin. An invalid case is not a safe margin or `NULL` thrust. Report the data range, extrapolation and OEM thrust sign convention.
- `ValveRangeabilityScreening` rejects missing trim and non-finite travel/rangeability. Finite travel is clamped to 0–1; a zero reference flow gives an undefined ratio (`NaN`). Its flow ratio assumes unchanged fluid conditions and pressure drop, not an installed-system response.

- Chart interpolation and extrapolation are not equivalent. `CompressorChartAlternativeMapLookupExtrapolate` explicitly permits extrapolation; outside available reference speeds, it can use a single nearest curve and scale head with speed. Treat that output as extrapolated, not vendor-supported operation.
- A chart can return finite, plausible-looking values outside its calibrated flow range. `getChartFlowStatus()` may report extrapolation even at an auto-generated nominal point. Gate every sweep point; `getValidatedDistanceToSurge()` is `NaN` outside the reported valid flow range.
- `getDistanceToSurge()` alone does not enforce the chart-range gate. Do not bisect or optimize on it without filtering extrapolated points and reporting the number excluded.
- `fitSurgeCurve` fits the control/surge boundary from measured flow/head; `molarMassHeadCorrectionFactor` only returns `mwReference / mwActual`. Neither certifies the rest of a vendor map.
- Use `getAftercooler()` on `CompressorTrain`; the earlier source Javadoc example calling `getCooler()` was corrected 2026-10.
- Do not infer chart units from array names. Flow points are inlet actual m3/hr in `CompressorChartCalibrator.fitSurgeCurve`; head must match the chart head unit; verify map reader metadata and reference conditions.
- `PumpApi610DesignCalculator` compares caller-supplied duty, BEP, NPSH, pressure, driver and mechanical evidence. It does not derive missing vendor data or certify API 610 compliance.
- NPSH margin is a screening result only when suction pressure, vapor pressure, static head, duty and NPSHr provenance are correct and use compatible units.
- A turboexpander anchor-point map is only as good as its reference fluid and anchor provenance. Confirm power balance, shaft mode/speed limits, cold-end temperature and hydrate margin.
- Compressor thermal/startup/shutdown models do not by themselves establish a stable controller or machine protection function; pair with dynamic process and anti-surge workflows as needed.
- Bypassing or removing one stage of a fixed-speed multi-stage centrifugal train (for example a GI 2nd stage whose gas is let down again): the machine delivers head from its curve, so the saving equals that stage's curve power per tonne (derive kW per t/h from live data and a benchmarked stage model), not a power recomputed from a changed system-pressure path. A model that recomputes head from a falling downstream pressure shows the remaining stage consuming less power as the system pressure falls, which a fixed-speed machine does not do (the excess pressure is throttled). Bound the two views and run the weight between them as an uncertainty. Clamp flow at the minimum stable flow per running train (recycle) in decline years.

- Chart-driven (head, efficiency) trains modelled outside `Compressor`: shaft power is `m_dot * Hp * g / eta_p / eta_mech`, not `m_dot * Hp * g`; the Schultz exponent is `n/(n-1) = kappa * eta_p / (kappa - 1)`. A sibling DX revamp model that dropped the `/eta_p` under-stated power about 25 % and reached only 71 % of the measured gas-turbine load; the corrected form reached 91-93 % (Oseberg DX, Oct 2026). Always check modelled train power against the driver load (rated shaft power x load %) and a measured interstage pressure before trusting savings. Stages that appear idle ("deep recycle", anti-surge flow close to the full train flow) still absorb power: derive it from measured suction/discharge pressures and the equivalent-flow tags (they were within 5 % of the model's actual flows) and run it through `Compressor` with a polytropic efficiency range.

## Validation / benchmarks

Use `CompressorTrainTest`, `CompressorChartGeneratorTest`, `CompressorChartSpeedInterpolationTest`, `TurboExpanderEnhancementsTest`, pump chart tests, and the corresponding design-calculator tests as API/regression evidence. Validate map values at vendor-rated points, hold out at least one point or curve, verify reference-gas conversion, and demonstrate the full intended operating envelope is in range. For API 610/API 617 screening, retain data provenance, assumptions, standard edition and unresolved checks. A generated map or a passing software test is not a vendor performance test.

## Related skills

- `neqsim-compressor-antisurge-recycle` — anti-surge recycle and minimum-speed coordination
- `neqsim-api-patterns` — compressor and pump process API patterns
- `neqsim-dynamic-equipment-implementation` — adding transient behavior to equipment
- `neqsim-troubleshooting` — operating-point and process convergence diagnostics
- `neqsim-power-generation` — gas-turbine/driver coupling
- `neqsim-dynamic-simulation` — transient controllers and process response
- `neqsim-vfp-lift-curves` — compressor capacity vs suction pressure as a VFP rate cap (scan the feasible flow band; a chart at low pressure ratio is not a bisection problem)

