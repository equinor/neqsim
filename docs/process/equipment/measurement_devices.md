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
double co2Emissions = emissionsCalc.getMeasuredValue("kg/hr");
System.out.println("CO2 emissions: " + co2Emissions + " kg/hr");
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
double nmvocYearly = nmvocAnalyser.getnmVOCFlowRate("tonnes/year");
System.out.println("NMVOC emissions: " + nmvocYearly + " tonnes/year");
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
hcdp.setReferencePressure(50.0, "bara");

double dewPointC = hcdp.getMeasuredValue("C");
System.out.println("HC dew point: " + dewPointC + " °C");
```

### WaterDewPointAnalyser

Calculates the water dew point temperature.

```java
import neqsim.process.measurementdevice.WaterDewPointAnalyser;

WaterDewPointAnalyser wdp =
    new WaterDewPointAnalyser("Water Dew Point", gasStream);
wdp.setReferencePressure(50.0, "bara");

double waterDewPoint = wdp.getMeasuredValue("C");
System.out.println("Water dew point: " + waterDewPoint + " °C");
```

### CricondenbarAnalyser

Calculates the cricondenbar (maximum pressure on phase envelope).

```java
import neqsim.process.measurementdevice.CricondenbarAnalyser;

CricondenbarAnalyser cricondenbar = new CricondenbarAnalyser(gasStream);
double maxPressure = cricondenbar.getMeasuredValue("bara");
System.out.println("Cricondenbar: " + maxPressure + " bara");
```

### HydrateEquilibriumTemperatureAnalyser

Calculates the hydrate equilibrium temperature at the stream pressure.

```java
import neqsim.process.measurementdevice.HydrateEquilibriumTemperatureAnalyser;

HydrateEquilibriumTemperatureAnalyser hydrateAnalyser =
    new HydrateEquilibriumTemperatureAnalyser(gasStream);
double hydrateTemp = hydrateAnalyser.getMeasuredValue("C");
System.out.println("Hydrate formation temp: " + hydrateTemp + " °C");
```

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
System.out.println("Likelihood of Failure: " + lof);

fivAnalyser.setMethod("FRMS");  // Fatigue Root Mean Square
double frms = fivAnalyser.getMeasuredValue("");
System.out.println("F-RMS: " + frms);
```

**Support Arrangements:**
- `"Stiff"` - Well-supported piping
- `"Medium stiff"` - Moderate support
- `"Medium"` - Typical support
- `"Flexible"` - Minimal support

**Analysis Methods:**
- `"LOF"` - Likelihood of Failure (API RP 14E based)
- `"FRMS"` - Fatigue Root Mean Square

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

### LevelTransmitter

Monitors liquid level in vessels.

```java
import neqsim.process.measurementdevice.LevelTransmitter;

LevelTransmitter lt = new LevelTransmitter(separator);
lt.setUnit("%");
double level = lt.getMeasuredValue();
```

### VolumeFlowTransmitter

Monitors volumetric flow rate.

```java
import neqsim.process.measurementdevice.VolumeFlowTransmitter;

VolumeFlowTransmitter vft = new VolumeFlowTransmitter(stream);
vft.setUnit("m3/hr");
double volumeFlow = vft.getMeasuredValue();
```

### VenturiFlowMeter

All five differential-pressure flow meters below share a common base,
`DifferentialPressureFlowMeter` (ISO 5167-1 general principles), which supplies the geometry
(`setGeometry`/`setPipeDiameter`/`setThroatDiameter`/`getBetaRatio`), the differential pressure
(explicit or via a linked `DifferentialPressureTransmitter`), the gas density/isentropic
exponent/dynamic viscosity readers (each overridable), the Reynolds-number iteration, and the
mass/actual-volume/standard-volume accessors. They differ only in the discharge coefficient and
the expansibility factor, `ExpansibilityModel` (`ORIFICE`, `ISENTROPIC` or `CONE`).

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
meter.setWetGasCorrelation(VenturiFlowMeter.WetGasCorrelation.ISO_TR_11583);
meter.setSurfaceTensionFactor(VenturiFlowMeter.H_HYDROCARBON);  // 1.0 HC, 1.35 water, 0.79 wet steam

// Supply the liquid load in one of three ways:
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

> **ISO/TR 11583 replaces the discharge coefficient.** In wet-gas mode the value passed to
> `setDischargeCoefficient` is ignored; the wet-gas $C$ of Equation (4) is used instead, and it
> tends to 1 rather than 0.985.

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
meter.setWetGasCorrelation(OrificeFlowMeter.WetGasCorrelation.ISO_TR_11583);

// Supply the liquid load in one of three ways:
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

GasDetector gasDetector = new GasDetector("Gas Detector 1", stream);
gasDetector.setDetectionLimit(20.0);  // % LEL
boolean gasDetected = gasDetector.isTriggered();
```

### FireDetector

Simulates fire detection for safety systems.

```java
import neqsim.process.measurementdevice.FireDetector;

FireDetector fireDetector = new FireDetector("Fire Detector 1");
fireDetector.setTemperatureThreshold(65.0);  // °C
boolean fireDetected = fireDetector.isTriggered();
```

## Quality Analysers

### MolarMassAnalyser

Calculates the molar mass of a stream.

```java
import neqsim.process.measurementdevice.MolarMassAnalyser;

MolarMassAnalyser mma = new MolarMassAnalyser(gasStream);
double molarMass = mma.getMeasuredValue("kg/mol");
System.out.println("Molar mass: " + molarMass * 1000 + " g/mol");
```

### WaterContentAnalyser

Measures water content in gas streams.

```java
import neqsim.process.measurementdevice.WaterContentAnalyser;

WaterContentAnalyser wca = new WaterContentAnalyser(gasStream);
double waterContent = wca.getMeasuredValue("ppm");
System.out.println("Water content: " + waterContent + " ppm");
```

### pHProbe

Measures pH of aqueous streams.

```java
import neqsim.process.measurementdevice.pHProbe;

pHProbe ph = new pHProbe(aqueousStream);
double phValue = ph.getMeasuredValue("");
System.out.println("pH: " + phValue);
```

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
