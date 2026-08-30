---
title: Measurement Devices and Analysers
description: NeqSim provides a comprehensive set of measurement devices and process analysers for monitoring fluid properties, compositions, and process conditions.
---

# Measurement Devices and Analysers

NeqSim provides a comprehensive set of measurement devices and process analysers for monitoring fluid properties, compositions, and process conditions.

## Overview

Measurement devices in NeqSim fall into several categories:

- **Fluid Analysers** - Dew point, composition, emissions
- **Process Monitors** - Level, pressure, temperature, flow
- **Safety Detectors** - Gas and fire detection
- **Performance Monitors** - Vibration analysis, compressor monitoring
- **Quality Analysers** - Hydrocarbon dew point, water content, molar mass

## Fluid Composition Analysers

### CombustionEmissionsCalculator

Calculates CO2 emissions from fuel gas combustion based on stream composition.

```java
import neqsim.process.measurementdevice.CombustionEmissionsCalculator;

// Create fuel gas stream
Stream fuelGas = new Stream("Fuel Gas", gas);
fuelGas.setFlowRate(1000.0, "kg/hr");
fuelGas.run();

// Create emissions calculator
CombustionEmissionsCalculator emissionsCalc =
    new CombustionEmissionsCalculator("CO2 Calculator", fuelGas);

// Get CO2 emissions rate
double co2Emissions = emissionsCalc.getMeasuredValue("kg/hr");  // kg CO2/hr
```

**CO2 Emission Factors (kg CO2 per kg component):**

| Component | Emission Factor |
|-----------|----------------|
| Methane | 2.75 |
| Ethane | 3.75 |
| Propane | 5.50 |
| n-Butane | 6.50 |
| n-Pentane | 7.50 |
| Hexane | 8.50 |
| Nitrogen | 0.0 |
| CO2 | 0.0 |

### NMVOCAnalyser

Calculates the mass flow rate of Non-Methane Volatile Organic Compounds (nmVOCs).

```java
import neqsim.process.measurementdevice.NMVOCAnalyser;

// Create analyser
NMVOCAnalyser nmvocAnalyser = new NMVOCAnalyser("NMVOC Monitor", ventStream);

// Get nmVOC flow rate
double nmvocFlow = nmvocAnalyser.getMeasuredValue("kg/hr");
double nmvocYearly = nmvocAnalyser.getnmVOCFlowRate("tonnes/year");  // tonnes/year
```

**Components included in nmVOC calculation:**
- Ethane, Propane, i-Butane, n-Butane
- i-Pentane, n-Pentane, n-Hexane, n-Heptane
- Benzene, nC8, nC9, nC10, nC11

## Dew Point Analysers

### HydrocarbonDewPointAnalyser

Calculates the hydrocarbon dew point temperature at a specified pressure.

```java
import neqsim.process.measurementdevice.HydrocarbonDewPointAnalyser;

HydrocarbonDewPointAnalyser hcdp =
    new HydrocarbonDewPointAnalyser("HC Dew Point", gasStream);
hcdp.setReferencePressure(50.0);  // bara

double dewPointC = hcdp.getMeasuredValue("C");  // hydrocarbon dew point, degC
```

### WaterDewPointAnalyser

Calculates the water dew point temperature.

```java
import neqsim.process.measurementdevice.WaterDewPointAnalyser;

WaterDewPointAnalyser wdp =
    new WaterDewPointAnalyser("Water Dew Point", gasStream);
wdp.setReferencePressure(50.0);  // bara

double waterDewPoint = wdp.getMeasuredValue("C");  // water dew point, degC
```

### CricondenbarAnalyser

Calculates the cricondenbar (maximum pressure on phase envelope).

```java
import neqsim.process.measurementdevice.CricondenbarAnalyser;

CricondenbarAnalyser cricondenbar = new CricondenbarAnalyser(gasStream);
double maxPressure = cricondenbar.getMeasuredValue("bara");  // cricondenbar, bara
```

### HydrateEquilibriumTemperatureAnalyser

Calculates the hydrate equilibrium temperature at the stream pressure.

```java
import neqsim.process.measurementdevice.HydrateEquilibriumTemperatureAnalyser;

HydrateEquilibriumTemperatureAnalyser hydrateAnalyser =
    new HydrateEquilibriumTemperatureAnalyser(gasStream);
double hydrateTemp = hydrateAnalyser.getMeasuredValue("C");  // hydrate formation temperature, degC
```

Concrete local instances of these four thermodynamic-limit analysers participate in transient-step
transactions when registered in a `ProcessSystem`. Rollback restores each stream binding and
complete inherited measurement/alarm state. It also restores reference pressure for the hydrate,
hydrocarbon-dew-point and water-dew-point analysers, plus the configured method for both dew-point
analysers. Scheduled configuration changes therefore replay together with `EventScheduler`
pending/fired bookkeeping, and Java serialization preserves identity and restart state.

Concrete descendants and online-signal operation fail closed. This support changes no phase
envelope, dew-point, hydrate or empirical correlation. It establishes rollback mechanics only; it
does not qualify thermodynamic model selection, fluid characterization, sampling, analyser
accuracy, alarm/trip integrity, external I/O, virtual commissioning or OTS use.

## Vibration Analysis

### FlowInducedVibrationAnalyser

Calculates Flow-Induced Vibration (FIV) risk indicators for pipelines.

```java
import neqsim.process.measurementdevice.FlowInducedVibrationAnalyser;

// Create pipeline
PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("Export", feed);
pipeline.setLength(5000.0);
pipeline.setDiameter(0.3048);  // 12 inch
pipeline.setThickness(0.0127); // 0.5 inch
pipeline.run();

// Create FIV analyser
FlowInducedVibrationAnalyser fivAnalyser =
    new FlowInducedVibrationAnalyser("FIV Monitor", pipeline);
fivAnalyser.setSupportArrangement("Stiff");
fivAnalyser.setSupportDistance(3.0);  // meters

// Get FIV metrics
fivAnalyser.setMethod("LOF");  // Likelihood of Failure
double lof = fivAnalyser.getMeasuredValue("");

fivAnalyser.setMethod("FRMS");  // Fatigue Root Mean Square
double frms = fivAnalyser.getMeasuredValue("");
```

**Support Arrangements:**
- `"Stiff"` - Well-supported piping
- `"Medium stiff"` - Moderate support
- `"Medium"` - Typical support
- `"Flexible"` - Minimal support

**Analysis Methods:**
- `"LOF"` - Likelihood of Failure (API RP 14E based)
- `"FRMS"` - Fatigue Root Mean Square

When a concrete local `FlowInducedVibrationAnalyser` is registered as a process measurement
device, transient transactions preserve its pipe binding, support and method configuration,
segment set, and the segment selected by an implicit calculation. Rejected trials can therefore
restore the analyser configuration and reproduce the same derived value; accepted commits retain
the update. This is transaction and restart coverage only and does not newly qualify the FIV
correlations or the underlying pipe model.

## Process Monitors

### PressureTransmitter

Monitors pressure at a measurement point.

```java
import neqsim.process.measurementdevice.PressureTransmitter;

PressureTransmitter pt = new PressureTransmitter(separator);
pt.setUnit("bara");
double pressure = pt.getMeasuredValue();
```

### TemperatureTransmitter

Monitors temperature at a measurement point.

```java
import neqsim.process.measurementdevice.TemperatureTransmitter;

TemperatureTransmitter tt = new TemperatureTransmitter(heatExchanger);
tt.setUnit("C");
double temperature = tt.getMeasuredValue();
```

When registered in a `ProcessSystem`, concrete local `PressureTransmitter` and `TemperatureTransmitter` instances take
part in transient step transactions. Rollback restores their stream binding, noise generator, delay/filter/fault state,
alarm state, and measurement configuration so a rejected sample can be replayed exactly. Subclasses and online-signal
bindings fail the transaction-coverage preflight until they provide a complete snapshot or external-I/O commit contract.

### LevelTransmitter

Monitors the unitless liquid-level fraction reported by a `Separator` or `Tank`. The transmitter
delegates to the vessel's authoritative Java state; it does not infer instrument technology, nozzle
design, alarm setpoints, or control intent.

```java
import neqsim.process.measurementdevice.LevelTransmitter;

LevelTransmitter separatorLevel = new LevelTransmitter("LT-2001", separator);
LevelTransmitter tankLevel = new LevelTransmitter("LT-2002", tank);

double separatorFraction = separatorLevel.getMeasuredValue("");
double tankFraction = tankLevel.getMeasuredValue("");
```

Proteus-compatible P&amp;ID export creates a dedicated sensing tap/nozzle on the owning tank or separator and terminates
the measuring line there. A vessel's process inlet or phase outlet is never relabelled as the level tap. Automatically
generated tank or separator measurements remain visibly and machine-readably marked as unreviewed measurement-only
proposals.

### VolumeFlowTransmitter

Monitors volumetric flow rate.

```java
import neqsim.process.measurementdevice.VolumeFlowTransmitter;

VolumeFlowTransmitter vft = new VolumeFlowTransmitter(stream);
vft.setUnit("m3/hr");
double volumeFlow = vft.getMeasuredValue();
```

### DifferentialPressureTransmitter

Measures the pressure difference between a high- and low-pressure stream. The sign convention is
`high pressure - low pressure`.

```java
import neqsim.process.measurementdevice.DifferentialPressureTransmitter;

DifferentialPressureTransmitter pdt =
    new DifferentialPressureTransmitter("PDT-101", upstream, downstream);
double differentialPressure = pdt.getMeasuredValue("bar");
```

A concrete local differential-pressure transmitter registered in a `ProcessSystem` also participates in transient step
transactions. Its two stream bindings and signal/alarm state are restored in place on rollback. Subclasses and
online-signal bindings remain fail-closed.

### VenturiFlowMeter

All five differential-pressure flow meters below share a common base,
`DifferentialPressureFlowMeter` (ISO 5167-1 general principles), which supplies the geometry
(`setGeometry`/`setPipeDiameter`/`setThroatDiameter`/`getBetaRatio`), the differential pressure
(explicit or via a linked `DifferentialPressureTransmitter`), the gas density/isentropic
exponent/dynamic viscosity readers (each overridable), the Reynolds-number iteration, and the
mass/actual-volume/standard-volume accessors. They differ only in the discharge coefficient and
the expansibility factor, `ExpansibilityModel` (`ORIFICE`, `ISENTROPIC` or `CONE`).

When any of the five concrete local meters is registered in a `ProcessSystem`, it participates in transient step
transactions. Rollback restores geometry, pressure/property overrides, the last Reynolds solve, subtype configuration,
stream/transmitter bindings, and noise/delay/filter/fault/alarm state. Orifice and Venturi wet-gas caches are invalidated
and recomputed from restored inputs. A linked `DifferentialPressureTransmitter` must also be registered because it owns
its own signal state. Subclasses and online-signal bindings remain fail-closed. This rollback support does not extend the
validity ranges or qualify the meters for allocation or fiscal service.

Derives mass, actual volume and standard volume flow from a measured differential pressure across a
classical Venturi tube, using the ISO 5167-1 general equation with the ISO 5167-4 Venturi expansibility
factor. The differential pressure is either set explicitly or read from a linked
`DifferentialPressureTransmitter`, which takes precedence when present.

```java
import neqsim.process.measurementdevice.VenturiFlowMeter;

VenturiFlowMeter meter = new VenturiFlowMeter("FT-001", stream);
meter.setGeometry(205.1, 138.1, "mm");   // pipe diameter D, throat diameter d
meter.setDischargeCoefficient(0.985);    // ISO 5167-4: 0.995 machined, 0.984 as-cast, 0.985 welded
meter.setDifferentialPressure(300.0, "mbar");

double massFlow = meter.getMassFlowRate("kg/hr");
double actualFlow = meter.getVolumeFlowRate("m3/hr");
double standardFlow = meter.getStandardVolumeFlowRate("Sm3/hr");
boolean withinIso = meter.isWithinIso5167ValidityRange();  // p2/p1 >= 0.75
```

#### Wet-gas correction (ISO/TR 11583)

A Venturi over-reads when liquid is present. Selecting the ISO/TR 11583 correlation solves
the wet-gas equations iteratively and returns the *gas* mass flow:

$$q_{m,gas} = \frac{C}{\sqrt{1-\beta^4}}\,\varepsilon\,\frac{\pi d^2}{4}\,\frac{\sqrt{2\,\Delta p\,\rho_{1,gas}}}{\Phi}$$

$$\Phi=\sqrt{1+C_{Ch}X+X^2},\qquad X = \frac{q_{m,liquid}}{q_{m,gas}}\sqrt{\frac{\rho_{1,gas}}{\rho_{liquid}}}$$

```java
import java.util.List;

meter.setWetGasCorrelation(VenturiFlowMeter.WetGasCorrelation.ISO_TR_11583);
meter.setSurfaceTensionFactor(VenturiFlowMeter.H_HYDROCARBON);  // 1.0 HC, 1.35 water, 0.79 wet steam

// Supply the liquid load in one of four ways:
meter.setLiquidFromStream(true);              // from the stream's own phase split
// meter.setLiquidToGasMassRatio(0.5);        // from a recent separator test
// meter.setLiquidMassFlowRate(2.5, "kg/sec");// absolute rate
// meter.setPressureLoss(0.125, "bar");       // ISO/TR 11583 6.4.5, needs a third tapping

double gasFlow = meter.getMassFlowRate("kg/sec");
double x = meter.getLockhartMartinelliParameter();
double phi = meter.getOverReadingFactor();
double uncertainty = meter.getRelativeUncertaintyOfCOverPhi();   // 6.5 Table 2
List<String> issues = meter.getValidityViolations();             // empty when in range
```

> **ISO/TR 11583 replaces the discharge coefficient by default.** In wet-gas mode the value passed
> to `setDischargeCoefficient` is overridden by the wet-gas $C$ of Equation (4) (which tends to 1
> rather than 0.985), unless `setUseWetGasDischargeCoefficient(false)` is called, in which case the
> configured (e.g. in-service-calibrated) $C$ is kept and only the $\Phi$ over-reading is applied.

Limits of use (reported, not enforced): $0.4\le\beta\le0.75$, $0<X\le0.3$, $Fr_{gas,th}>3$,
$\rho_{gas}/\rho_{liquid}>0.02$, $D\ge50$ mm. The Technical Report covers a single liquid at
roughly 95 % gas volume fraction or more and states it "is not intended for the oil and gas
industry"; combining an aqueous and a hydrocarbon phase into one effective liquid is an
extension beyond it. Gas and liquid density can be supplied from sampling with
`setGasDensity` / `setLiquidDensity` instead of being read from the stream, as the Technical
Report advises against in-line densitometers in wet-gas service.

#### Wet-gas correction (de Leeuw, 1997)

The de Leeuw (1997) correlation, reported by R.N. Steven, "Wet gas metering with a horizontally
mounted Venturi meter", *Flow Measurement and Instrumentation* 12 (2002) 361-372, uses the same
Chisholm-form over-reading equation as ISO/TR 11583 but with a purely Froude-number-based
exponent that has no diameter-ratio term, and it never replaces the discharge coefficient:

$$n = 0.41 \ \ (Fr_{gas}\le 1.5), \qquad n = 0.606\left(1-e^{-0.746\,Fr_{gas}}\right) \ \ (Fr_{gas}\ge 1.5)$$

```java
meter.setWetGasCorrelation(VenturiFlowMeter.WetGasCorrelation.DE_LEEUW);
meter.setLiquidFromStream(true);   // or setLiquidToGasMassRatio / setLiquidMassFlowRate

double gasFlow = meter.getMassFlowRate("kg/sec");
double phi = meter.getOverReadingFactor();
boolean inRange = meter.isWithinDeLeeuwValidityRange();
```

Because `C` is never replaced, `setUseWetGasDischargeCoefficient` has no effect on this
correlation; an in-service-calibrated discharge coefficient is safe by construction. Steven (2002)
independently benchmarked de Leeuw against five general two-phase Orifice Plate correlations and
one other Venturi correlation on NEL wet-gas-loop data and found it the best performer (RMS
fractional deviation 0.0211). However, de Leeuw's own data was taken on a 4 in Venturi with
$\beta=0.401$ and $n$ has no $\beta$ term, so a different diameter ratio is an extrapolation, and
there is no published $X$ range or permanent-pressure-loss route (unlike ISO/TR 11583 6.4.5).
`getValidityViolations()` reports the $Fr_{gas}\ge 0.5$ lower bound and a $\beta$-departure note.

### OrificeFlowMeter

Orifice plate following ISO 5167-2. The discharge coefficient is the Reader-Harris/Gallagher (1998)
equation, which depends on the pipe Reynolds number and on the pressure-tapping arrangement
(`OrificeFlowMeter.TappingArrangement`: `CORNER`, `D_AND_D_HALF` or `FLANGE`); the expansibility
factor is `ExpansibilityModel.ORIFICE`.

```java
import java.util.List;
import neqsim.process.measurementdevice.OrificeFlowMeter;

OrificeFlowMeter meter = new OrificeFlowMeter("FT-200", stream);
meter.setGeometry(200.0, 100.0, "mm");
meter.setTappingArrangement(OrificeFlowMeter.TappingArrangement.FLANGE);
meter.setDifferentialPressure(300.0, "mbar");

double massFlow = meter.getMassFlowRate("kg/hr");
List<String> issues = meter.getValidityViolations();  // 12.5 mm <= d, 50-1000 mm D, 0.1-0.75 beta, Re,D limits
```

#### Wet-gas correction (ISO/TR 11583 Clause 7)

Selecting the ISO/TR 11583 Clause 7 orifice method returns the *gas* mass flow using the same
Chisholm-form over-reading equation as the Venturi tube (Clause 6), but **the discharge
coefficient is never replaced** — Clause 7.5.2 keeps the plain Reader-Harris/Gallagher $C$,
evaluated at the Reynolds number that would occur if only the gas were flowing:

$$q_{m,gas} = \frac{C}{\sqrt{1-\beta^4}}\,\varepsilon\,\frac{\pi d^2}{4}\,\frac{\sqrt{2\,\Delta p\,\rho_{1,gas}}}{\Phi}$$

$$\Phi=\sqrt{1+C_{Ch}X+X^2},\qquad C_{Ch} = \left(\frac{\rho_{liquid}}{\rho_{1,gas}}\right)^{n} + \left(\frac{\rho_{1,gas}}{\rho_{liquid}}\right)^{n}$$

The exponent $n$ depends only on the gas densiometric Froude number and has no diameter-ratio
term (unlike Venturi's beta-reduced exponent):

$$n = 0.214 \ \ (0.2\le Fr_{gas} < 1.5), \qquad n = \left(\frac{1}{\sqrt{2}} - \frac{0.3}{\sqrt{Fr_{gas}}}\right)^2 \ \ (Fr_{gas} > 1.5)$$

```java
import java.util.List;

meter.setWetGasCorrelation(OrificeFlowMeter.WetGasCorrelation.ISO_TR_11583);

// Supply the liquid load in one of four ways:
meter.setLiquidFromStream(true);               // from the stream's own phase split
// meter.setLiquidToGasMassRatio(0.5);         // from a recent separator test
// meter.setLiquidMassFlowRate(2.5, "kg/sec"); // absolute rate
// meter.setPressureLoss(0.45, "bar");         // ISO/TR 11583 7.5.5, needs 0.5 <= beta <= 0.68

double gasFlow = meter.getMassFlowRate("kg/sec");
double x = meter.getLockhartMartinelliParameter();
double froude = meter.getGasDensiometricFroudeNumber();
double phi = meter.getOverReadingFactor();
List<String> issues = meter.getValidityViolations();   // Clause 7 limits when wet-gas mode is active
```

> **The discharge coefficient is never replaced.** Unlike `VenturiFlowMeter`'s ISO/TR 11583
> Clause 6 method, orifice Clause 7 always uses the plain dry-gas $C$, so there is no
> `useWetGasDischargeCoefficient`-style guard and an in-service-calibrated $C$ is not disturbed
> beyond the $\Phi$ over-reading division.

Limits of use (reported, not enforced): $0.24\le\beta\le0.73$, $0<X\le0.3$, $Fr_{gas}\ge0.2$,
$\rho_{gas}/\rho_{liquid}>0.014$, $D\ge50$ mm. When the 7.5.5 pressure-loss route is used (no
explicit liquid rate or ratio given, $0.5\le\beta\le0.68$), two additional bounds on $X$ and the
density ratio are also checked. As with the Venturi tube, an aqueous and a hydrocarbon liquid
phase are combined into one effective liquid when `setLiquidFromStream(true)` is used, which is
an extension beyond the Technical Report.

### NozzleFlowMeter

The four nozzle sub-types of ISO 5167-3, selected with `NozzleFlowMeter.NozzleType`: `ISA_1932`
(Reynolds-dependent), `LONG_RADIUS` (Reynolds-dependent), `THROAT_TAPPED` (Reynolds-dependent,
piecewise in Re,d) and `VENTURI_NOZZLE` (constant C). All four share the isentropic expansibility
factor, `ExpansibilityModel.ISENTROPIC`.

```java
import neqsim.process.measurementdevice.NozzleFlowMeter;

NozzleFlowMeter meter = new NozzleFlowMeter("FT-300", stream);
meter.setNozzleType(NozzleFlowMeter.NozzleType.ISA_1932);
meter.setGeometry(200.0, 100.0, "mm");
meter.setDifferentialPressure(300.0, "mbar");

double massFlow = meter.getMassFlowRate("kg/hr");
```

### ConeFlowMeter

Cone meter following ISO 5167-5. The cone has no physical throat bore: set the pipe diameter and
the cone diameter with `setGeometry`, and the diameter ratio $\beta=\sqrt{1-d_c^2/D^2}$ is derived.
The discharge coefficient is the constant 0.82 of an uncalibrated meter; the expansibility factor is
`ExpansibilityModel.CONE`.

```java
import neqsim.process.measurementdevice.ConeFlowMeter;

ConeFlowMeter meter = new ConeFlowMeter("FT-400", stream);
meter.setGeometry(200.0, 160.0, "mm");  // pipe diameter D, cone diameter dc
meter.setDifferentialPressure(300.0, "mbar");

double massFlow = meter.getMassFlowRate("kg/hr");
double coneDiameter = meter.getConeDiameter("mm");
```

### WedgeFlowMeter

Wedge meter following ISO 5167-6. The wedge has no physical throat bore either: set the pipe
diameter and the wedge gap height with `setGeometry`, or the wedge ratio $h/D$ directly with
`setWedgeRatio`, and the diameter ratio is derived per ISO 5167-6 Formula (3). The discharge
coefficient is $C=0.77-0.09\beta$ of an uncalibrated meter; since no wedge-specific expansibility
data has been published, ISO 5167-6 applies the same isentropic factor as the nozzles and the
classical Venturi tube, `ExpansibilityModel.ISENTROPIC`.

```java
import neqsim.process.measurementdevice.WedgeFlowMeter;

WedgeFlowMeter meter = new WedgeFlowMeter("FT-500", stream);
meter.setGeometry(200.0, 80.0, "mm");  // pipe diameter D, wedge gap height h
meter.setDifferentialPressure(300.0, "mbar");

double massFlow = meter.getMassFlowRate("kg/hr");
double wedgeRatio = meter.getWedgeRatio();  // h / D
```

## Safety Devices

### GasDetector

Simulates gas detection for safety systems.

```java
import neqsim.process.measurementdevice.GasDetector;

GasDetector gasDetector =
    new GasDetector("Gas Detector 1", GasDetector.GasType.COMBUSTIBLE);
gasDetector.setGasConcentration(25.0);  // % LEL
boolean gasDetected = gasDetector.isGasDetected(20.0);
```

### FireDetector

Simulates fire detection for safety systems.

```java
import neqsim.process.measurementdevice.FireDetector;

FireDetector fireDetector = new FireDetector("Fire Detector 1");
fireDetector.setDetectionThreshold(0.8);
fireDetector.setSignalLevel(0.9);
boolean fireDetected = fireDetector.isFireDetected();
```


When concrete local `GasDetector` and `FireDetector` instances are registered in a
`ProcessSystem`, their complete detector and inherited alarm/measurement state participates in
transient-step transactions. This includes gas type, concentration, species, location, LEL and
response-time configuration, plus the fire latch, signal, threshold, configured delay and location.
Scheduled event actions that change these detectors can therefore be rolled back and replayed
deterministically together with scheduler pending/fired bookkeeping. Concrete descendants and
online-signal bindings remain fail-closed.

This is an in-memory numerical rollback contract, not fire-and-gas detector certification. The
configured response time and detection delay are retained settings; the current detector classes do
not integrate those values as physical sensor dynamics. External I/O, voting logic, ESD action,
detector coverage, reliability and safety-integrity qualification remain outside this support.

### PushButton transaction boundary

A registered concrete local `PushButton` participates in transient-step transactions. Rollback
restores its pushed latch, optional blowdown-valve binding, automatic-activation setting,
logic-binding list and inherited measurement/alarm state. Scheduled pushes can therefore be
rejected and replayed together with `EventScheduler` pending/fired bookkeeping, and Java
serialization preserves the transaction identity and local restart state.

Automatic activation of a bound `BlowdownValve` and linked `ProcessLogic` actions fail the
transaction preflight because they mutate state outside the button. Setting automatic valve
activation to `false` permits a valve binding to remain as configuration while the push changes
only local state. Subclasses and online-signal operation also remain fail-closed. This is rollback
mechanics, not ESD, manual-input reliability, safety-integrity, external-I/O, virtual-commissioning
or OTS qualification.

## Quality Analysers

### MolarMassAnalyser

Calculates the molar mass of a stream.

```java
import neqsim.process.measurementdevice.MolarMassAnalyser;

MolarMassAnalyser mma = new MolarMassAnalyser(gasStream);
double molarMass = mma.getMeasuredValue("kg/mol");  // g/mol = molarMass * 1000
```

### WaterContentAnalyser

Measures water content in gas streams.

```java
import neqsim.process.measurementdevice.WaterContentAnalyser;

WaterContentAnalyser wca = new WaterContentAnalyser(gasStream);
double waterContent = wca.getMeasuredValue("ppm");  // water content, ppm
```

### pHProbe

Measures pH of aqueous streams.

```java
import neqsim.process.measurementdevice.pHProbe;

pHProbe ph = new pHProbe(aqueousStream);
double phValue = ph.getMeasuredValue("");  // pH
```

The probe extracts the stream's aqueous phase and solves it as a single-phase
`Electrolyte-CPA-EOS-statoil` system. `setAlkalinity(value)` adds NaOH on a
mmol/kg-water basis. The calculation fails closed if the selected reactions do not satisfy
aqueous charge, element-balance, and reaction-residual gates after bounded refinement; it does
not return an uncertified intermediate pH.

A registered concrete local `pHProbe` participates in transient transactions. Its snapshot
preserves the stream and reactive-system bindings, alkalinity, reaction-calculation scratch
objects, and the last cached pH input/result. Rollback therefore restores an exact cached reading
instead of retaining work from a rejected trial. The coverage does not newly validate aqueous
chemistry, alkalinity assumptions, sampling, or sensor accuracy.

## Multi-Phase Measurement

### MultiPhaseMeter

Simulates multi-phase flow meter measurements.

```java
import neqsim.process.measurementdevice.MultiPhaseMeter;

MultiPhaseMeter mpm = new MultiPhaseMeter("MPFM-1", multiphaseStream);

double gasFlow = mpm.getGasFlowRate("Sm3/hr");
double oilFlow = mpm.getOilFlowRate("m3/hr");
double waterFlow = mpm.getWaterFlowRate("m3/hr");
double waterCut = mpm.getWaterCut();
double gor = mpm.getGOR("Sm3/Sm3");
```

## Compressor Monitoring

### CompressorMonitor

Monitors compressor performance parameters.

```java
import neqsim.process.measurementdevice.CompressorMonitor;

CompressorMonitor cm = new CompressorMonitor(compressor);

double polyEff = cm.getPolytropicEfficiency();
double isenEff = cm.getIsentropicEfficiency();
double head = cm.getPolytropicHead("kJ/kg");
double power = cm.getPower("kW");
double surgeMargin = cm.getSurgeMargin();
```

## Well Allocation

### WellAllocator

Allocates production to individual wells based on test data.

```java
import neqsim.process.measurementdevice.WellAllocator;

WellAllocator allocator = new WellAllocator("Allocation System");
allocator.addWellTest("Well-A", oilRate, gasRate, waterRate);
allocator.addWellTest("Well-B", oilRate2, gasRate2, waterRate2);
allocator.allocateProduction(totalOil, totalGas, totalWater);

double wellAOil = allocator.getAllocatedOil("Well-A");
```

## Python Usage

```python
from jpype import JClass

# Import measurement devices
CombustionEmissionsCalculator = JClass('neqsim.process.measurementdevice.CombustionEmissionsCalculator')
FlowInducedVibrationAnalyser = JClass('neqsim.process.measurementdevice.FlowInducedVibrationAnalyser')
NMVOCAnalyser = JClass('neqsim.process.measurementdevice.NMVOCAnalyser')

# Emissions calculation
emissions_calc = CombustionEmissionsCalculator("CO2", fuel_stream)
co2_rate = emissions_calc.getMeasuredValue("kg/hr")
print(f"CO2 emissions: {co2_rate} kg/hr")

# nmVOC analysis
nmvoc = NMVOCAnalyser("NMVOC", vent_stream)
nmvoc_rate = nmvoc.getMeasuredValue("tonnes/year")
print(f"NMVOC: {nmvoc_rate} tonnes/year")

# FIV analysis
fiv = FlowInducedVibrationAnalyser("FIV", pipeline)
fiv.setMethod("LOF")
lof = fiv.getMeasuredValue("")
print(f"LOF: {lof}")
```

## API Reference

### MeasurementDeviceBaseClass

Base class for all measurement devices.

| Method | Returns | Description |
|--------|---------|-------------|
| `getMeasuredValue()` | `double` | Get measurement in default unit |
| `getMeasuredValue(unit)` | `double` | Get measurement in specified unit |
| `setUnit(unit)` | `void` | Set default measurement unit |
| `getUnit()` | `String` | Get current measurement unit |
| `displayResult()` | `void` | Display measurement result |

### StreamMeasurementDeviceBaseClass

Base class for stream-based measurement devices.

| Method | Returns | Description |
|--------|---------|-------------|
| `setStream(stream)` | `void` | Set the stream to measure |
| `getStream()` | `StreamInterface` | Get the measured stream |

### CombustionEmissionsCalculator

| Method | Returns | Description |
|--------|---------|-------------|
| `getMeasuredValue(unit)` | `double` | Get CO2 emissions rate |
| `setComponents()` | `void` | Update component list from stream |

### FlowInducedVibrationAnalyser

| Method | Parameters | Description |
|--------|------------|-------------|
| `setMethod(method)` | `"LOF"` or `"FRMS"` | Set analysis method |
| `setSupportArrangement(type)` | `"Stiff"`, `"Medium stiff"`, `"Medium"`, `"Flexible"` | Set pipe support type |
| `setSupportDistance(distance)` | meters | Set support spacing |
| `setSegment(segment)` | segment number | Analyse specific pipe segment |

### NMVOCAnalyser

| Method | Returns | Description |
|--------|---------|-------------|
| `getMeasuredValue(unit)` | `double` | Get nmVOC flow rate |
| `getnmVOCFlowRate(unit)` | `double` | Get nmVOC flow rate |

## See Also

- [Process Simulation](../../wiki/process_simulation)
- [Safety Systems](../safety/)
- [Pipeline Simulation](../../fluidmechanics/)
- [Capacity Constraints](../CAPACITY_CONSTRAINT_FRAMEWORK) - FIV/AIV limits
