---
title: "Screening and Sizing Calculators"
description: "Standalone, educational screening-level calculators for flare radiation, line sizing, flow-induced vibration, pump NPSH, control-valve sizing and noise, thermowell strength, pipeline overpressure protection, orifice metering, and crude desalting. Each is a serializable Java class with an optional process-object bridge and JSON output."
keywords: "screening calculator, sizing calculator, flare radiation, API 521, frustum, solid flame, relevant wind, line sizing, erosional velocity, API RP 14E, likelihood of failure, LOF, kinetic energy, rho v2, flow-induced vibration, FIV, AVIFF, acoustic-induced vibration, AIV, Energy Institute, pump hydraulics, NPSH, NPSHa, NPSHr, cavitation, control valve sizing, Kv, Cv, IEC 60534-2-1, choked flow, control valve noise, IEC 60534-8-3, aerodynamic noise, thermowell, ASME PTC 19.3, TW-2016, wake frequency, pipeline overpressure protection, two barrier, MIP, MAIP, orifice metering, GPSA, ISO 5167, API 14.3, critical flow orifice, sonic flow, orifice well tester, prover, crude desalter, ASTM D3230, residual salt, wash water, serializable, JSON, process bridge"
---

# Screening and Sizing Calculators

NeqSim ships a family of small, self-contained calculators that perform
screening-level engineering checks based on published, public methods (API,
IEC, ISO, ASME PTC, GPSA, Energy Institute). Each calculator:

- is a plain `Serializable` Java class with scalar setters for manual input;
- exposes a single `calc…()` method and individual output getters;
- emits a `toJson()` summary;
- where useful, offers an optional `from…(processObject)` **bridge method** that
  populates its inputs directly from a running NeqSim `Stream`, `Pump`, `Flare`,
  or `ThrottlingValve` so the manual property-paste step is removed.

> These are **screening tools**. They flag whether a detailed assessment is
> needed; they do not replace code-compliant detailed design.

---

## Calculator catalogue

| Calculator | Package | Standard / basis | `calc` method | Process bridge |
|------------|---------|------------------|---------------|----------------|
| `FlareFrustumRadiationCalculator` | `process.equipment.flare` | API 521 solid-flame (frustum) radiation | `calcRadiation()` | `fromFlare(Flare)` |
| `RelevantWindCalculator` | `process.equipment.flare` | Power-law wind profile + wind-rose scan | `calc()` | — |
| `LineSizingLofCalculator` | `process.mechanicaldesign.pipeline` | API RP 14E erosional velocity + kinetic-energy LOF | `calcScreening()` | `fromStream(StreamInterface, double)` |
| `AviffScreeningCalculator` | `process.mechanicaldesign.pipeline` | Energy Institute AVIFF flow-induced-vibration screening | `calcScreening()` | — |
| `PumpHydraulicsNpshCalculator` | `process.mechanicaldesign.pump` | NPSHa/NPSHr margin + hydraulic/brake power | `calcHydraulics()` | `fromPump(Pump)` |
| `ThermowellDesignCalculator` | `process.mechanicaldesign.thermowell` | ASME PTC 19.3 TW-2016 (TW-1974 fallback) | `calcAll()` | — |
| `ControlValveGasSizing_IEC_60534_2_1` | `process.mechanicaldesign.valve` | IEC 60534-2-1 compressible sizing (Kv/Cv) | `calcSizing()` | `fromValve(ThrottlingValve)` |
| `ControlValveNoise_IEC_60534_8_3` | `process.mechanicaldesign.valve` | IEC 60534-8-3 aerodynamic noise | `calcNoise()` | — |
| `PipelinePressureProtectionCalculator` | `process.safety.overpressure` | Two-barrier overpressure protection, MIP check | `calcProtection()` | — |
| `GpsaOrificeCalculator` | `standards.gasquality` | GPSA / ISO 5167 / API 14.3 orifice metering (liquid/steam) | `calcFlow()` | — |
| `CriticalFlowOrifice` | `standards.gasquality` | Choked (sonic) flow through a restriction | `calcCriticalFlow()` | — |
| `OrificeWellTester` | `standards.gasquality` | GPSA critical-flow prover (orifice well tester) | `calcRate()` | — |
| `CrudeDesalterCalculator` | `standards.oilquality` | Wash-water dilution desalter screening (ASTM D3230 companion) | `calcPerformance()` | `fromStreams(StreamInterface, StreamInterface, double)` |

---

## Flare thermal radiation

`FlareFrustumRadiationCalculator` locates the radiant centroid of a
wind-tilted flame and computes the radiant heat flux at a ground-level receptor
by inverse-square spreading with atmospheric transmissivity (API 521
solid-flame model). The relevant (design) wind speed can be supplied directly
or obtained from `RelevantWindCalculator`, which scales a reference wind speed
to the flare-tip elevation and scans a wind rose for the worst sector.

```java
import neqsim.process.equipment.flare.FlareFrustumRadiationCalculator;

FlareFrustumRadiationCalculator flare = new FlareFrustumRadiationCalculator();
flare.setDuty(50.0, 45.0e6, 0.20);          // mass flow kg/s, LHV J/kg, radiated fraction
flare.setFlameGeometry(60.0, 120.0, 10.0, 100.0); // flame length, jet velocity, wind, tip elevation (m, m/s)
flare.setReceptor(150.0, 1.5, 1.0, 6300.0); // horizontal dist, elevation, transmissivity, allowable W/m2
flare.calcRadiation();

double flux = flare.getRadiantHeatFlux();    // W/m2 at the receptor
boolean ok = flare.isWithinAllowable();
```

Use the process bridge to take the duty straight from a `Flare` unit:

```java
FlareFrustumRadiationCalculator fromUnit = new FlareFrustumRadiationCalculator();
fromUnit.fromFlare(flareEquipment);          // reads heat duty from the process Flare
fromUnit.calcRadiation();
```

---

## Line sizing and flow-induced vibration

`LineSizingLofCalculator` combines the API RP 14E erosional velocity, the
erosion utilization, and the fluid kinetic energy ($\rho v^2$) into a single
likelihood-of-failure (LOF) band — `LOW` (&lt; 0.5), `MEDIUM` (0.5–1.0), or
`HIGH` (&ge; 1.0):

$$
V_e = \frac{C}{\sqrt{\rho}}, \qquad
\text{LOF} = \max\!\left(\frac{v}{V_e},\; \frac{\rho v^2}{(\rho v^2)_{ref}}\right)
$$

```java
import neqsim.process.mechanicaldesign.pipeline.LineSizingLofCalculator;

LineSizingLofCalculator line = new LineSizingLofCalculator();
line.setFlowConditions(120.0, 10.0);   // mixture density kg/m3, velocity m/s
line.calcScreening();
String band = line.getLikelihoodBand(); // "LOW" / "MEDIUM" / "HIGH"

// Or populate density and velocity from a running process stream:
LineSizingLofCalculator fromFlow = new LineSizingLofCalculator();
fromFlow.fromStream(processStream, 0.2032); // stream + pipe internal diameter (m)
fromFlow.calcScreening();
```

`AviffScreeningCalculator` performs the Energy Institute "Avoidance of
Vibration Induced Fatigue Failure" main-line screening, forming a LOF from the
flow kinetic energy, a pipe-size Fatigue Vibration Factor, a support-arrangement
Fatigue Correction Factor, and a gas-void-fraction correction for the
multiphase region.

---

## Pump hydraulics and NPSH

`PumpHydraulicsNpshCalculator` computes hydraulic and brake power and screens
the suction side for cavitation by comparing the available NPSH against the
required NPSH.

```java
import neqsim.process.mechanicaldesign.pump.PumpHydraulicsNpshCalculator;

PumpHydraulicsNpshCalculator pump = new PumpHydraulicsNpshCalculator();
pump.setDutyPoint(100.0, 80.0, 850.0, 0.72);     // flow m3/h, head m, density kg/m3, efficiency
pump.setSuctionConditions(3.0, 1.0, 2.0, 0.5, 3.0); // p_suct, p_vap (bar), static head, friction, NPSHr (m)
pump.calcHydraulics();

double brakePower = pump.getBrakePower();        // W
boolean cavitation = pump.isCavitationRisk();

// Bridge: pull flow, head, density, efficiency from a process Pump:
PumpHydraulicsNpshCalculator fromUnit = new PumpHydraulicsNpshCalculator();
fromUnit.fromPump(processPump);
fromUnit.calcHydraulics();
```

---

## Control valve sizing and noise

`ControlValveGasSizing_IEC_60534_2_1` computes the required flow coefficient
(Kv and Cv) for a compressible-service control valve per IEC 60534-2-1,
including the pressure-drop ratio $x$, the specific-heat-ratio factor
$F_\gamma$, choked-flow detection, and the expansion factor $Y$.

```java
import neqsim.process.mechanicaldesign.valve.ControlValveGasSizing_IEC_60534_2_1;

ControlValveGasSizing_IEC_60534_2_1 valve = new ControlValveGasSizing_IEC_60534_2_1();
valve.setFlowConditions(1000.0, 10.0, 5.0, 8.0); // W kg/h, p1, p2 (bar), inlet density kg/m3
valve.setValveCoefficients(1.30, 0.70, 1.0);     // gamma, terminal ratio xT, piping factor Fp
valve.calcSizing();
double cv = valve.getRequiredCv();
boolean choked = valve.isChoked();

// Bridge: take inlet/outlet pressure, density, and gamma from a ThrottlingValve:
ControlValveGasSizing_IEC_60534_2_1 fromUnit = new ControlValveGasSizing_IEC_60534_2_1();
fromUnit.fromValve(processValve);
fromUnit.calcSizing();
```

`ControlValveNoise_IEC_60534_8_3` predicts the external A-weighted sound
pressure level one metre downstream of the valve, identifying the flow regime
(subsonic through fully developed supersonic) and the pipe-wall transmission
loss.

---

## Thermowell strength

`ThermowellDesignCalculator` screens an intrusive thermowell against the four
ASME PTC 19.3 TW-2016 acceptance checks (frequency limit, dynamic stress,
static stress, hydrostatic limit), with process density, velocity, viscosity,
and pressure taken directly from a NeqSim fluid. `calcAll()` runs all four
checks; the older TW-1974 frequency-limit basis is available as a fallback.

---

## Pipeline overpressure protection

`PipelinePressureProtectionCalculator` evaluates a pipeline or piping segment
against a high-pressure source using a two-barrier philosophy. It computes the
maximum incidental pressure (MIP = incidental factor &middot; design pressure)
and reports whether the segment is fully rated, protected by one/two barriers,
or insufficiently protected.

```java
import neqsim.process.safety.overpressure.PipelinePressureProtectionCalculator;

PipelinePressureProtectionCalculator seg = new PipelinePressureProtectionCalculator();
seg.setPressureBasis(250.0, 150.0, 1.1);    // max source, design pressure (bar), incidental factor
seg.setBarriers(145.0, 160.0);              // barrier 1 and barrier 2 set points (bar)
seg.calcProtection();
```

---

## Orifice metering and choked flow

| Class | Use |
|-------|-----|
| `GpsaOrificeCalculator` | Liquid/NGL and steam orifice metering (GPSA / ISO 5167 / API 14.3); complements `Standard_AGA3` (natural-gas custody transfer). Call `calcFlow()`. |
| `CriticalFlowOrifice` | Maximum (sonic) discharge through a fixed restriction — hole, broken tapping, blowdown orifice. Call `calcCriticalFlow()`. |
| `OrificeWellTester` | Gas-well rate from a GPSA critical-flow prover (orifice well tester). Call `calcRate()`. |

---

## Crude desalting

`CrudeDesalterCalculator` estimates the residual salt content of crude oil
leaving a one- or two-stage electrostatic desalter using a wash-water dilution
model. It complements `Standard_ASTM_D3230` (salt-content measurement).

```java
import neqsim.standards.oilquality.CrudeDesalterCalculator;

CrudeDesalterCalculator desalter = new CrudeDesalterCalculator();
desalter.setFeedConditions(50.0, 0.06, 1.5);   // inlet salt, wash-water fraction, mix-valve dp
desalter.setStageConfiguration(2, 0.9, 0.003); // stages, stage efficiency, residual brine fraction
desalter.calcPerformance();
double outletSalt = desalter.getOutletSaltContent();

// Bridge: derive the effective wash fraction from crude and wash-water streams:
CrudeDesalterCalculator fromStreams = new CrudeDesalterCalculator();
fromStreams.fromStreams(crudeStream, washWaterStream, 50.0);
fromStreams.calcPerformance();
```

---

## Related documentation

- [Relief Valve Sizing - API 520/521](../safety/relief_valve_sizing_api.md)
- [Standards Documentation](../standards/index.md)
- [Process Simulation Documentation](index.md)
